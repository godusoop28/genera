package com.c21genera.leads.application;

import com.c21genera.identity.CurrentUser;
import com.c21genera.leads.AssignmentMethod;
import com.c21genera.leads.AssignmentStatus;
import com.c21genera.leads.LeadInterest;
import com.c21genera.leads.LeadSource;
import com.c21genera.leads.LeadStage;
import com.c21genera.leads.application.AvailabilityService.AdvisorState;
import com.c21genera.leads.application.AvailabilityService.Status;
import com.c21genera.leads.domain.AssignmentLog;
import com.c21genera.leads.domain.Lead;
import com.c21genera.leads.domain.LeadActivity;
import com.c21genera.leads.infrastructure.AssignmentLogRepository;
import com.c21genera.leads.infrastructure.LeadActivityRepository;
import com.c21genera.leads.infrastructure.LeadRepository;
import com.c21genera.online21.PropertyCatalog;
import com.c21genera.online21.PropertyCatalog.CatalogProperty;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operaciones del personal sobre los leads (lista, detalle, etapa, reasignación, notas, alta manual). */
@Service
@Transactional
public class LeadService {

  public enum Group {
    ALL,
    UNASSIGNED,
    PENDING,
    NEW,
    OPEN,
    WON,
    LOST;

    boolean matches(Lead l) {
      return switch (this) {
        case ALL -> true;
        case UNASSIGNED -> l.getAssignmentStatus() == AssignmentStatus.UNASSIGNED || (l.getAdvisorUserId() == null && !l.getAssignmentStatus().isPending());
        case PENDING -> l.getAssignmentStatus().isPending();
        case NEW -> l.getStage() == LeadStage.NEW;
        case OPEN -> l.getStage().isOpen();
        case WON -> l.getStage() == LeadStage.WON;
        case LOST -> l.getStage() == LeadStage.LOST;
      };
    }
  }

  public record Filter(String q, Group group, UUID advisorId, LeadSource source, Instant from) {}

  public record PropertyView(Long online21Id, String key, String title) {}

