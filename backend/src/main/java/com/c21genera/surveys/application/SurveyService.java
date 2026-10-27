package com.c21genera.surveys.application;

import com.c21genera.expedientes.ExpedienteLifecycleApi;
import com.c21genera.expedientes.ExpedienteLifecycleApi.ManualClientDataView;
import com.c21genera.expedientes.ExpedienteSummary;
import com.c21genera.identity.CurrentUser;
import com.c21genera.notifications.NotificationsApi;
import com.c21genera.notifications.StaffAlerts;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.events.SurveyEvents.SurveyWhatsAppRequested;
import com.c21genera.surveys.SurveyDelivery;
import com.c21genera.surveys.domain.NpsCategory;
import com.c21genera.surveys.domain.NpsSurvey;
import com.c21genera.surveys.domain.NpsSurvey.Contact;
import com.c21genera.surveys.domain.SurveyChannel;
import com.c21genera.surveys.domain.SurveySettings;
import com.c21genera.surveys.domain.SurveyStatus;
import com.c21genera.surveys.domain.Touchpoint;
import com.c21genera.surveys.domain.TouchpointConfig;
import com.c21genera.surveys.infrastructure.NpsSurveyRepository;
import com.c21genera.surveys.infrastructure.SurveyProperties;
import com.c21genera.surveys.infrastructure.SurveySettingsRepository;
import com.c21genera.surveys.infrastructure.TouchpointConfigRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Control de Cambios #28: programa, envía y recibe las encuestas NPS por punto
 * de contacto. Las automáticas respetan la configuración del punto (activo,
 * canal, retraso) y la regla anti-saturación; las que genera el asesor
 * siempre se crean (con aviso si el cliente ya recibió una hace poco).
 */
@Service
@Transactional
public class SurveyService implements SurveyDelivery {

  static final Set<String> DIRECTION = Set.of("ADMINISTRATOR", "DIRECTOR");
  private static final String SIGNATURE = "\n\nCENTURY 21 Genera\nWhatsApp 7778005300 · ventas@c21genera.com";

  public record SurveyView(
      UUID id,
      String token,
      String link,
      Touchpoint touchpoint,
      int touchpointNumber,
      String touchpointLabel,
      SurveyChannel channel,
      SurveyStatus status,
      UUID expedienteId,
      String expedienteFolio,
      UUID leadId,
      String clientName,
      String advisorName,
      Instant sendAt,
      Instant sentAt,
      int remindersSent,
      String deliveryDetail,
      Integer score,
      NpsCategory category,
      String comment,
      Instant answeredAt,
      String createdByName,
      Instant createdAt,
      boolean recentlySurveyed) {}

  /** Lo que ve el cliente en la página pública. */
  public record PublicSurvey(
      String token,
      int touchpointNumber,
      String touchpointLabel,
      String moment,
      String introMessage,
      boolean askComment,
      String clientFirstName,
      String status,
      Integer score,
      NpsCategory category,
      String followUpQuestion,
      String comment) {}

  public record ManualRequest(Touchpoint touchpoint, SurveyChannel channel, UUID expedienteId, UUID leadId, String clientName, String clientPhone, String clientEmail) {}

  private final NpsSurveyRepository surveys;
  private final TouchpointConfigRepository configs;
  private final SurveySettingsRepository settingsRepository;
  private final ExpedienteLifecycleApi expedientes;
  private final NotificationsApi email;
  private final StaffAlerts alerts;
  private final ApplicationEventPublisher events;
  private final SurveyProperties properties;
  private final Clock clock;

  SurveyService(
      NpsSurveyRepository surveys,
      TouchpointConfigRepository configs,
      SurveySettingsRepository settingsRepository,
      ExpedienteLifecycleApi expedientes,
      NotificationsApi email,
      StaffAlerts alerts,
      ApplicationEventPublisher events,
      SurveyProperties properties,
      Clock clock) {
    this.surveys = surveys;
    this.configs = configs;
    this.settingsRepository = settingsRepository;
    this.expedientes = expedientes;
    this.email = email;
    this.alerts = alerts;
    this.events = events;
    this.properties = properties;
    this.clock = clock;
  }

