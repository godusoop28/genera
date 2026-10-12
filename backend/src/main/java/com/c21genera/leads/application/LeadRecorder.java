package com.c21genera.leads.application;

import com.c21genera.leads.AssignmentMethod;
import com.c21genera.leads.AssignmentStatus;
import com.c21genera.leads.LeadInterest;
import com.c21genera.leads.application.AssignmentService.Decision;
import com.c21genera.leads.domain.AdvisorProfile;
import com.c21genera.leads.domain.AssignmentLog;
import com.c21genera.leads.domain.Lead;
import com.c21genera.leads.domain.LeadActivity;
import com.c21genera.leads.domain.SyncStatus;
import com.c21genera.leads.infrastructure.AdvisorProfileRepository;
import com.c21genera.leads.infrastructure.AssignmentLogRepository;
import com.c21genera.leads.infrastructure.LeadActivityRepository;
import com.c21genera.online21.ProspectGateway;
import com.c21genera.online21.ProspectGateway.ProspectRequest;
import com.c21genera.shared.events.LeadEvents.LeadAssigned;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Escrituras comunes del lead: actividades, decisiones de asignación (con su bitácora y aviso) y registro en 21Online. */
@Component
class LeadRecorder {

  private static final Map<LeadInterest, String> INTEREST_TEXT =
      Map.of(
          LeadInterest.BUY, "comprar",
          LeadInterest.RENT, "rentar",
          LeadInterest.SELL, "vender su propiedad",
          LeadInterest.LEASE_OUT, "poner en renta su propiedad",
          LeadInterest.INVEST, "invertir",
          LeadInterest.OTHER, "información");

  private final LeadActivityRepository activities;
  private final AssignmentLogRepository logs;
  private final AdvisorProfileRepository profiles;
  private final ProspectGateway prospects;
  private final StaffNames staff;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  LeadRecorder(
      LeadActivityRepository activities,
      AssignmentLogRepository logs,
      AdvisorProfileRepository profiles,
      ProspectGateway prospects,
      StaffNames staff,
      ApplicationEventPublisher events,
      Clock clock) {
    this.activities = activities;
    this.logs = logs;
    this.profiles = profiles;
    this.prospects = prospects;
    this.staff = staff;
    this.events = events;
    this.clock = clock;
  }

  void activity(Lead lead, LeadActivity.Kind kind, String text, UUID actorId, String actorName) {
    activities.save(new LeadActivity(lead.getId(), kind, text, actorId, actorName, clock.instant()));
  }

  void log(Lead lead, AssignmentLog.Outcome outcome, UUID advisorId, AssignmentMethod reason, String rule, UUID shiftId, String roundMode, String detail, UUID actorId, String actorName) {
    logs.save(new AssignmentLog(lead.getId(), outcome, advisorId, reason, rule, shiftId, roundMode, detail, actorId, actorName, clock.instant()));
  }

  /** Aplica la decisión del servicio de asignación al lead (actor null = el sistema). */
  void apply(Lead lead, Decision d, UUID actorId, String actorName) {
    if (d.propertyAdvisorUserId() != null) {
      lead.propertyOwner(d.propertyAdvisorUserId(), lead.getPropertyAdvisorOnline21Id());
    }
    String mode = d.roundMode() == null ? null : d.roundMode().name();
    if (d.status() == AssignmentStatus.ASSIGNED) {
      boolean reassignment = lead.getAdvisorUserId() != null && !lead.getAdvisorUserId().equals(d.advisorId()) && lead.getAssignmentStatus() == AssignmentStatus.ASSIGNED;
      lead.assignTo(d.advisorId(), d.reason(), d.rule(), d.shiftId(), clock.instant());
      String name = staff.nameOf(d.advisorId(), new java.util.HashMap<>());
      log(lead, reassignment ? AssignmentLog.Outcome.REASSIGNED : AssignmentLog.Outcome.ASSIGNED, d.advisorId(), d.reason(), d.rule(), d.shiftId(), mode, d.detail(), actorId, actorName);
      activity(lead, reassignment ? LeadActivity.Kind.REASSIGNED : LeadActivity.Kind.ASSIGNED, "Asignado a " + name + ": " + d.rule() + "." + (d.detail() != null ? " " + d.detail() + "." : ""), actorId, actorName);
      notifyAdvisor(lead, d.advisorId(), name);
      return;
    }
    UUID reserved = d.status() == AssignmentStatus.PENDING_OWNER ? d.advisorId() : null;
    boolean changed = lead.getAssignmentStatus() != d.status() || lead.getPendingReason() == null || !lead.getPendingReason().equals(d.pendingReason());
    lead.pending(d.status(), reserved, d.rule(), d.pendingReason(), d.nextShiftAt(), clock.instant());
    if (changed) {
      log(lead, AssignmentLog.Outcome.PENDING, reserved, d.reason(), d.rule(), null, mode, d.pendingReason(), actorId, actorName);
      activity(lead, LeadActivity.Kind.ASSIGNED, (d.status() == AssignmentStatus.PENDING_OWNER ? "Reservado para el asesor de la propiedad: " : "Pendiente por horario: ") + d.pendingReason() + ".", actorId, actorName);
    }
  }

