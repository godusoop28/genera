package com.c21genera.leads.application;

import com.c21genera.leads.AssignmentMethod;
import com.c21genera.leads.AssignmentStatus;
import com.c21genera.leads.LeadIntake;
import com.c21genera.leads.LeadSource;
import com.c21genera.leads.application.AssignmentService.Decision;
import com.c21genera.leads.domain.AdvisorProfile;
import com.c21genera.leads.domain.AssignmentLog;
import com.c21genera.leads.domain.Lead;
import com.c21genera.leads.domain.LeadActivity;
import com.c21genera.leads.domain.SyncStatus;
import com.c21genera.leads.infrastructure.AdvisorProfileRepository;
import com.c21genera.leads.infrastructure.LeadRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Canalización desde canales automáticos (asistente de WhatsApp):
 *
 * <ul>
 *   <li>Idempotente por {@code requestKey}: un reintento del mismo evento
 *       devuelve el mismo lead y no consume otro turno de la ronda ni cuenta
 *       otro lead en el límite diario.
 *   <li>A. Continuidad: si el mismo teléfono tiene un lead activo (abierto, en
 *       los últimos 30 días) con asesor o reservado, se conserva al responsable
 *       y la nueva solicitud se anexa; nunca se reasigna por un mensaje nuevo
 *       ni por un cambio de turno.
 *   <li>D. Si en esa continuidad pregunta por una propiedad de OTRO asesor, no
 *       se sobrescribe al responsable: se registra el interés y se señala el
 *       conflicto para coordinación.
 *   <li>Lead nuevo: decide el servicio único de asignación (reglas B y C).
 * </ul>
 */
@Service
@Transactional
class LeadIntakeService implements LeadIntake {

  static final Duration RECENT_LEAD_WINDOW = Duration.ofDays(30);

  private final LeadRepository leads;
  private final AdvisorProfileRepository profiles;
  private final AssignmentService assignment;
  private final LeadRecorder recorder;
  private final StaffNames staff;
  private final Clock clock;

  LeadIntakeService(LeadRepository leads, AdvisorProfileRepository profiles, AssignmentService assignment, LeadRecorder recorder, StaffNames staff, Clock clock) {
    this.leads = leads;
    this.profiles = profiles;
    this.assignment = assignment;
    this.recorder = recorder;
    this.staff = staff;
    this.clock = clock;
  }

  @Override
  public HandoffResult handoff(HandoffRequest req) {
    Map<UUID, String> names = new HashMap<>();
    if (!req.dryRun() && req.requestKey() != null) {
      Optional<Lead> same = leads.findByRequestKey(req.requestKey());
      if (same.isPresent()) {
        return resultOf(same.get(), names, false);
      }
    }

    Optional<Lead> active = activeLead(req.phone());
    if (active.isPresent()) {
      return continuity(active.get(), req, names);
    }

    Long ownerOnline21 = req.property() != null ? req.property().advisorOnline21Id() : null;
    Decision decision = assignment.decideNew(ownerOnline21, req.dryRun());
    if (req.dryRun()) {
      // Simulador: no guarda el lead, no mueve la ronda ni manda avisos.
      return new HandoffResult(statusOf(decision.status()), null, staff.nameOf(decision.advisorId(), names), decision.reason(), decision.nextShiftAt(), false);
    }

    Lead draft = new Lead(req.name(), req.phone(), req.source(), req.sourceDetail(), req.interest(), clock.instant());
    if (req.property() != null) {
      draft.attachProperty(req.property().online21Id(), req.property().key(), req.property().title());
      draft.propertyOwner(null, ownerOnline21);
    }
    draft.describe(req.summary(), req.budget());
    draft.linkConversation(req.conversationId(), req.requestKey());
    // save() con id asignado hace merge: se trabaja con la instancia administrada que devuelve.
    Lead lead = leads.save(draft);
    recorder.activity(lead, LeadActivity.Kind.CREATED, "Llegó por " + channel(req.source()) + ".", null, null);
    recorder.apply(lead, decision, null, null);
    recorder.pushToOnline21(lead, ownerOnline21 != null ? ownerOnline21 : recorder.online21IdOf(lead.getAdvisorUserId()));
    return resultOf(lead, names, false);
  }