  // --- Disparo automático (puntos vía sistema) ------------------------------------

  /**
   * Un evento del proceso tocó un punto de contacto del expediente. No hace
   * nada si el punto está inactivo o si ya hubo una encuesta de ese punto en
   * el expediente; si la regla anti-saturación lo impide, queda registrada como
   * omitida (para que se vea en el historial).
   */
  public Optional<NpsSurvey> trigger(Touchpoint touchpoint, UUID expedienteId) {
    return trigger(touchpoint, expedienteId, Duration.ZERO);
  }

  /** extraDelay: se suma al retraso configurado (p. ej. post-venta a partir del cierre). */
  public Optional<NpsSurvey> trigger(Touchpoint touchpoint, UUID expedienteId, Duration extraDelay) {
    TouchpointConfig config = config(touchpoint);
    if (!config.isActive() || expedienteId == null) {
      return Optional.empty();
    }
    if (surveys.existsByExpedienteIdAndTouchpointAndStatusIn(expedienteId, touchpoint, List.of(SurveyStatus.SCHEDULED, SurveyStatus.SENT, SurveyStatus.ANSWERED))) {
      return Optional.empty();
    }
    Contact contact = contactOf(expedienteId, null, null, null, null);
    Instant now = clock.instant();
    NpsSurvey survey =
        new NpsSurvey(touchpoint, config.getChannel(), contact, config.getIntroMessage(), config.isAskComment(), now.plus(Duration.ofMinutes(config.getDelayMinutes())).plus(extraDelay), null, now);
    String blocked = frequencyBlock(contact.key(), survey.getSendAt());
    if (blocked != null) {
      survey.skip(blocked);
    }
    return Optional.of(surveys.save(survey));
  }

  /** Igual que trigger, pero si no tiene retraso se envía en ese momento (p. ej. en el portal del cliente). */
  public Optional<NpsSurvey> triggerNow(Touchpoint touchpoint, UUID expedienteId) {
    Optional<NpsSurvey> created = trigger(touchpoint, expedienteId);
    created.filter(s -> s.getStatus() == SurveyStatus.SCHEDULED && !s.getSendAt().isAfter(clock.instant())).ifPresent(s -> dispatch(s, false));
    return created;
  }

  // --- Activación manual (asesor) -------------------------------------------------

  /**
   * El asesor genera la encuesta de una interacción (presencial o del
   * sistema). Con canal ADVISOR_LINK queda lista para compartir; con WhatsApp,
   * correo o sistema se envía de inmediato.
   */
  public SurveyView createManual(ManualRequest req, CurrentUser user) {
    if (req.touchpoint() == null) {
      throw new UnprocessableException("SURVEY_TOUCHPOINT_REQUIRED", "Elige el punto de contacto.");
    }
    if (req.expedienteId() == null && req.leadId() == null) {
      throw new UnprocessableException("SURVEY_TARGET_REQUIRED", "La encuesta debe pertenecer a un expediente o a un lead.");
    }
    SurveyChannel channel = req.channel() == null ? SurveyChannel.ADVISOR_LINK : req.channel();
    if (channel == SurveyChannel.QR) {
      throw new UnprocessableException("SURVEY_CHANNEL_INVALID", "El QR es una liga fija por punto de contacto; descárgalo en la configuración de encuestas.");
    }
    if (channel == SurveyChannel.SYSTEM && req.expedienteId() == null) {
      throw new UnprocessableException("SURVEY_CHANNEL_INVALID", "La encuesta dentro del sistema solo aplica a clientes con expediente (portal de carga).");
    }
    TouchpointConfig config = config(req.touchpoint());
    Contact contact = contactOf(req.expedienteId(), req.leadId(), req.clientName(), req.clientPhone(), req.clientEmail());
    if (req.expedienteId() == null) {
      // Lead: el asesor que la genera es quien atiende la interacción.
      contact = new Contact(null, null, req.leadId(), contact.name(), contact.phone(), contact.email(), user.id(), user.name());
    } else if (contact.advisorName() == null) {
      contact = new Contact(contact.expedienteId(), contact.expedienteFolio(), contact.leadId(), contact.name(), contact.phone(), contact.email(), user.id(), user.name());
    }
    if (channel == SurveyChannel.WHATSAPP && contact.phone() == null) {
      throw new UnprocessableException("SURVEY_PHONE_REQUIRED", "El cliente no tiene teléfono registrado: comparte la liga o envíala por correo.");
    }
    if (channel == SurveyChannel.EMAIL && contact.email() == null) {
      throw new UnprocessableException("SURVEY_EMAIL_REQUIRED", "El cliente no tiene correo registrado: comparte la liga o envíala por WhatsApp.");
    }
    Instant now = clock.instant();
    boolean recent = frequencyBlock(contact.key(), now) != null;
    NpsSurvey survey = surveys.save(new NpsSurvey(req.touchpoint(), channel, contact, config.getIntroMessage(), config.isAskComment(), now, user.name(), now));
    dispatch(survey, false);
    return view(survey, recent, true);
  }