  /** Asignación manual (ya validada por quien llama). */
  void assignManually(Lead lead, UUID advisorId, String advisorName, String exceptionReason, UUID actorId, String actorName) {
    boolean reassignment = lead.getAdvisorUserId() != null && lead.getAssignmentStatus() == AssignmentStatus.ASSIGNED;
    String rule = "Asignación manual" + (exceptionReason != null ? " con excepción" : "");
    lead.assignTo(advisorId, AssignmentMethod.MANUAL, rule, null, clock.instant());
    log(lead, reassignment ? AssignmentLog.Outcome.REASSIGNED : AssignmentLog.Outcome.ASSIGNED, advisorId, AssignmentMethod.MANUAL, rule, null, null, exceptionReason, actorId, actorName);
    activity(
        lead,
        reassignment ? LeadActivity.Kind.REASSIGNED : LeadActivity.Kind.ASSIGNED,
        "Asignado manualmente a " + advisorName + "." + (exceptionReason != null ? " Excepción: " + exceptionReason : ""),
        actorId,
        actorName);
    notifyAdvisor(lead, advisorId, advisorName);
  }

  private void notifyAdvisor(Lead lead, UUID advisorId, String advisorName) {
    String whatsapp = profiles.findById(advisorId).map(AdvisorProfile::getWhatsappPhone).orElse(null);
    if (whatsapp == null) {
      lead.markAdvisorNotice(SyncStatus.NOT_REQUIRED);
    }
    events.publishEvent(new LeadAssigned(lead.getId(), advisorId, advisorName, whatsapp, notice(lead)));
  }

  /** Registra el prospecto en 21Online si la integración está activa. Nunca falla el flujo del lead. */
  void pushToOnline21(Lead lead, Long advisorOnline21Id) {
    if (!prospects.enabled()) {
      return;
    }
    var id =
        prospects.createProspect(
            new ProspectRequest(lead.getName(), lead.getPhone(), lead.getEmail(), lead.getPropertyOnline21Id(), advisorOnline21Id, "Genera " + lead.getSource().name(), lead.getSummary()));
    lead.markOnline21(id.isPresent() ? SyncStatus.DONE : SyncStatus.FAILED, id.orElse(null));
  }

  Long online21IdOf(UUID advisorUserId) {
    return advisorUserId == null ? null : profiles.findById(advisorUserId).map(AdvisorProfile::getOnline21UserId).orElse(null);
  }

  /** Aviso al asesor (texto plano, máx. ~1000 caracteres, sin enlaces al panel), como en Inova. */
  static String notice(Lead lead) {
    StringBuilder sb = new StringBuilder("Nuevo lead de CENTURY 21 Genera\n");
    sb.append("Cliente: ").append(lead.getName());
    if (lead.getPhone() != null) {
      sb.append(" · ").append(lead.getPhone());
    }
    sb.append("\nBusca: ").append(INTEREST_TEXT.getOrDefault(lead.getInterest(), "información"));
    if (lead.getPropertyTitle() != null) {
      sb.append("\nPropiedad: ").append(lead.getPropertyTitle());
      if (lead.getPropertyKey() != null) {
        sb.append(" (").append(lead.getPropertyKey()).append(")");
      }
    }
    if (lead.getSummary() != null) {
      sb.append("\nLo que dijo: ").append(lead.getSummary());
    }
    String text = sb.toString();
    return text.length() > 1000 ? text.substring(0, 999) + "…" : text;
  }
}
