package com.c21genera.leads.application;

import com.c21genera.identity.CurrentUser;
import com.c21genera.leads.AssignmentMethod;
import com.c21genera.leads.LeadInterest;
import com.c21genera.leads.LeadSource;
import com.c21genera.leads.LeadStage;
import com.c21genera.leads.domain.Lead;
import com.c21genera.leads.domain.LeadActivity;
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
    NEW,
    OPEN,
    WON,
    LOST;

    boolean matches(Lead l) {
      return switch (this) {
        case ALL -> true;
        case UNASSIGNED -> l.getAdvisorUserId() == null;
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
      UUID conversationId) {}

  public record ActivityView(UUID id, LeadActivity.Kind kind, String text, String actorName, Instant createdAt) {}

  public record LeadDetail(LeadItem lead, List<ActivityView> activities, String online21SyncStatus, String advisorNoticeStatus) {}

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
      boolean autoAssign) {}

  private final LeadRepository leads;
  private final LeadActivityRepository activities;
  private final LeadRecorder recorder;
  private final AssignmentService assignment;
  private final StaffNames staff;
  private final PropertyCatalog catalog;
  private final Clock clock;

  LeadService(
      LeadRepository leads,
      LeadActivityRepository activities,
      LeadRecorder recorder,
      AssignmentService assignment,
      StaffNames staff,
      PropertyCatalog catalog,
      Clock clock) {
    this.leads = leads;
    this.activities = activities;
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
    return new LeadDetail(item(lead, names), timeline, lead.getOnline21SyncStatus().name(), lead.getAdvisorNoticeStatus().name());
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

  public LeadDetail reassign(UUID id, UUID advisorId, CurrentUser user) {
    Lead lead = load(id, user);
    if (advisorId == null || advisorId.equals(lead.getAdvisorUserId())) {
      return get(id, user);
    }
    String name =
        staff.activeStaff().stream()
            .filter(m -> m.id().equals(advisorId))
            .map(m -> m.name())
            .findFirst()
            .orElseThrow(() -> new UnprocessableException("ADVISOR_INACTIVE", "Ese usuario no está activo."));
    recorder.assign(lead, advisorId, name, AssignmentMethod.MANUAL, lead.getAdvisorUserId() != null, user.id(), user.name());
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
    Lead draft = new Lead(req.name(), req.phone(), req.source() == null ? LeadSource.PHONE : req.source(), req.sourceDetail(), req.interest(), clock.instant());
    draft.setEmail(req.email());
    draft.describe(req.summary(), req.budget());
    Long propertyAdvisor = null;
    if (req.propertyReference() != null && !req.propertyReference().isBlank()) {
      Optional<CatalogProperty> property = catalog.find(req.propertyReference()).property();
      if (property.isPresent()) {
        draft.attachProperty(property.get().id(), property.get().key(), property.get().shortTitle());
        propertyAdvisor = property.get().advisorOnline21Id();
      } else {
        draft.attachProperty(null, req.propertyReference().trim(), null);
      }
    }
    // save() con id asignado hace merge: se trabaja con la instancia administrada que devuelve.
    Lead lead = leads.save(draft);
    recorder.activity(lead, LeadActivity.Kind.CREATED, "Registrado por " + user.name() + ".", user.id(), user.name());

    Map<UUID, String> names = staff.names();
    boolean canAssign = user.hasPermission("LEAD_ASSIGN");
    if (req.advisorId() != null && canAssign) {
      recorder.assign(lead, req.advisorId(), staff.nameOf(req.advisorId(), names), AssignmentMethod.MANUAL, false, user.id(), user.name());
    } else if (req.autoAssign() && canAssign) {
      Long owner = propertyAdvisor;
      assignment
          .choose(owner, false)
          .ifPresentOrElse(
              a -> recorder.assign(lead, a.advisorUserId(), staff.nameOf(a.advisorUserId(), names), a.method(), false, user.id(), user.name()),
              () -> recorder.activity(lead, LeadActivity.Kind.ASSIGNED, "No había asesor disponible: queda pendiente de asignar.", null, null));
    } else if (!user.hasPermission("LEAD_VIEW_ALL")) {
      // Un asesor que registra su propio prospecto se lo queda.
      recorder.assign(lead, user.id(), user.name(), AssignmentMethod.MANUAL, false, user.id(), user.name());
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
        l.getConversationId());
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