  // --- Envío ----------------------------------------------------------------------

  /** Envía (o reenvía como recordatorio) por el canal de la encuesta, con respaldo al otro canal. */
  void dispatch(NpsSurvey s, boolean reminder) {
    Instant now = clock.instant();
    SurveyChannel channel = s.getChannel();
    if (channel == SurveyChannel.SYSTEM && s.getExpedienteId() == null) {
      channel = s.getClientPhone() != null ? SurveyChannel.WHATSAPP : SurveyChannel.EMAIL;
      s.fallbackTo(channel);
    }
    switch (channel) {
      case WHATSAPP -> {
        if (s.getClientPhone() != null) {
          events.publishEvent(new SurveyWhatsAppRequested(s.getId(), s.getClientPhone(), message(s, reminder)));
          if (!reminder) {
            s.markSent(now, "Enviándose por WhatsApp");
          }
        } else if (s.getClientEmail() != null) {
          s.fallbackTo(SurveyChannel.EMAIL);
          sendEmail(s, reminder, now, "Sin teléfono: se envió por correo");
        } else if (!reminder) {
          s.markFailed("El cliente no tiene teléfono ni correo registrado.");
        }
      }
      case EMAIL -> {
        if (s.getClientEmail() != null) {
          sendEmail(s, reminder, now, "Enviada por correo");
        } else if (s.getClientPhone() != null) {
          s.fallbackTo(SurveyChannel.WHATSAPP);
          events.publishEvent(new SurveyWhatsAppRequested(s.getId(), s.getClientPhone(), message(s, reminder)));
          if (!reminder) {
            s.markSent(now, "Sin correo: se envió por WhatsApp");
          }
        } else if (!reminder) {
          s.markFailed("El cliente no tiene teléfono ni correo registrado.");
        }
      }
      case SYSTEM -> {
        if (!reminder) {
          s.markSent(now, "Disponible en el portal del cliente");
        }
      }
      case ADVISOR_LINK -> {
        if (!reminder) {
          s.markSent(now, "Liga lista para que el asesor la comparta");
          if (s.getCreatedByName() == null && s.getAdvisorUserId() != null) {
            // Automática con liga del asesor: se le avisa para que la comparta.
            alerts.notifyUser(
                s.getAdvisorUserId(),
                "Encuesta para compartir",
                "Comparte la encuesta de satisfacción con " + (s.getClientName() != null ? s.getClientName() : "tu cliente"),
                s.getTouchpoint().label() + "\nLiga: " + link(s),
                s.getExpedienteId() != null ? "/expedientes/" + s.getExpedienteId() : null,
                "survey-share:" + s.getId(),
                false);
          }
        }
      }
      case QR -> {}
    }
    if (reminder) {
      s.reminded(now);
    }
  }