  private HandoffResult continuity(Lead lead, HandoffRequest req, Map<UUID, String> names) {
    UUID responsible = lead.getAdvisorUserId();
    UUID propertyOwner =
        req.property() != null && req.property().advisorOnline21Id() != null
            ? profiles.findByOnline21UserId(req.property().advisorOnline21Id()).map(AdvisorProfile::getUserId).orElse(null)
            : null;
    boolean conflict = propertyOwner != null && responsible != null && !propertyOwner.equals(responsible);
    if (req.dryRun()) {
      HandoffStatus status = lead.getAssignmentStatus().isPending() ? statusOf(lead.getAssignmentStatus()) : HandoffStatus.EXISTING;
      return new HandoffResult(status, lead.getId(), staff.nameOf(responsible, names), AssignmentMethod.CONTINUITY, lead.getNextShiftAt(), conflict);
    }
    String property = req.property() != null ? " Pregunta por " + req.property().title() + "." : "";
    recorder.activity(lead, LeadActivity.Kind.FOLLOW_UP, "Volvió a escribir por " + channel(req.source()) + "." + property + (req.summary() != null ? " " + req.summary() : ""), null, null);
    if (lead.getPropertyOnline21Id() == null && req.property() != null) {
      lead.attachProperty(req.property().online21Id(), req.property().key(), req.property().title());
      lead.propertyOwner(propertyOwner, req.property().advisorOnline21Id());
    }
    if (conflict) {
      String note =
          "Pregunta por " + req.property().title() + ", cuyo asesor responsable es " + staff.nameOf(propertyOwner, names)
              + "; el lead lo atiende " + staff.nameOf(responsible, names) + ". Coordinar entre ambos.";
      lead.coordination(note);
      recorder.log(lead, AssignmentLog.Outcome.CONFLICT, responsible, AssignmentMethod.CONTINUITY, "Cliente existente pregunta por una propiedad de otro asesor", null, null, note, null, null);
    } else {
      recorder.log(lead, AssignmentLog.Outcome.KEPT, responsible, AssignmentMethod.CONTINUITY, "Continuidad: se conserva al responsable del lead activo", null, null, null, null, null);
    }
    lead.touch(clock.instant());
    if (lead.getAssignmentStatus() == AssignmentStatus.ASSIGNED && responsible != null) {
      // El asesor recibe la ficha actualizada (nueva propiedad o conflicto incluidos).
      recorder.notifyAdvisor(lead, responsible, staff.nameOf(responsible, names), LeadRecorder.FOLLOW_UP);
    }
    HandoffStatus status = lead.getAssignmentStatus().isPending() ? statusOf(lead.getAssignmentStatus()) : HandoffStatus.EXISTING;
    return new HandoffResult(status, lead.getId(), staff.nameOf(responsible, names), AssignmentMethod.CONTINUITY, lead.getNextShiftAt(), conflict);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<RecentLead> recentLeadFor(String phone) {
    Map<UUID, String> names = new HashMap<>();
    return recent(phone).map(l -> new RecentLead(l.getId(), staff.nameOf(l.getAdvisorUserId(), names), l.getStage()));
  }

  @Override
  public void recordAdvisorNotice(UUID leadId, boolean sent, String detail) {
    leads.findById(leadId)
        .ifPresent(
            lead -> {
              lead.markAdvisorNotice(sent ? SyncStatus.DONE : SyncStatus.FAILED);
              recorder.activity(lead, LeadActivity.Kind.NOTICE, sent ? "Aviso por WhatsApp enviado al asesor." : "No se pudo avisar al asesor por WhatsApp" + (detail != null ? ": " + detail : "."), null, null);
            });
  }

  private Optional<Lead> recent(String phone) {
    String digits = Lead.digitsOf(phone);
    if (digits == null) {
      return Optional.empty();
    }
    return leads.findFirstByPhoneDigitsAndCreatedAtAfterOrderByCreatedAtDesc(digits, clock.instant().minus(RECENT_LEAD_WINDOW));
  }

  /** Lead abierto con responsable (asignado o reservado) o pendiente por horario: se continúa ahí. */
  private Optional<Lead> activeLead(String phone) {
    return recent(phone).filter(l -> l.getStage().isOpen() && (l.getAdvisorUserId() != null || l.getAssignmentStatus().isPending()));
  }

  private HandoffResult resultOf(Lead lead, Map<UUID, String> names, boolean conflict) {
    return new HandoffResult(statusOf(lead.getAssignmentStatus()), lead.getId(), staff.nameOf(lead.getAdvisorUserId(), names), lead.getAssignmentMethod(), lead.getNextShiftAt(), conflict);
  }

  private static HandoffStatus statusOf(AssignmentStatus status) {
    return switch (status) {
      case ASSIGNED -> HandoffStatus.ASSIGNED;
      case PENDING_OWNER -> HandoffStatus.PENDING_OWNER;
      case PENDING_SCHEDULE, UNASSIGNED -> HandoffStatus.PENDING_SCHEDULE;
    };
  }

  private static String channel(LeadSource source) {
    return source == LeadSource.WHATSAPP_BOT ? "WhatsApp (asistente)" : source.name();
  }
}
