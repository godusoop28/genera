package com.c21genera.leads.application;

import com.c21genera.expedientes.ExpedienteLifecycleApi;
import com.c21genera.expedientes.ExpedienteLifecycleApi.ExpedienteRef;
import com.c21genera.identity.CurrentUser;
import com.c21genera.leads.AssignmentMethod;
import com.c21genera.leads.AssignmentStatus;
import com.c21genera.leads.LeadInterest;
import com.c21genera.leads.LeadSource;
import com.c21genera.leads.LeadStage;
import com.c21genera.leads.LeadTemperature;
import com.c21genera.leads.LostReason;
import com.c21genera.leads.application.AvailabilityService.AdvisorState;
import com.c21genera.leads.application.AvailabilityService.Status;
import com.c21genera.leads.application.LeadSourceCatalog.Resolved;
import com.c21genera.leads.application.LeadSourceCatalog.SourceOption;
import com.c21genera.leads.application.LeadTaskService.TaskView;
import com.c21genera.leads.domain.AssignmentLog;
import com.c21genera.leads.domain.Lead;
import com.c21genera.leads.domain.LeadActivity;
import com.c21genera.leads.domain.LeadSharedProperty;
import com.c21genera.leads.domain.LeadSourceOption;
import com.c21genera.leads.domain.LeadStageHistory;
import com.c21genera.leads.domain.LeadTask;
import com.c21genera.leads.infrastructure.AssignmentLogRepository;
import com.c21genera.leads.infrastructure.LeadActivityRepository;
import com.c21genera.leads.infrastructure.LeadRepository;
import com.c21genera.leads.infrastructure.LeadSharedPropertyRepository;
import com.c21genera.leads.infrastructure.LeadStageHistoryRepository;
import com.c21genera.leads.infrastructure.LeadTaskRepository;
import com.c21genera.online21.PropertyCatalog;
import com.c21genera.online21.PropertyCatalog.CatalogProperty;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operaciones del personal sobre los leads: lista y pipeline, ficha completa
 * (CC#30), etapa con su historial, reasignación, notas, inmuebles
 * compartidos, vínculo con el expediente y alta manual con la fuente del
 * catálogo (CC#29).
 */
@Service
@Transactional
public class LeadService {

  public enum Group {
    ALL,
    UNASSIGNED,
    PENDING,
    NEW,
    OPEN,
    STALE,
    WON,
    LOST;

    boolean matches(Lead l, Instant staleBefore) {
      return switch (this) {
        case ALL -> true;
        case UNASSIGNED -> l.getAssignmentStatus() == AssignmentStatus.UNASSIGNED || (l.getAdvisorUserId() == null && !l.getAssignmentStatus().isPending());
        case PENDING -> l.getAssignmentStatus().isPending();
        case NEW -> l.getStage() == LeadStage.NEW;
        case OPEN -> l.getStage().isOpen();
        case STALE -> l.getStage().isOpen() && l.getLastActivityAt().isBefore(staleBefore);
        case WON -> l.getStage() == LeadStage.WON;
        case LOST -> l.getStage() == LeadStage.LOST;
      };
    }
  }

  public record Filter(
      String q, Group group, UUID advisorId, LeadSource source, Instant from, Long propertyOnline21Id, String sourceCode, LeadTemperature temperature, LeadStage stage, UUID expedienteId) {

    public Filter(String q, Group group, UUID advisorId, LeadSource source, Instant from, Long propertyOnline21Id) {
      this(q, group, advisorId, source, from, propertyOnline21Id, null, null, null, null);
    }
  }

  public record PropertyView(Long online21Id, String key, String title) {}

  public record AdvisorRef(UUID id, String name) {}

  public record ExpedienteLink(UUID id, String folio) {}

  /** Próxima actividad programada (para la lista y el pipeline). */
  public record NextTask(UUID id, LeadTask.Type type, Instant scheduledAt, boolean overdue) {}

  public record LeadItem(
      UUID id,
      String name,
      String phone,
      String email,
      LeadSource source,
      String sourceDetail,
      LeadInterest interest,
      LeadStage stage,
      PropertyView property,
      AdvisorRef advisor,
      AssignmentMethod assignmentMethod,
      String summary,
      BigDecimal budget,
      String lostReason,
      Instant createdAt,
      Instant lastActivityAt,
      UUID conversationId,
      AssignmentStatus assignmentStatus,
      String assignmentRule,
      String pendingReason,
      Instant nextShiftAt,
      AdvisorRef propertyAdvisor,
      String coordinationNote,
      // Control de Cambios #29.
      String sourceCode,
      String sourceLabel,
      LeadSourceOption.Category sourceCategory,
      // Control de Cambios #30.
      LocalDate firstContactOn,
      UUID propertyCategoryId,
      UUID propertySubtypeId,
      String propertyTypeLabel,
      String zone,
      BigDecimal budgetMin,
      String desiredFeatures,
      LeadTemperature temperature,
      Integer closeProbability,
      LocalDate expectedCloseOn,
      LostReason lostReasonCode,
      Instant stageEnteredAt,
      ExpedienteLink expediente,
      NextTask nextTask,
      int overdueTasks,
      boolean stale) {}

  public record ActivityView(UUID id, LeadActivity.Kind kind, String text, String actorName, Instant createdAt) {}

  /** Una entrada de la bitácora de asignación: cuándo, a quién, por qué, con qué regla y turno. */
  public record AssignmentView(
      UUID id, AssignmentLog.Outcome outcome, AdvisorRef advisor, AssignmentMethod reason, String rule, UUID shiftId, String roundMode, String detail, String actorName, Instant createdAt) {}

  /** Paso por una etapa; exitedAt null = etapa actual. days: duración (hasta hoy si sigue en ella). */
  public record StageStep(LeadStage stage, Instant enteredAt, Instant exitedAt, double days, String actorName) {}

  public record SharedPropertyView(UUID id, Long online21Id, String key, String title, String note, String actorName, Instant createdAt) {}

  public record ExpedienteCandidate(UUID id, String folio, String ownerDisplayName, String propertyKey) {}

  public record LeadDetail(
      LeadItem lead,
      List<ActivityView> activities,
      List<AssignmentView> assignments,
      String online21SyncStatus,
      String advisorNoticeStatus,
      List<TaskView> tasks,
      List<StageStep> stageHistory,
      List<SharedPropertyView> sharedProperties,
      LeadStage suggestedStage,
      List<ExpedienteCandidate> expedienteCandidates) {}

  public record LeadPage(List<LeadItem> items, long total, int page, int totalPages, Map<Group, Long> counts) {}

  public record CreateLead(
      String name,
      String phone,
      String email,
      LeadSource source,
      String sourceDetail,
      LeadInterest interest,
      String propertyReference,
      String summary,
      BigDecimal budget,
      UUID advisorId,
      String exceptionReason,
      boolean autoAssign,
      String sourceCode,
      Profile profile) {

    public CreateLead(
        String name,
        String phone,
        String email,
        LeadSource source,
        String sourceDetail,
        LeadInterest interest,
        String propertyReference,
        String summary,
        BigDecimal budget,
        UUID advisorId,
        String exceptionReason,
        boolean autoAssign) {
      this(name, phone, email, source, sourceDetail, interest, propertyReference, summary, budget, advisorId, exceptionReason, autoAssign, null, null);
    }
  }

  /** Control de Cambios #30: interés y estatus de la ficha (todo opcional). */
  public record Profile(
      LocalDate firstContactOn,
      UUID propertySubtypeId,
      String zone,
      BigDecimal budgetMin,
      BigDecimal budgetMax,
      String desiredFeatures,
      LeadTemperature temperature,
      Integer closeProbability,
      LocalDate expectedCloseOn) {}

  /** Corrección de la ficha: reemplaza todos los campos editables. */
  public record UpdateLead(
      String name, String phone, String email, LeadInterest interest, String sourceCode, String sourceDetail, String summary, String propertyReference, Profile profile) {}

  private final LeadRepository leads;
  private final LeadActivityRepository activities;
  private final AssignmentLogRepository assignmentLogs;
  private final LeadStageHistoryRepository stageHistory;
  private final LeadSharedPropertyRepository sharedProperties;
  private final LeadTaskRepository taskRepository;
  private final LeadTaskService tasks;
  private final LeadSourceCatalog sources;
  private final LeadRecorder recorder;
  private final AssignmentService assignment;
  private final AvailabilityService availability;
  private final AdvisorRoutingService routing;
  private final StaffNames staff;
  private final PropertyCatalog catalog;
  private final com.c21genera.propertycatalog.PropertyCatalog propertyTypes;
  private final ExpedienteLifecycleApi expedientes;
  private final Clock clock;

  LeadService(
      LeadRepository leads,
      LeadActivityRepository activities,
      AssignmentLogRepository assignmentLogs,
      LeadStageHistoryRepository stageHistory,
      LeadSharedPropertyRepository sharedProperties,
      LeadTaskRepository taskRepository,
      LeadTaskService tasks,
      LeadSourceCatalog sources,
      LeadRecorder recorder,
      AssignmentService assignment,
      AvailabilityService availability,
      AdvisorRoutingService routing,
      StaffNames staff,
      PropertyCatalog catalog,
      com.c21genera.propertycatalog.PropertyCatalog propertyTypes,
      ExpedienteLifecycleApi expedientes,
      Clock clock) {
    this.leads = leads;
    this.activities = activities;
    this.assignmentLogs = assignmentLogs;
    this.stageHistory = stageHistory;
    this.sharedProperties = sharedProperties;
    this.taskRepository = taskRepository;
    this.tasks = tasks;
    this.sources = sources;
    this.availability = availability;
    this.routing = routing;
    this.recorder = recorder;
    this.assignment = assignment;
    this.staff = staff;
    this.catalog = catalog;
    this.propertyTypes = propertyTypes;
    this.expedientes = expedientes;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public LeadPage list(Filter filter, CurrentUser user, int page, int size) {
    List<Lead> scoped = leads.findAll(spec(filter, user), Sort.by(Sort.Direction.DESC, "createdAt"));
    Instant staleBefore = staleBefore();
    Map<Group, Long> counts = new EnumMap<>(Group.class);
    for (Group g : Group.values()) {
      counts.put(g, scoped.stream().filter(l -> g.matches(l, staleBefore)).count());
    }
    Group group = filter.group() == null ? Group.ALL : filter.group();
    List<Lead> visible = scoped.stream().filter(l -> group.matches(l, staleBefore)).toList();
    int safeSize = Math.max(1, Math.min(size, 200));
    int from = Math.min(page * safeSize, visible.size());
    int to = Math.min(from + safeSize, visible.size());
    List<LeadItem> items = items(visible.subList(from, to));
    return new LeadPage(items, visible.size(), page, (int) Math.ceil(visible.size() / (double) safeSize), counts);
  }

  @Transactional(readOnly = true)
  public LeadDetail get(UUID id, CurrentUser user) {
    Lead lead = load(id, user);
    Map<UUID, String> names = staff.names();
    List<ActivityView> timeline =
        activities.findByLeadIdOrderByCreatedAtDesc(id).stream()
            .map(a -> new ActivityView(a.getId(), a.getKind(), a.getText(), a.getActorName(), a.getCreatedAt()))
            .toList();
    List<AssignmentView> assignments =
        assignmentLogs.findByLeadIdOrderByCreatedAtDesc(id).stream()
            .map(
                a ->
                    new AssignmentView(
                        a.getId(),
                        a.getOutcome(),
                        a.getAdvisorUserId() == null ? null : new AdvisorRef(a.getAdvisorUserId(), staff.nameOf(a.getAdvisorUserId(), names)),
                        a.getReason(),
                        a.getRule(),
                        a.getShiftId(),
                        a.getRoundMode(),
                        a.getDetail(),
                        a.getActorName(),
                        a.getCreatedAt()))
            .toList();
    List<LeadTask> leadTasks = taskRepository.findByLeadIdOrderByScheduledAtDesc(id);
    List<SharedPropertyView> shared =
        sharedProperties.findByLeadIdOrderByCreatedAtDesc(id).stream()
            .map(p -> new SharedPropertyView(p.getId(), p.getPropertyOnline21Id(), p.getPropertyKey(), p.getPropertyTitle(), p.getNote(), p.getActorName(), p.getCreatedAt()))
            .toList();
    List<ExpedienteCandidate> candidates =
        lead.getExpedienteId() == null && (lead.getStage() == LeadStage.OFFER_ACCEPTED || lead.getStage() == LeadStage.WON) ? candidatesFor(lead) : List.of();
    return new LeadDetail(
        items(List.of(lead)).get(0),
        timeline,
        assignments,
        lead.getOnline21SyncStatus().name(),
        lead.getAdvisorNoticeStatus().name(),
        tasks.ofLead(lead),
        steps(lead),
        shared,
        tasks.suggestedStage(lead, leadTasks),
        candidates);
  }

  /**
   * Cambio de etapa: cierra la etapa anterior y abre la nueva en el historial
   * (fechas de entrada y salida). Al llegar a "Oferta aceptada" se vincula
   * solo con el expediente del inmueble si hay exactamente uno con esa clave.
   */
  public LeadDetail changeStage(UUID id, LeadStage stage, LostReason reasonCode, String lostReason, CurrentUser user) {
    Lead lead = load(id, user);
    String detail = lostReason == null || lostReason.isBlank() ? null : lostReason.trim();
    if (stage == LeadStage.LOST && reasonCode == null && detail == null) {
      throw new UnprocessableException("LOST_REASON_REQUIRED", "Indica el motivo por el que se perdió el lead.");
    }
    if (stage == LeadStage.LOST && reasonCode == LostReason.OTHER && detail == null) {
      throw new UnprocessableException("LOST_REASON_REQUIRED", "Describe el motivo de pérdida.");
    }
    if (lead.getStage() != stage) {
      Instant now = clock.instant();
      closeStage(lead, now);
      lead.changeStage(stage, reasonCode, detail, now);
      stageHistory.save(new LeadStageHistory(lead.getId(), stage, now, null, user.name()));
      recorder.activity(
          lead,
          LeadActivity.Kind.STAGE,
          stage == LeadStage.LOST
              ? "Marcado como perdido: " + lostReasonLabel(lead.getLostReasonCode()) + (detail != null ? " — " + detail : "") + "."
              : "Etapa cambiada a \"" + stageLabel(stage) + "\".",
          user.id(),
          user.name());
      if ((stage == LeadStage.OFFER_ACCEPTED || stage == LeadStage.WON) && lead.getExpedienteId() == null) {
        List<ExpedienteCandidate> candidates = candidatesFor(lead);
        if (candidates.size() == 1) {
          link(lead, candidates.get(0).id(), candidates.get(0).folio(), "automáticamente por la clave del inmueble", user);
        }
      }
    }
    return get(id, user);
  }

  public LeadDetail changeStage(UUID id, LeadStage stage, String lostReason, CurrentUser user) {
    return changeStage(id, stage, null, lostReason, user);
  }

  /** Corrige la ficha. Cada campo modificado queda en el historial (incluida la fuente, CC#29). */
  public LeadDetail update(UUID id, UpdateLead req, CurrentUser user) {
    Lead lead = load(id, user);
    if (req.name() == null || req.name().isBlank()) {
      throw new UnprocessableException("NAME_REQUIRED", "El nombre es obligatorio.");
    }
    List<String> changed = new ArrayList<>();
    track(changed, "nombre", lead.getName(), req.name().strip());
    track(changed, "teléfono", lead.getPhone(), blank(req.phone()));
    track(changed, "correo", lead.getEmail(), blank(req.email()));
    lead.rename(req.name());
    lead.setPhone(req.phone());
    lead.setEmail(req.email());
    if (req.interest() != null && req.interest() != lead.getInterest()) {
      changed.add("tipo de operación");
      lead.interest(req.interest());
    }
    if (req.sourceCode() != null && !req.sourceCode().isBlank()) {
      Resolved source = sources.resolve(req.sourceCode(), req.sourceDetail(), lead.getSourceCode());
      if (!source.code().equals(lead.getSourceCode()) || !Objects.equals(source.detail(), lead.getSourceDetail())) {
        Map<String, SourceOption> catalogByCode = sources.byCode();
        String before = Optional.ofNullable(catalogByCode.get(lead.getSourceCode())).map(SourceOption::label).orElse(lead.getSource().name());
        changed.add("fuente (" + before + " → " + source.label() + (source.detail() != null ? ": " + source.detail() : "") + ")");
        lead.source(source.channel(), source.code(), source.detail());
      }
    }
    track(changed, "lo que nos dijo", lead.getSummary(), blank(req.summary()));
    lead.replaceSummary(req.summary());
    if (req.propertyReference() != null) {
      String ref = blank(req.propertyReference());
      String current = lead.getPropertyOnline21Id() != null ? lead.getPropertyOnline21Id().toString() : lead.getPropertyKey();
      if (ref == null && current != null) {
        lead.attachProperty(null, null, null);
        changed.add("propiedad de interés");
      } else if (ref != null && !ref.equalsIgnoreCase(String.valueOf(current)) && !ref.equalsIgnoreCase(String.valueOf(lead.getPropertyKey()))) {
        attachProperty(lead, ref);
        changed.add("propiedad de interés");
      }
    }
    if (req.profile() != null) {
      applyProfile(lead, req.profile(), changed);
    }
    if (!changed.isEmpty()) {
      lead.touch(clock.instant());
      recorder.activity(lead, LeadActivity.Kind.EDIT, "Actualizó la ficha: " + String.join(", ", changed) + ".", user.id(), user.name());
    }
    return get(id, user);
  }

  /** Inmueble del portafolio que se le mostró o propuso al prospecto. */
  public LeadDetail shareProperty(UUID id, String reference, String note, CurrentUser user) {
    Lead lead = load(id, user);
    if (reference == null || reference.isBlank()) {
      throw new UnprocessableException("PROPERTY_REQUIRED", "Indica el número o la clave de la propiedad.");
    }
    Optional<CatalogProperty> property = catalog.find(reference.strip()).property();
    LeadSharedProperty shared =
        property
            .map(p -> new LeadSharedProperty(lead.getId(), p.id(), p.internalKey() != null ? p.internalKey() : p.key(), p.shortTitle(), note, user.name(), clock.instant()))
            .orElseGet(() -> new LeadSharedProperty(lead.getId(), null, reference.strip(), null, note, user.name(), clock.instant()));
    sharedProperties.save(shared);
    lead.touch(clock.instant());
    recorder.activity(
        lead,
        LeadActivity.Kind.PROPERTY_SHARED,
        "Le compartió la propiedad " + (shared.getPropertyTitle() != null ? shared.getPropertyTitle() : shared.getPropertyKey()) + (shared.getNote() != null ? ": " + shared.getNote() : "") + ".",
        user.id(),
        user.name());
    return get(id, user);
  }

  /** Vincula (por folio) o desvincula (folio vacío) el expediente del inmueble. */
  public LeadDetail linkExpediente(UUID id, String folio, CurrentUser user) {
    Lead lead = load(id, user);
    if (folio == null || folio.isBlank()) {
      if (lead.getExpedienteId() != null) {
        String before = lead.getExpedienteFolio();
        lead.linkExpediente(null, null);
        recorder.activity(lead, LeadActivity.Kind.EXPEDIENTE, "Se desvinculó del expediente " + before + ".", user.id(), user.name());
      }
      return get(id, user);
    }
    ExpedienteRef ref = expedientes.refByFolio(folio).orElseThrow(() -> new UnprocessableException("EXPEDIENTE_NOT_FOUND", "No existe un expediente con el folio " + folio.strip() + "."));
    link(lead, ref.id(), ref.folio(), "manualmente", user);
    return get(id, user);
  }

  private void link(Lead lead, UUID expedienteId, String folio, String how, CurrentUser user) {
    lead.linkExpediente(expedienteId, folio);
    recorder.activity(lead, LeadActivity.Kind.EXPEDIENTE, "Vinculado " + how + " al expediente " + folio + ".", user.id(), user.name());
  }

  /**
   * Asignación manual. Si el asesor está fuera de horario, ausente, en pausa o
   * ya alcanzó su límite, se exige un motivo de excepción (queda en la bitácora).
   */
  public LeadDetail reassign(UUID id, UUID advisorId, String exceptionReason, CurrentUser user) {
    Lead lead = load(id, user);
    if (advisorId == null || (advisorId.equals(lead.getAdvisorUserId()) && lead.getAssignmentStatus() == AssignmentStatus.ASSIGNED)) {
      return get(id, user);
    }
    assignManually(lead, advisorId, exceptionReason, user);
    return get(id, user);
  }

  private void assignManually(Lead lead, UUID advisorId, String exceptionReason, CurrentUser user) {
    String name =
        staff.activeStaff().stream()
            .filter(m -> m.id().equals(advisorId))
            .map(m -> m.name())
            .findFirst()
            .orElseThrow(() -> new UnprocessableException("ADVISOR_INACTIVE", "Ese usuario no está activo."));
    AdvisorState state = availability.state(availability.snapshot(null), advisorId);
    String reason = exceptionReason == null || exceptionReason.isBlank() ? null : exceptionReason.trim();
    if (state.status() != Status.AVAILABLE && reason == null) {
      throw new UnprocessableException(
          "ASSIGNMENT_EXCEPTION_REASON_REQUIRED", name + ": " + state.detail() + ". Indica el motivo de la excepción para asignarlo de todos modos.");
    }
    recorder.assignManually(lead, advisorId, name, state.status() == Status.AVAILABLE ? null : state.detail() + " — " + reason, user.id(), user.name());
  }

  /** Vuelve a mandar la ficha al asesor que atiende el lead (p. ej. si cambió su WhatsApp o el aviso falló). */
  public LeadDetail resendNotice(UUID id, CurrentUser user) {
    Lead lead = load(id, user);
    if (lead.getAssignmentStatus() != AssignmentStatus.ASSIGNED || lead.getAdvisorUserId() == null) {
      throw new UnprocessableException("LEAD_NOT_ASSIGNED", "El lead todavía no tiene un asesor asignado.");
    }
    String name = staff.nameOf(lead.getAdvisorUserId(), new HashMap<>());
    recorder.notifyAdvisor(lead, lead.getAdvisorUserId(), name, LeadRecorder.RESENT);
    recorder.activity(lead, LeadActivity.Kind.NOTICE, "Se pidió reenviar la ficha a " + name + ".", user.id(), user.name());
    return get(id, user);
  }

  public LeadDetail addNote(UUID id, String text, CurrentUser user) {
    Lead lead = load(id, user);
    if (text == null || text.isBlank()) {
      throw new UnprocessableException("NOTE_EMPTY", "La nota está vacía.");
    }
    lead.touch(clock.instant());
    recorder.activity(lead, LeadActivity.Kind.NOTE, text.trim(), user.id(), user.name());
    return get(id, user);
  }

  /** Alta manual (llamada, oficina, referido). Si no se indica asesor: a la ruleta o al propio usuario. */
  public LeadDetail create(CreateLead req, CurrentUser user) {
    if (req.name() == null || req.name().isBlank()) {
      throw new UnprocessableException("NAME_REQUIRED", "El nombre es obligatorio.");
    }
    LeadSource channel = req.source() == null ? LeadSource.PHONE : req.source();
    Resolved source = sources.resolve(req.sourceCode() != null && !req.sourceCode().isBlank() ? req.sourceCode() : channel.defaultCode(), req.sourceDetail(), req.sourceCode() == null ? channel.defaultCode() : null);
    Lead draft = new Lead(req.name(), req.phone(), source.channel(), source.detail(), req.interest(), clock.instant());
    draft.source(source.channel(), source.code(), source.detail());
    draft.setEmail(req.email());
    draft.describe(req.summary(), req.budget());
    if (req.profile() != null) {
      applyProfile(draft, req.profile(), new ArrayList<>());
    }
    Long propertyAdvisor = null;
    if (req.propertyReference() != null && !req.propertyReference().isBlank()) {
      propertyAdvisor = attachProperty(draft, req.propertyReference());
    }
    // save() con id asignado hace merge: se trabaja con la instancia administrada que devuelve.
    Lead lead = leads.save(draft);
    stageHistory.save(new LeadStageHistory(lead.getId(), LeadStage.NEW, lead.getStageEnteredAt(), null, user.name()));
    recorder.activity(lead, LeadActivity.Kind.CREATED, "Registrado por " + user.name() + " · fuente: " + source.label() + (source.detail() != null ? " (" + source.detail() + ")" : "") + ".", user.id(), user.name());

    boolean canAssign = user.hasPermission("LEAD_ASSIGN");
    if (req.advisorId() != null && canAssign) {
      assignManually(lead, req.advisorId(), req.exceptionReason(), user);
    } else if (req.autoAssign() && canAssign) {
      // Mismo servicio único que usa el bot.
      recorder.apply(lead, assignment.decideNew(propertyAdvisor, false), user.id(), user.name());
    } else if (!user.hasPermission("LEAD_VIEW_ALL")) {
      // Un asesor que registra su propio prospecto se lo queda.
      recorder.assignManually(lead, user.id(), user.name(), null, user.id(), user.name());
    } else {
      lead.markUnassigned("Registrado sin asignar");
    }
    recorder.pushToOnline21(lead, propertyAdvisor != null ? propertyAdvisor : recorder.online21IdOf(lead.getAdvisorUserId()));
    return get(lead.getId(), user);
  }

  // ---------------------------------------------------------------------

  /** Devuelve el asesor de 21Online de la propiedad (si se encontró). */
  private Long attachProperty(Lead lead, String reference) {
    Optional<CatalogProperty> property = catalog.find(reference.strip()).property();
    if (property.isPresent()) {
      lead.attachProperty(property.get().id(), property.get().key(), property.get().shortTitle());
      lead.propertyOwner(null, property.get().advisorOnline21Id());
      return property.get().advisorOnline21Id();
    }
    lead.attachProperty(null, reference.trim(), null);
    return null;
  }

  private void applyProfile(Lead lead, Profile p, List<String> changed) {
    UUID categoryId = null;
    String typeLabel = null;
    if (p.propertySubtypeId() != null) {
      var subtype = propertyTypes.subtype(p.propertySubtypeId()).orElseThrow(() -> new UnprocessableException("PROPERTY_SUBTYPE_UNKNOWN", "Ese tipo de inmueble no existe en el catálogo."));
      categoryId = subtype.categoryId();
      typeLabel = subtype.label(null);
    }
    if (p.budgetMin() != null && p.budgetMax() != null && p.budgetMin().compareTo(p.budgetMax()) > 0) {
      throw new UnprocessableException("BUDGET_RANGE_INVALID", "El presupuesto mínimo no puede ser mayor que el máximo.");
    }
    if (p.closeProbability() != null && (p.closeProbability() < 0 || p.closeProbability() > 100)) {
      throw new UnprocessableException("PROBABILITY_INVALID", "La probabilidad de cierre va de 0 a 100 %.");
    }
    track(changed, "tipo de inmueble", lead.getPropertyTypeLabel(), typeLabel);
    track(changed, "zona", lead.getZone(), blank(p.zone()));
    track(changed, "presupuesto mínimo", str(lead.getBudgetMin()), str(p.budgetMin()));
    track(changed, "presupuesto máximo", str(lead.getBudget()), str(p.budgetMax()));
    track(changed, "características", lead.getDesiredFeatures(), blank(p.desiredFeatures()));
    track(changed, "temperatura", lead.getTemperature() == null ? null : temperatureLabel(lead.getTemperature()), p.temperature() == null ? null : temperatureLabel(p.temperature()));
    track(changed, "probabilidad de cierre", str(lead.getCloseProbability()), str(p.closeProbability()));
    track(changed, "fecha estimada de cierre", str(lead.getExpectedCloseOn()), str(p.expectedCloseOn()));
    if (p.firstContactOn() != null) {
      track(changed, "fecha de primer contacto", str(lead.getFirstContactOn()), str(p.firstContactOn()));
    }
    lead.wishes(categoryId, p.propertySubtypeId(), typeLabel, p.zone(), p.budgetMin(), p.budgetMax(), p.desiredFeatures());
    lead.outlook(p.temperature(), p.closeProbability(), p.expectedCloseOn(), p.firstContactOn());
  }

  private static void track(List<String> changed, String field, String before, String after) {
    if (!Objects.equals(blank(before), blank(after))) {
      changed.add(field);
    }
  }

  private static String blank(String s) {
    return s == null || s.isBlank() ? null : s.strip();
  }

  private static String str(Object o) {
    if (o instanceof BigDecimal b) {
      return b.stripTrailingZeros().toPlainString();
    }
    return o == null ? null : o.toString();
  }

  /** Cierra la etapa actual en el historial (o la crea, para leads anteriores al historial). */
  private void closeStage(Lead lead, Instant now) {
    List<LeadStageHistory> history = stageHistory.findByLeadIdOrderByEnteredAtAsc(lead.getId());
    Optional<LeadStageHistory> open = history.stream().filter(h -> h.getExitedAt() == null).reduce((a, b) -> b);
    if (open.isPresent()) {
      open.get().exit(now);
    } else {
      Instant entered = lead.getStageEnteredAt() != null ? lead.getStageEnteredAt() : lead.getCreatedAt();
      stageHistory.save(new LeadStageHistory(lead.getId(), lead.getStage(), entered, now, null));
    }
  }

  private List<StageStep> steps(Lead lead) {
    Instant now = clock.instant();
    List<LeadStageHistory> history = stageHistory.findByLeadIdOrderByEnteredAtAsc(lead.getId());
    if (history.isEmpty()) {
      Instant entered = lead.getStageEnteredAt() != null ? lead.getStageEnteredAt() : lead.getCreatedAt();
      return List.of(new StageStep(lead.getStage(), entered, null, days(entered, now), null));
    }
    return history.stream().map(h -> new StageStep(h.getStage(), h.getEnteredAt(), h.getExitedAt(), days(h.getEnteredAt(), h.getExitedAt() != null ? h.getExitedAt() : now), h.getActorName())).toList();
  }

  static double days(Instant from, Instant to) {
    if (from == null || to == null) {
      return 0;
    }
    return Math.round(Duration.between(from, to).toMinutes() / 144.0) / 10.0;
  }

  private List<ExpedienteCandidate> candidatesFor(Lead lead) {
    Map<UUID, ExpedienteRef> found = new java.util.LinkedHashMap<>();
    for (String ref : java.util.Arrays.asList(lead.getPropertyKey(), lead.getPropertyOnline21Id() == null ? null : lead.getPropertyOnline21Id().toString())) {
      if (ref != null) {
        expedientes.findByProperty(ref).forEach(e -> found.putIfAbsent(e.id(), e));
      }
    }
    return found.values().stream().map(e -> new ExpedienteCandidate(e.id(), e.folio(), e.ownerDisplayName(), e.propertyKey())).toList();
  }

  private Instant staleBefore() {
    return clock.instant().minus(Duration.ofDays(routing.staleDays()));
  }

  private Lead load(UUID id, CurrentUser user) {
    Lead lead = leads.findById(id).orElseThrow(() -> new NotFoundException("Lead no encontrado"));
    if (!user.hasPermission("LEAD_VIEW_ALL") && !user.id().equals(lead.getAdvisorUserId())) {
      throw new AccessDeniedException("Este lead no está asignado a ti.");
    }
    return lead;
  }

  private Specification<Lead> spec(Filter f, CurrentUser user) {
    return (root, query, cb) -> {
      List<Predicate> p = new ArrayList<>();
      if (!user.hasPermission("LEAD_VIEW_ALL")) {
        p.add(cb.equal(root.get("advisorUserId"), user.id()));
      }
      if (f.advisorId() != null) {
        p.add(cb.equal(root.get("advisorUserId"), f.advisorId()));
      }
      if (f.source() != null) {
        p.add(cb.equal(root.get("source"), f.source()));
      }
      if (f.sourceCode() != null && !f.sourceCode().isBlank()) {
        p.add(cb.equal(root.get("sourceCode"), f.sourceCode()));
      }
      if (f.temperature() != null) {
        p.add(cb.equal(root.get("temperature"), f.temperature()));
      }
      if (f.stage() != null) {
        p.add(cb.equal(root.get("stage"), f.stage()));
      }
      if (f.expedienteId() != null) {
        p.add(cb.equal(root.get("expedienteId"), f.expedienteId()));
      }
      if (f.from() != null) {
        p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from()));
      }
      if (f.propertyOnline21Id() != null) {
        p.add(cb.equal(root.get("propertyOnline21Id"), f.propertyOnline21Id()));
      }
      if (f.q() != null && !f.q().isBlank()) {
        String like = "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%";
        String digits = f.q().replaceAll("\\D", "");
        List<Predicate> any = new ArrayList<>();
        any.add(cb.like(cb.lower(root.get("name")), like));
        any.add(cb.like(cb.lower(cb.coalesce(root.get("propertyKey"), "")), like));
        any.add(cb.like(cb.lower(cb.coalesce(root.get("propertyTitle"), "")), like));
        any.add(cb.like(cb.lower(cb.coalesce(root.get("summary"), "")), like));
        any.add(cb.like(cb.lower(cb.coalesce(root.get("zone"), "")), like));
        if (digits.length() >= 4) {
          any.add(cb.like(cb.coalesce(root.get("phoneDigits"), ""), "%" + digits + "%"));
        }
        p.add(cb.or(any.toArray(Predicate[]::new)));
      }
      return cb.and(p.toArray(Predicate[]::new));
    };
  }

  /** Vistas de varios leads con sus próximas actividades (dos consultas, sin N+1). */
  List<LeadItem> items(Collection<Lead> list) {
    if (list.isEmpty()) {
      return List.of();
    }
    Map<UUID, String> names = staff.names();
    Map<String, SourceOption> catalogByCode = sources.byCode();
    Instant now = clock.instant();
    Instant staleBefore = staleBefore();
    Map<UUID, List<LeadTask>> pendingByLead =
        taskRepository.findByLeadIdIn(list.stream().map(Lead::getId).toList()).stream()
            .filter(t -> t.getStatus() == LeadTask.Status.SCHEDULED)
            .collect(Collectors.groupingBy(LeadTask::getLeadId));
    return list.stream().map(l -> item(l, names, catalogByCode, LeadTaskService.pending(pendingByLead.getOrDefault(l.getId(), List.of())), now, staleBefore)).toList();
  }

  LeadItem item(Lead l, Map<UUID, String> names) {
    return items(List.of(l)).get(0);
  }

  private LeadItem item(Lead l, Map<UUID, String> names, Map<String, SourceOption> catalogByCode, List<LeadTask> pending, Instant now, Instant staleBefore) {
    SourceOption source = l.getSourceCode() == null ? null : catalogByCode.get(l.getSourceCode());
    LeadTask next = pending.isEmpty() ? null : pending.get(0);
    return new LeadItem(
        l.getId(),
        l.getName(),
        l.getPhone(),
        l.getEmail(),
        l.getSource(),
        l.getSourceDetail(),
        l.getInterest(),
        l.getStage(),
        l.getPropertyKey() == null && l.getPropertyOnline21Id() == null ? null : new PropertyView(l.getPropertyOnline21Id(), l.getPropertyKey(), l.getPropertyTitle()),
        l.getAdvisorUserId() == null ? null : new AdvisorRef(l.getAdvisorUserId(), staff.nameOf(l.getAdvisorUserId(), names)),
        l.getAssignmentMethod(),
        l.getSummary(),
        l.getBudget(),
        l.getLostReason(),
        l.getCreatedAt(),
        l.getLastActivityAt(),
        l.getConversationId(),
        l.getAssignmentStatus(),
        l.getAssignmentRule(),
        l.getPendingReason(),
        l.getNextShiftAt(),
        l.getPropertyAdvisorUserId() == null ? null : new AdvisorRef(l.getPropertyAdvisorUserId(), staff.nameOf(l.getPropertyAdvisorUserId(), names)),
        l.getCoordinationNote(),
        l.getSourceCode(),
        source == null ? null : source.label(),
        source == null ? null : source.category(),
        l.getFirstContactOn(),
        l.getPropertyCategoryId(),
        l.getPropertySubtypeId(),
        l.getPropertyTypeLabel(),
        l.getZone(),
        l.getBudgetMin(),
        l.getDesiredFeatures(),
        l.getTemperature(),
        l.getCloseProbability(),
        l.getExpectedCloseOn(),
        l.getLostReasonCode(),
        l.getStageEnteredAt(),
        l.getExpedienteId() == null ? null : new ExpedienteLink(l.getExpedienteId(), l.getExpedienteFolio()),
        next == null ? null : new NextTask(next.getId(), next.getType(), next.getScheduledAt(), next.isOverdue(now)),
        (int) pending.stream().filter(t -> t.isOverdue(now)).count(),
        l.getStage().isOpen() && l.getLastActivityAt().isBefore(staleBefore));
  }

  static String stageLabel(LeadStage stage) {
    return switch (stage) {
      case NEW -> "Nuevo";
      case CONTACTED -> "Primer contacto";
      case QUALIFIED -> "Calificado";
      case APPOINTMENT -> "Cita agendada";
      case VISITED -> "Visita realizada";
      case PROPOSAL -> "Propuesta enviada";
      case NEGOTIATION -> "Negociación";
      case OFFER_ACCEPTED -> "Oferta aceptada";
      case WON -> "Cierre";
      case LOST -> "Perdido";
    };
  }

  static String lostReasonLabel(LostReason reason) {
    if (reason == null) {
      return "otro";
    }
    return switch (reason) {
      case PRICE -> "precio";
      case ZONE -> "zona";
      case TIMING -> "tiempo";
      case COMPETITION -> "competencia";
      case NO_RESPONSE -> "dejó de responder";
      case OTHER -> "otro";
    };
  }

  static String temperatureLabel(LeadTemperature t) {
    return switch (t) {
      case COLD -> "frío";
      case WARM -> "tibio";
      case HOT -> "caliente";
    };
  }
}