  private void sendEmail(NpsSurvey s, boolean reminder, Instant now, String detail) {
    String first = firstName(s.getClientName());
    String subject = (reminder ? "Recordatorio: " : "") + "¿Cómo fue tu experiencia con CENTURY 21 Genera?";
    String body =
        "Hola" + (first != null ? " " + first : "") + ",\n\n"
            + (s.getIntroMessage() != null ? s.getIntroMessage() + "\n\n" : "")
            + "Queremos saber cómo te fue " + s.getTouchpoint().moment() + ". "
            + "¿Qué tan probable es que recomiendes los servicios de CENTURY 21 Genera a un familiar o amigo?\n\n"
            + "Responde en menos de un minuto aquí:\n" + link(s)
            + SIGNATURE;
    email.notify(s.getExpedienteId(), "Encuesta de satisfacción", s.getClientEmail(), subject, body);
    if (!reminder) {
      s.markSent(now, detail);
    }
  }

  String message(NpsSurvey s, boolean reminder) {
    String first = firstName(s.getClientName());
    return (reminder ? "Recordatorio: " : "")
        + "Hola" + (first != null ? " " + first : "") + ", " + (s.getIntroMessage() != null ? s.getIntroMessage() + " " : "")
        + "en CENTURY 21 Genera queremos saber cómo te fue " + s.getTouchpoint().moment()
        + ". ¿Qué tan probable es que nos recomiendes con un familiar o amigo? Responde aquí (1 minuto): " + link(s);
  }

  @Override
  public void recordWhatsAppDelivery(UUID surveyId, boolean sent, String detail) {
    surveys.findById(surveyId).ifPresent(s -> {
      if (sent) {
        if (s.getStatus() == SurveyStatus.SENT) {
          s.deliveryResult(s.getRemindersSent() > 0 ? "Recordatorio enviado por WhatsApp" : "Enviada por WhatsApp");
        }
        return;
      }
      String why = "No se pudo enviar por WhatsApp" + (detail != null ? ": " + detail : "");
      if (s.getStatus() == SurveyStatus.SENT && s.getClientEmail() != null && s.getChannel() == SurveyChannel.WHATSAPP) {
        s.fallbackTo(SurveyChannel.EMAIL);
        sendEmail(s, false, clock.instant(), why + ". Se envió por correo.");
      } else if (s.getStatus() == SurveyStatus.SENT && s.getRemindersSent() == 0) {
        s.markFailed(why);
      } else {
        s.deliveryResult(why);
      }
    });
  }

  // --- Página pública -----------------------------------------------------------

  @Transactional(readOnly = true)
  public PublicSurvey publicView(String token) {
    return publicOf(byToken(token));
  }

  /** El cliente elige su calificación. Puede corregirla mientras deja su comentario. */
  public PublicSurvey answer(String token, int score) {
    if (score < 0 || score > 10) {
      throw new UnprocessableException("SURVEY_SCORE_INVALID", "La calificación va de 0 a 10.");
    }
    NpsSurvey s = byToken(token);
    Instant now = clock.instant();
    switch (s.getStatus()) {
      case EXPIRED -> throw new UnprocessableException("SURVEY_EXPIRED", "Esta encuesta ya venció. ¡Gracias por tu interés!");
      case SKIPPED, FAILED -> throw new UnprocessableException("SURVEY_UNAVAILABLE", "Esta encuesta ya no está disponible.");
      case ANSWERED -> {
        if (s.getAnsweredAt() != null && s.getAnsweredAt().plus(Duration.ofHours(2)).isBefore(now)) {
          return publicOf(s);
        }
        boolean becameDetractor = s.getScore() != null && s.getScore() > 6 && score <= 6;
        s.answer(score, s.getAnsweredAt());
        if (becameDetractor) {
          alertDetractor(s);
        }
      }
      default -> {
        s.answer(score, now);
        if (score <= 6) {
          alertDetractor(s);
        }
      }
    }
    return publicOf(s);
  }

