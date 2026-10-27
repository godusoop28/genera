package com.c21genera.leads.application;

import com.c21genera.identity.CurrentUser;
import com.c21genera.leads.LeadStage;
import com.c21genera.leads.domain.Lead;
import com.c21genera.leads.domain.LeadActivity;
import com.c21genera.leads.domain.LeadTask;
import com.c21genera.leads.domain.LeadTask.Result;
import com.c21genera.leads.domain.LeadTask.Status;
import com.c21genera.leads.domain.LeadTask.Type;
import com.c21genera.leads.infrastructure.LeadRepository;
import com.c21genera.leads.infrastructure.LeadTaskRepository;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Control de Cambios #30, partes 3 y 4: actividades del lead (programadas y
 * realizadas) y la agenda de los asesores. Cada alta, resultado o
 * cancelación queda en el historial del lead, que es de solo lectura.
 */
@Service
@Transactional
public class LeadTaskService {

  /** Anticipaciones permitidas para el recordatorio (15 min, 1 h, 1 día). */
  static final Set<Integer> REMINDERS = Set.of(15, 60, 1440);

  private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.forLanguageTag("es-MX"));

  public record TaskView(
      UUID id,
      UUID leadId,
      String leadName,
      LeadStage leadStage,
      Type type,
      Status status,
      String title,
      Instant scheduledAt,
      Integer durationMinutes,
      LeadService.AdvisorRef advisor,
      String propertyKey,
      String propertyTitle,
      String notesBefore,
      Integer reminderMinutes,
      Result result,
      Integer actualMinutes,
      String outcomeNotes,
      String nextAction,
      Instant nextContactAt,
      Instant completedAt,
      boolean overdue,
      String createdByName) {}

  /**
   * done=true registra una actividad ya realizada (con su resultado); si no,
   * la programa. advisorId null = el asesor del lead (o quien la registra).
   */
  public record TaskInput(
      Type type,
      String title,
      Instant scheduledAt,
      Integer durationMinutes,
      UUID advisorId,
      String propertyKey,
      String propertyTitle,
      String notesBefore,
      Integer reminderMinutes,
      boolean done,
      Result result,
      Integer actualMinutes,
      String outcomeNotes,
      String nextAction,
      Instant nextContactAt,
      Type nextType) {}

  public record CompleteInput(Result result, Integer actualMinutes, String outcomeNotes, String nextAction, Instant nextContactAt, Type nextType) {}

  public record AgendaFilter(Instant from, Instant to, UUID advisorId, Type type, UUID leadId, String property) {}

  private final LeadTaskRepository tasks;
  private final LeadRepository leads;
  private final LeadRecorder recorder;
  private final StaffNames staff;
  private final Clock clock;

  LeadTaskService(LeadTaskRepository tasks, LeadRepository leads, LeadRecorder recorder, StaffNames staff, Clock clock) {
    this.tasks = tasks;
    this.leads = leads;
    this.recorder = recorder;
    this.staff = staff;
    this.clock = clock;
  }

  public TaskView create(UUID leadId, TaskInput in, CurrentUser user) {
    Lead lead = load(leadId, user);
    if (in.type() == null) {
      throw new UnprocessableException("TASK_TYPE_REQUIRED", "Elige el tipo de actividad.");
    }
    if (in.type() == Type.OTHER && (in.title() == null || in.title().isBlank())) {
      throw new UnprocessableException("TASK_TITLE_REQUIRED", "Describe la actividad.");
    }
    Instant now = clock.instant();
    UUID advisor = advisorFor(lead, in.advisorId(), user);
    LeadTask task = new LeadTask(lead.getId(), in.type(), in.title(), advisor, user.id(), user.name());
    if (in.done()) {
      task.schedule(in.scheduledAt() == null ? now : in.scheduledAt(), in.durationMinutes(), null, in.propertyKey(), in.propertyTitle(), in.notesBefore());
      task.complete(requireResult(in.result(), in.type()), in.actualMinutes(), in.outcomeNotes(), in.nextAction(), in.nextContactAt(), now);
      tasks.save(task);
      recorder.activity(lead, LeadActivity.Kind.TASK, doneText(task), user.id(), user.name());
      followUp(lead, task, in.nextType(), user);
    } else {
      if (in.scheduledAt() == null) {
        throw new UnprocessableException("TASK_WHEN_REQUIRED", "Indica la fecha y hora de la actividad.");
      }
      task.schedule(in.scheduledAt(), in.durationMinutes(), reminder(in.reminderMinutes()), in.propertyKey(), in.propertyTitle(), in.notesBefore());
      tasks.save(task);
      recorder.activity(lead, LeadActivity.Kind.TASK, "Programó " + typeLabel(task.getType()).toLowerCase(Locale.ROOT) + label(task) + " para el " + when(task.getScheduledAt()) + ".", user.id(), user.name());
    }
    lead.touch(now);
    return view(task, lead, staff.names(), now);
  }

  /** Reprogramar o corregir una actividad que todavía no se realiza. */
  public TaskView reschedule(UUID taskId, TaskInput in, CurrentUser user) {
    LeadTask task = tasks.findById(taskId).orElseThrow(() -> new NotFoundException("Actividad no encontrada"));
    Lead lead = load(task.getLeadId(), user);
    if (task.getStatus() != Status.SCHEDULED) {
      throw new UnprocessableException("TASK_CLOSED", "La actividad ya se realizó o se canceló.");
    }
    Instant before = task.getScheduledAt();
    if (in.scheduledAt() == null) {
      throw new UnprocessableException("TASK_WHEN_REQUIRED", "Indica la fecha y hora de la actividad.");
    }
    if (in.title() != null) {
      task.retitle(in.title());
    }
    task.schedule(in.scheduledAt(), in.durationMinutes(), reminder(in.reminderMinutes()), in.propertyKey(), in.propertyTitle(), in.notesBefore());
    if (in.advisorId() != null && user.hasPermission("LEAD_ASSIGN")) {
      task.reassign(in.advisorId());
    }
    if (before != null && !before.equals(task.getScheduledAt())) {
      recorder.activity(lead, LeadActivity.Kind.TASK, "Reprogramó " + typeLabel(task.getType()).toLowerCase(Locale.ROOT) + label(task) + ": del " + when(before) + " al " + when(task.getScheduledAt()) + ".", user.id(), user.name());
    }
    return view(task, lead, staff.names(), clock.instant());
  }

  public TaskView complete(UUID taskId, CompleteInput in, CurrentUser user) {
    LeadTask task = tasks.findById(taskId).orElseThrow(() -> new NotFoundException("Actividad no encontrada"));
    Lead lead = load(task.getLeadId(), user);
    if (task.getStatus() != Status.SCHEDULED) {
      throw new UnprocessableException("TASK_CLOSED", "La actividad ya se realizó o se canceló.");
    }
    Instant now = clock.instant();
    task.complete(requireResult(in.result(), task.getType()), in.actualMinutes(), in.outcomeNotes(), in.nextAction(), in.nextContactAt(), now);
    recorder.activity(lead, LeadActivity.Kind.TASK, doneText(task), user.id(), user.name());
    lead.touch(now);
    followUp(lead, task, in.nextType(), user);
    return view(task, lead, staff.names(), now);
  }

  public TaskView cancel(UUID taskId, String reason, CurrentUser user) {
    LeadTask task = tasks.findById(taskId).orElseThrow(() -> new NotFoundException("Actividad no encontrada"));
    Lead lead = load(task.getLeadId(), user);
    if (task.getStatus() != Status.SCHEDULED) {
      throw new UnprocessableException("TASK_CLOSED", "La actividad ya se realizó o se canceló.");
    }
    task.cancel(clock.instant());
    String why = reason == null || reason.isBlank() ? "" : ": " + reason.strip();
    recorder.activity(lead, LeadActivity.Kind.TASK, "Canceló " + typeLabel(task.getType()).toLowerCase(Locale.ROOT) + label(task) + " del " + when(task.getScheduledAt()) + why + ".", user.id(), user.name());
    return view(task, lead, staff.names(), clock.instant());
  }

  @Transactional(readOnly = true)
  public List<TaskView> ofLead(Lead lead) {
    Instant now = clock.instant();
    Map<UUID, String> names = staff.names();
    return tasks.findByLeadIdOrderByScheduledAtDesc(lead.getId()).stream().map(t -> view(t, lead, names, now)).toList();
  }

  /**
   * Agenda (día, semana, mes o lista): lo del rango más lo vencido sin
   * resultado. Un asesor sin LEAD_VIEW_ALL solo ve sus actividades y las de
   * sus leads.
   */
  @Transactional(readOnly = true)
  public List<TaskView> agenda(AgendaFilter f, CurrentUser user) {
    Instant now = clock.instant();
    Instant from = f.from() == null ? now.minus(Duration.ofDays(7)) : f.from();
    Instant to = f.to() == null || !f.to().isAfter(from) ? from.plus(Duration.ofDays(42)) : f.to();
    // La agenda general se limita a ~4 meses; la de un solo lead (su historial) puede abarcar más.
    int maxDays = f.leadId() == null ? 120 : 500;
    if (Duration.between(from, to).toDays() > maxDays) {
      to = from.plus(Duration.ofDays(maxDays));
    }
    List<LeadTask> found = tasks.agenda(from, to, now);
    Map<UUID, Lead> byId = leads.findAllById(found.stream().map(LeadTask::getLeadId).collect(Collectors.toSet())).stream().collect(Collectors.toMap(Lead::getId, Function.identity()));
    boolean all = user.hasPermission("LEAD_VIEW_ALL");
    String property = f.property() == null || f.property().isBlank() ? null : f.property().strip().toLowerCase(Locale.ROOT);
    Map<UUID, String> names = staff.names();
    return found.stream()
        .filter(t -> byId.containsKey(t.getLeadId()))
        .filter(t -> all || user.id().equals(t.getAdvisorUserId()) || user.id().equals(byId.get(t.getLeadId()).getAdvisorUserId()))
        .filter(t -> f.advisorId() == null || f.advisorId().equals(t.getAdvisorUserId()))
        .filter(t -> f.type() == null || f.type() == t.getType())
        .filter(t -> f.leadId() == null || f.leadId().equals(t.getLeadId()))
        .filter(t -> property == null || contains(t.getPropertyKey(), property) || contains(t.getPropertyTitle(), property))
        .map(t -> view(t, byId.get(t.getLeadId()), names, now))
        .toList();
  }

  /**
   * Etapa que sugieren las actividades (el asesor decide si la aplica). Solo
   * sugiere avanzar, nunca retroceder, y nunca cierra el lead.
   */
  LeadStage suggestedStage(Lead lead, Collection<LeadTask> leadTasks) {
    if (!lead.getStage().isOpen()) {
      return null;
    }
    LeadStage best = null;
    for (LeadTask t : leadTasks) {
      LeadStage s = stageFor(t);
      if (s != null && (best == null || s.ordinal() > best.ordinal())) {
        best = s;
      }
    }
    return best != null && best.ordinal() > lead.getStage().ordinal() ? best : null;
  }

  private static LeadStage stageFor(LeadTask t) {
    if (t.getStatus() == Status.SCHEDULED) {
      return t.getType() == Type.VISIT || t.getType() == Type.OFFICE_MEETING || t.getType() == Type.VIDEO_CALL ? LeadStage.APPOINTMENT : null;
    }
    if (t.getStatus() != Status.DONE || t.getResult() == Result.NO_ANSWER || t.getResult() == Result.REJECTED) {
      return null;
    }
    return switch (t.getType()) {
      case VISIT -> LeadStage.VISITED;
      case PROPOSAL -> LeadStage.PROPOSAL;
      case OFFER_FOLLOW_UP -> LeadStage.NEGOTIATION;
      case OFFICE_MEETING, VIDEO_CALL -> LeadStage.QUALIFIED;
      case CALL, WHATSAPP, EMAIL -> t.getResult() == Result.INTERESTED || t.getResult() == Result.ADVANCED ? LeadStage.QUALIFIED : LeadStage.CONTACTED;
      case NOTE, OTHER -> null;
    };
  }

  /** "Siguiente acción" con fecha: crea sola la próxima actividad programada. */
  private void followUp(Lead lead, LeadTask done, Type nextType, CurrentUser user) {
    if (done.getNextContactAt() == null || !done.getNextContactAt().isAfter(clock.instant())) {
      return;
    }
    Type type = nextType != null ? nextType : (done.getType() == Type.NOTE ? Type.CALL : done.getType());
    LeadTask next = new LeadTask(lead.getId(), type, done.getNextAction(), done.getAdvisorUserId(), user.id(), user.name());
    next.schedule(done.getNextContactAt(), null, 60, done.getPropertyKey(), done.getPropertyTitle(), done.getNextAction());
    tasks.save(next);
    recorder.activity(lead, LeadActivity.Kind.TASK, "Siguiente contacto programado: " + typeLabel(type).toLowerCase(Locale.ROOT) + " el " + when(next.getScheduledAt()) + ".", user.id(), user.name());
  }

  private UUID advisorFor(Lead lead, UUID requested, CurrentUser user) {
    if (requested != null && (user.hasPermission("LEAD_ASSIGN") || requested.equals(user.id()))) {
      return requested;
    }
    return lead.getAdvisorUserId() != null ? lead.getAdvisorUserId() : user.id();
  }

  private static Result requireResult(Result result, Type type) {
    if (result != null) {
      return result;
    }
    if (type == Type.NOTE) {
      return Result.CONTACTED;
    }
    throw new UnprocessableException("TASK_RESULT_REQUIRED", "Indica el resultado de la actividad.");
  }

  private static Integer reminder(Integer minutes) {
    if (minutes == null || minutes <= 0) {
      return null;
    }
    if (!REMINDERS.contains(minutes)) {
      throw new UnprocessableException("TASK_REMINDER_INVALID", "El recordatorio puede ser 15 minutos, 1 hora o 1 día antes.");
    }
    return minutes;
  }

  private Lead load(UUID leadId, CurrentUser user) {
    Lead lead = leads.findById(leadId).orElseThrow(() -> new NotFoundException("Lead no encontrado"));
    if (!user.hasPermission("LEAD_VIEW_ALL") && !user.id().equals(lead.getAdvisorUserId())) {
      throw new AccessDeniedException("Este lead no está asignado a ti.");
    }
    return lead;
  }

  TaskView view(LeadTask t, Lead lead, Map<UUID, String> names, Instant now) {
    return new TaskView(
        t.getId(),
        t.getLeadId(),
        lead == null ? null : lead.getName(),
        lead == null ? null : lead.getStage(),
        t.getType(),
        t.getStatus(),
        t.getTitle(),
        t.getScheduledAt(),
        t.getDurationMinutes(),
        t.getAdvisorUserId() == null ? null : new LeadService.AdvisorRef(t.getAdvisorUserId(), staff.nameOf(t.getAdvisorUserId(), names)),
        t.getPropertyKey(),
        t.getPropertyTitle(),
        t.getNotesBefore(),
        t.getReminderMinutes(),
        t.getResult(),
        t.getActualMinutes(),
        t.getOutcomeNotes(),
        t.getNextAction(),
        t.getNextContactAt(),
        t.getCompletedAt(),
        t.isOverdue(now),
        t.getCreatedByName());
  }

  static List<LeadTask> pending(Collection<LeadTask> all) {
    return all.stream().filter(t -> t.getStatus() == Status.SCHEDULED).sorted(Comparator.comparing(LeadTask::getScheduledAt, Comparator.nullsLast(Comparator.naturalOrder()))).toList();
  }

  private static String doneText(LeadTask t) {
    StringBuilder b = new StringBuilder(typeLabel(t.getType())).append(label(t)).append(": ").append(resultLabel(t.getResult()).toLowerCase(Locale.ROOT));
    if (t.getActualMinutes() != null) {
      b.append(" (").append(t.getActualMinutes()).append(" min)");
    }
    b.append('.');
    if (t.getOutcomeNotes() != null) {
      b.append(' ').append(t.getOutcomeNotes());
    }
    if (t.getNextAction() != null) {
      b.append(" Siguiente: ").append(t.getNextAction()).append('.');
    }
    return b.toString();
  }

  private static String label(LeadTask t) {
    StringBuilder b = new StringBuilder();
    if (t.getTitle() != null) {
      b.append(" \"").append(t.getTitle()).append('"');
    }
    if (t.getPropertyKey() != null || t.getPropertyTitle() != null) {
      b.append(" (").append(t.getPropertyTitle() != null ? t.getPropertyTitle() : t.getPropertyKey()).append(')');
    }
    return b.toString();
  }

  static String when(Instant at) {
    return at == null ? "—" : WHEN.format(ZonedDateTime.ofInstant(at, StaffNames.MEXICO_CITY));
  }

  private static boolean contains(String value, String needle) {
    return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
  }

  static String typeLabel(Type type) {
    return switch (type) {
      case CALL -> "Llamada";
      case WHATSAPP -> "Mensaje de WhatsApp";
      case EMAIL -> "Correo electrónico";
      case VISIT -> "Visita al inmueble";
      case OFFICE_MEETING -> "Reunión en oficina";
      case VIDEO_CALL -> "Videollamada";
      case PROPOSAL -> "Envío de propuesta";
      case OFFER_FOLLOW_UP -> "Seguimiento de oferta";
      case NOTE -> "Nota interna";
      case OTHER -> "Actividad";
    };
  }

  static String resultLabel(Result result) {
    return switch (result) {
      case CONTACTED -> "Contactado";
      case NO_ANSWER -> "No contestó";
      case RESCHEDULED -> "Reagendó";
      case REJECTED -> "Rechazó";
      case INTERESTED -> "Interesado";
      case ADVANCED -> "Avanzó";
    };
  }
}
