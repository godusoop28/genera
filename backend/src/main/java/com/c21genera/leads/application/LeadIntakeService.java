package com.c21genera.leads.application;

import com.c21genera.leads.LeadIntake;
import com.c21genera.leads.LeadSource;
import com.c21genera.leads.application.AssignmentService.Assignment;
import com.c21genera.leads.domain.Lead;
import com.c21genera.leads.domain.LeadActivity;
import com.c21genera.leads.domain.SyncStatus;
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
 * Canalización desde canales automáticos (asistente de WhatsApp), con las
 * mismas reglas que Inova:
 *
 * <ul>
 *   <li>Idempotente por {@code requestKey}: un reintento devuelve el mismo lead.
 *   <li>Si el mismo teléfono tiene un lead con asesor en los últimos 30 días,
 *       no se reasigna: se anexa la nueva solicitud como seguimiento.
 *   <li>Si nadie puede recibirlo, el lead se guarda igual (sin asesor) para
 *       que el equipo lo asigne a mano; nunca se pierde.
 * </ul>
 */
@Service
@Transactional
class LeadIntakeService implements LeadIntake {

  static final Duration RECENT_LEAD_WINDOW = Duration.ofDays(30);

  private final LeadRepository leads;
  private final AssignmentService assignment;
  private final LeadRecorder recorder;
  private final StaffNames staff;
  private final Clock clock;

  LeadIntakeService(LeadRepository leads, AssignmentService assignment, LeadRecorder recorder, StaffNames staff, Clock clock) {
    this.leads = leads;
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
        return resultOf(same.get(), names);
      }
    }

    Optional<Lead> recent = recentWithAdvisor(req.phone());
    if (recent.isPresent()) {
      Lead lead = recent.get();
      if (!req.dryRun()) {
        String property = req.property() != null ? " Pregunta por " + req.property().title() + "." : "";
        recorder.activity(lead, LeadActivity.Kind.FOLLOW_UP, "Volvió a escribir por " + channel(req.source()) + "." + property + (req.summary() != null ? " " + req.summary() : ""), null, null);
        lead.touch(clock.instant());
      }
      return new HandoffResult(HandoffStatus.EXISTING, lead.getId(), staff.nameOf(lead.getAdvisorUserId(), names), lead.getAssignmentMethod());
    }

    Optional<Assignment> chosen = assignment.choose(req.property() != null ? req.property().advisorOnline21Id() : null, req.dryRun());
    if (req.dryRun()) {
      return chosen
          .map(a -> new HandoffResult(HandoffStatus.ASSIGNED, null, staff.nameOf(a.advisorUserId(), names), a.method()))
          .orElseGet(() -> new HandoffResult(HandoffStatus.NO_ADVISOR, null, null, null));
    }

    Lead draft = new Lead(req.name(), req.phone(), req.source(), req.sourceDetail(), req.interest(), clock.instant());
    if (req.property() != null) {
      draft.attachProperty(req.property().online21Id(), req.property().key(), req.property().title());
    }
    draft.describe(req.summary(), req.budget());
    draft.linkConversation(req.conversationId(), req.requestKey());
    // save() con id asignado hace merge: se trabaja con la instancia administrada que devuelve.
    Lead lead = leads.save(draft);
    recorder.activity(lead, LeadActivity.Kind.CREATED, "Llegó por " + channel(req.source()) + ".", null, null);

    if (chosen.isPresent()) {
      String name = staff.nameOf(chosen.get().advisorUserId(), names);
      recorder.assign(lead, chosen.get().advisorUserId(), name, chosen.get().method(), false, null, null);
    } else {
      recorder.activity(lead, LeadActivity.Kind.ASSIGNED, "No había asesor disponible: queda pendiente de asignar.", null, null);
    }
    recorder.pushToOnline21(lead, req.property() != null && req.property().advisorOnline21Id() != null ? req.property().advisorOnline21Id() : recorder.online21IdOf(lead.getAdvisorUserId()));
    return resultOf(lead, names);
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

  private Optional<Lead> recentWithAdvisor(String phone) {
    return recent(phone).filter(l -> l.getAdvisorUserId() != null);
  }

  private HandoffResult resultOf(Lead lead, Map<UUID, String> names) {
    return lead.getAdvisorUserId() == null
        ? new HandoffResult(HandoffStatus.NO_ADVISOR, lead.getId(), null, null)
        : new HandoffResult(HandoffStatus.ASSIGNED, lead.getId(), staff.nameOf(lead.getAdvisorUserId(), names), lead.getAssignmentMethod());
  }

  private static String channel(LeadSource source) {
    return source == LeadSource.WHATSAPP_BOT ? "WhatsApp (asistente)" : source.name();
  }
}