  public PublicSurvey comment(String token, String comment) {
    NpsSurvey s = byToken(token);
    if (s.getStatus() != SurveyStatus.ANSWERED) {
      throw new UnprocessableException("SURVEY_NOT_ANSWERED", "Primero elige tu calificación del 0 al 10.");
    }
    if (s.getAnsweredAt().plus(Duration.ofDays(2)).isBefore(clock.instant())) {
      return publicOf(s);
    }
    s.comment(comment);
    if (s.getScore() != null && s.getScore() <= 6 && s.getComment() != null) {
      alerts.notifyRoles(
          DIRECTION, "Comentario de detractor", "Comentario de " + clientLabel(s) + " (" + s.getScore() + "/10)", s.getComment(), linkFor(s), "nps-comment:" + s.getId(), false);
    }
    return publicOf(s);
  }

  /** QR fijo de un punto de contacto: la respuesta crea la encuesta en ese momento. */
  public PublicSurvey answerStatic(Touchpoint touchpoint, int score, String name) {
    TouchpointConfig config = config(touchpoint);
    if (!config.isActive()) {
      throw new UnprocessableException("SURVEY_UNAVAILABLE", "Esta encuesta no está disponible en este momento.");
    }
    Instant now = clock.instant();
    NpsSurvey s = new NpsSurvey(touchpoint, SurveyChannel.QR, new Contact(null, null, null, name, null, null, null, null), config.getIntroMessage(), config.isAskComment(), now, null, now);
    s.markSent(now, "Respondida desde el QR");
    surveys.save(s);
    return answer(s.getToken(), score);
  }

  @Transactional(readOnly = true)
  public PublicSurvey staticView(Touchpoint touchpoint) {
    TouchpointConfig config = config(touchpoint);
    if (!config.isActive()) {
      throw new UnprocessableException("SURVEY_UNAVAILABLE", "Esta encuesta no está disponible en este momento.");
    }
    return new PublicSurvey(null, touchpoint.number(), touchpoint.label(), touchpoint.moment(), config.getIntroMessage(), config.isAskComment(), null, "OPEN", null, null, null, null);
  }

  /** Encuestas "dentro del sistema" pendientes para el portal del cliente. */
  @Transactional(readOnly = true)
  public List<PublicSurvey> pendingInPortal(UUID expedienteId) {
    return surveys.findByExpedienteIdOrderByCreatedAtDesc(expedienteId).stream()
        .filter(s -> s.getChannel() == SurveyChannel.SYSTEM && s.getStatus() == SurveyStatus.SENT)
        .map(this::publicOf)
        .toList();
  }

  private void alertDetractor(NpsSurvey s) {
    alerts.notifyRoles(
        DIRECTION,
        "Alerta NPS: detractor",
        "Detractor: " + clientLabel(s) + " calificó con " + s.getScore() + "/10 — " + s.getTouchpoint().label(),
        "Cliente: " + clientLabel(s)
            + (s.getExpedienteFolio() != null ? "\nExpediente: " + s.getExpedienteFolio() : "")
            + (s.getAdvisorName() != null ? "\nAsesor: " + s.getAdvisorName() : "")
            + "\nPunto de contacto: " + s.getTouchpoint().number() + ". " + s.getTouchpoint().label()
            + "\nCanal: " + s.getChannel()
            + "\n\nDale seguimiento antes de que el problema escale.",
        linkFor(s),
        "nps-detractor:" + s.getId(),
        true);
  }

  private static String linkFor(NpsSurvey s) {
    if (s.getExpedienteId() != null) {
      return "/expedientes/" + s.getExpedienteId();
    }
    return s.getLeadId() != null ? "/leads/" + s.getLeadId() : "/nps";
  }

  private static String clientLabel(NpsSurvey s) {
    return s.getClientName() != null ? s.getClientName() : "un cliente";
  }