  public record AdvisorRef(UUID id, String name) {}

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
      String coordinationNote) {}

  public record ActivityView(UUID id, LeadActivity.Kind kind, String text, String actorName, Instant createdAt) {}

  /** Una entrada de la bitácora de asignación: cuándo, a quién, por qué, con qué regla y turno. */
  public record AssignmentView(
      UUID id, AssignmentLog.Outcome outcome, AdvisorRef advisor, AssignmentMethod reason, String rule, UUID shiftId, String roundMode, String detail, String actorName, Instant createdAt) {}

  public record LeadDetail(LeadItem lead, List<ActivityView> activities, List<AssignmentView> assignments, String online21SyncStatus, String advisorNoticeStatus) {}

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
      boolean autoAssign) {}

  private final LeadRepository leads;
  private final LeadActivityRepository activities;
  private final AssignmentLogRepository assignmentLogs;
  private final LeadRecorder recorder;
  private final AssignmentService assignment;
  private final AvailabilityService availability;
  private final StaffNames staff;
  private final PropertyCatalog catalog;
  private final Clock clock;

  LeadService(
      LeadRepository leads,
      LeadActivityRepository activities,
      AssignmentLogRepository assignmentLogs,
      LeadRecorder recorder,
      AssignmentService assignment,
      AvailabilityService availability,
      StaffNames staff,
      PropertyCatalog catalog,
      Clock clock) {
    this.leads = leads;
    this.activities = activities;
    this.assignmentLogs = assignmentLogs;
    this.availability = availability;
    this.recorder = recorder;
    this.assignment = assignment;
    this.staff = staff;
    this.catalog = catalog;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public LeadPage list(Filter filter, CurrentUser user, int page, int size) {
    List<Lead> scoped = leads.findAll(spec(filter, user), Sort.by(Sort.Direction.DESC, "createdAt"));
    Map<Group, Long> counts = new EnumMap<>(Group.class);
    for (Group g : Group.values()) {
      counts.put(g, scoped.stream().filter(g::matches).count());
    }
    Group group = filter.group() == null ? Group.ALL : filter.group();
    List<Lead> visible = scoped.stream().filter(group::matches).toList();
    int safeSize = Math.max(1, Math.min(size, 100));
    int from = Math.min(page * safeSize, visible.size());
    int to = Math.min(from + safeSize, visible.size());
    Map<UUID, String> names = staff.names();
    List<LeadItem> items = visible.subList(from, to).stream().map(l -> item(l, names)).toList();
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
    return new LeadDetail(item(lead, names), timeline, assignments, lead.getOnline21SyncStatus().name(), lead.getAdvisorNoticeStatus().name());
  }

  public LeadDetail changeStage(UUID id, LeadStage stage, String lostReason, CurrentUser user) {
    Lead lead = load(id, user);
    if (stage == LeadStage.LOST && (lostReason == null || lostReason.isBlank())) {
      throw new UnprocessableException("LOST_REASON_REQUIRED", "Indica el motivo por el que se perdió el lead.");
    }
    if (lead.getStage() != stage) {
      lead.changeStage(stage, lostReason == null ? null : lostReason.trim(), clock.instant());
      recorder.activity(
          lead,
          LeadActivity.Kind.STAGE,
          stage == LeadStage.LOST ? "Marcado como perdido: " + lostReason.trim() + "." : "Etapa cambiada a \"" + stageLabel(stage) + "\".",
          user.id(),
          user.name());
    }
    return get(id, user);
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
    Lead draft = new Lead(req.name(), req.phone(), req.source() == null ? LeadSource.PHONE : req.source(), req.sourceDetail(), req.interest(), clock.instant());
    draft.setEmail(req.email());
    draft.describe(req.summary(), req.budget());
    Long propertyAdvisor = null;
    if (req.propertyReference() != null && !req.propertyReference().isBlank()) {
      Optional<CatalogProperty> property = catalog.find(req.propertyReference()).property();
      if (property.isPresent()) {
        draft.attachProperty(property.get().id(), property.get().key(), property.get().shortTitle());
        propertyAdvisor = property.get().advisorOnline21Id();
        draft.propertyOwner(null, propertyAdvisor);
      } else {
        draft.attachProperty(null, req.propertyReference().trim(), null);
      }
    }
    // save() con id asignado hace merge: se trabaja con la instancia administrada que devuelve.
    Lead lead = leads.save(draft);
    recorder.activity(lead, LeadActivity.Kind.CREATED, "Registrado por " + user.name() + ".", user.id(), user.name());

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
      if (f.from() != null) {
        p.add(cb.greaterThanOrEqualTo(root.get("createdAt"), f.from()));
      }
      if (f.q() != null && !f.q().isBlank()) {
        String like = "%" + f.q().trim().toLowerCase(Locale.ROOT) + "%";
        String digits = f.q().replaceAll("\\D", "");
        List<Predicate> any = new ArrayList<>();
        any.add(cb.like(cb.lower(root.get("name")), like));
        any.add(cb.like(cb.lower(cb.coalesce(root.get("propertyKey"), "")), like));
        any.add(cb.like(cb.lower(cb.coalesce(root.get("propertyTitle"), "")), like));
        any.add(cb.like(cb.lower(cb.coalesce(root.get("summary"), "")), like));
        if (digits.length() >= 4) {
          any.add(cb.like(cb.coalesce(root.get("phoneDigits"), ""), "%" + digits + "%"));
        }
        p.add(cb.or(any.toArray(Predicate[]::new)));
      }
      return cb.and(p.toArray(Predicate[]::new));
    };
  }

  LeadItem item(Lead l, Map<UUID, String> names) {
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
        l.getCoordinationNote());
  }

  static String stageLabel(LeadStage stage) {
    return switch (stage) {
      case NEW -> "Nuevo";
      case CONTACTED -> "Contactado";
      case APPOINTMENT -> "Cita agendada";
      case VISITED -> "Visitó";
      case NEGOTIATION -> "Negociación";
      case WON -> "Cerrado ganado";
      case LOST -> "Perdido";
    };
  }

}