  // --- Consultas internas ---------------------------------------------------------

  /** showScores=false: el asesor no ve calificaciones (cuando la empresa decidió no compartirlas). */
  @Transactional(readOnly = true)
  public List<SurveyView> ofExpediente(UUID expedienteId, boolean showScores) {
    return surveys.findByExpedienteIdOrderByCreatedAtDesc(expedienteId).stream().map(s -> view(s, false, showScores)).toList();
  }

  @Transactional(readOnly = true)
  public List<SurveyView> ofLead(UUID leadId, boolean showScores) {
    return surveys.findByLeadIdOrderByCreatedAtDesc(leadId).stream().map(s -> view(s, false, showScores)).toList();
  }

  @Transactional(readOnly = true)
  public SurveySettings settings() {
    return settingsRepository.findById((short) 1).orElseThrow(() -> new IllegalStateException("Falta la fila de survey_settings"));
  }

  @Transactional(readOnly = true)
  public List<TouchpointConfig> configs() {
    return configs.findAll().stream().sorted(Comparator.comparingInt(c -> c.getTouchpoint().number())).toList();
  }

  public TouchpointConfig updateConfig(Touchpoint touchpoint, boolean active, SurveyChannel channel, int delayMinutes, boolean askComment, String intro, CurrentUser user) {
    if (channel == null || channel == SurveyChannel.QR) {
      throw new UnprocessableException("SURVEY_CHANNEL_INVALID", "Elige WhatsApp, correo, dentro del sistema o liga del asesor.");
    }
    TouchpointConfig config = config(touchpoint);
    config.update(active, channel, delayMinutes, askComment, intro, user.name(), clock.instant());
    return config;
  }

  public SurveySettings updateSettings(int minDays, int maxPerPeriod, int periodDays, boolean reminder, int reminderHours, int maxReminders, int expireDays, boolean advisorsSeeOwn, CurrentUser user) {
    SurveySettings s = settings();
    s.update(minDays, maxPerPeriod, periodDays, reminder, reminderHours, maxReminders, expireDays, advisorsSeeOwn, user.name(), clock.instant());
    return s;
  }

  String link(NpsSurvey s) {
    return properties.baseUrl() + "/" + s.getToken();
  }

  public String staticLink(Touchpoint t) {
    return properties.baseUrl() + "/p/" + t.name().toLowerCase(java.util.Locale.ROOT);
  }

  // ---------------------------------------------------------------------------

  /**
   * Motivo por el que la regla anti-saturación impide otra encuesta
   * automática (null = se puede). Se compara contra la fecha de envío de cada
   * encuesta (sendAt), no contra la de creación: así la de post-venta, que se
   * manda 30 días después, no choca con la del cierre.
   */
  String frequencyBlock(String contactKey, Instant sendAt) {
    if (contactKey == null) {
      return null;
    }
    SurveySettings settings = settings();
    int window = Math.max(settings.getMinDaysBetween(), settings.getPeriodDays());
    List<NpsSurvey> near =
        surveys.findByContactKeyAndSendAtGreaterThanEqual(contactKey, sendAt.minus(Duration.ofDays(window))).stream().filter(s -> s.getStatus().countsForFrequency()).toList();
    Duration minBetween = Duration.ofDays(settings.getMinDaysBetween());
    if (settings.getMinDaysBetween() > 0 && near.stream().anyMatch(s -> Duration.between(s.getSendAt(), sendAt).abs().compareTo(minBetween) < 0)) {
      return "Omitida: el cliente ya recibió una encuesta en los últimos " + settings.getMinDaysBetween() + " días.";
    }
    Instant periodStart = sendAt.minus(Duration.ofDays(settings.getPeriodDays()));
    long inPeriod = near.stream().filter(s -> !s.getSendAt().isBefore(periodStart) && !s.getSendAt().isAfter(sendAt)).count();
    if (inPeriod >= settings.getMaxPerPeriod()) {
      return "Omitida: el cliente ya recibió " + inPeriod + " encuestas en los últimos " + settings.getPeriodDays() + " días.";
    }
    return null;
  }

  /** Datos del cliente: lo capturado a mano gana; si falta, los del expediente (datos del cliente o el propietario). */
  private Contact contactOf(UUID expedienteId, UUID leadId, String name, String phone, String emailAddress) {
    if (expedienteId == null) {
      return new Contact(null, null, leadId, name, phone, emailAddress, null, null);
    }
    ExpedienteSummary summary = expedientes.getSummary(expedienteId);
    ManualClientDataView manual = expedientes.getManualData(expedienteId);
    var owner = summary.participants().stream().filter(ExpedienteSummary.ParticipantView::isOwner).min(Comparator.comparingInt(ExpedienteSummary.ParticipantView::ordinal));
    String resolvedPhone = firstNonBlank(phone, manual == null ? null : manual.phone(), owner.map(ExpedienteSummary.ParticipantView::phone).orElse(null));
    String resolvedEmail = firstNonBlank(emailAddress, manual == null ? null : manual.email(), owner.map(ExpedienteSummary.ParticipantView::email).orElse(null));
    String advisorName = null;
    try {
      advisorName = expedientes.getPropertyProfile(expedienteId).assignedAdvisorName();
    } catch (RuntimeException e) {
      // Sin perfil del inmueble: la encuesta se crea sin nombre de asesor.
    }
    return new Contact(expedienteId, summary.folio(), leadId, firstNonBlank(name, summary.ownerDisplayName()), resolvedPhone, resolvedEmail, summary.assignedAdvisorId(), advisorName);
  }

  private static String firstNonBlank(String... values) {
    return Arrays.stream(values).filter(v -> v != null && !v.isBlank()).findFirst().orElse(null);
  }

  private static String firstName(String name) {
    return name == null || name.isBlank() ? null : name.strip().split("\\s+")[0];
  }

  private TouchpointConfig config(Touchpoint touchpoint) {
    return configs.findById(touchpoint).orElseThrow(() -> new IllegalStateException("Falta la configuración del punto " + touchpoint));
  }

  private NpsSurvey byToken(String token) {
    return surveys.findByToken(token).orElseThrow(() -> new NotFoundException("Encuesta no encontrada"));
  }

  private PublicSurvey publicOf(NpsSurvey s) {
    NpsCategory category = s.category();
    return new PublicSurvey(
        s.getToken(),
        s.getTouchpoint().number(),
        s.getTouchpoint().label(),
        s.getTouchpoint().moment(),
        s.getIntroMessage(),
        s.isAskComment(),
        firstName(s.getClientName()),
        s.getStatus() == SurveyStatus.SCHEDULED ? "SENT" : s.getStatus().name(),
        s.getScore(),
        category,
        category == null ? null : category.followUpQuestion(),
        s.getComment());
  }

  SurveyView view(NpsSurvey s, boolean recentlySurveyed, boolean showScores) {
    boolean shareable = s.getChannel() == SurveyChannel.ADVISOR_LINK || s.getChannel() == SurveyChannel.QR || s.getStatus() == SurveyStatus.SENT;
    return new SurveyView(
        s.getId(),
        shareable ? s.getToken() : null,
        shareable ? link(s) : null,
        s.getTouchpoint(),
        s.getTouchpoint().number(),
        s.getTouchpoint().label(),
        s.getChannel(),
        s.getStatus(),
        s.getExpedienteId(),
        s.getExpedienteFolio(),
        s.getLeadId(),
        s.getClientName(),
        s.getAdvisorName(),
        s.getSendAt(),
        s.getSentAt(),
        s.getRemindersSent(),
        s.getDeliveryDetail(),
        showScores ? s.getScore() : null,
        showScores ? s.category() : null,
        showScores ? s.getComment() : null,
        s.getAnsweredAt(),
        s.getCreatedByName(),
        s.getCreatedAt(),
        recentlySurveyed);
  }
}
