package com.c21genera.leads.application;

import com.c21genera.leads.AssignmentMethod;
import com.c21genera.leads.LeadInterest;
import com.c21genera.leads.domain.AdvisorProfile;
import com.c21genera.leads.domain.Lead;
import com.c21genera.leads.domain.LeadActivity;
import com.c21genera.leads.domain.SyncStatus;
import com.c21genera.leads.infrastructure.AdvisorProfileRepository;
import com.c21genera.leads.infrastructure.LeadActivityRepository;
import com.c21genera.online21.ProspectGateway;
import com.c21genera.online21.ProspectGateway.ProspectRequest;
import com.c21genera.shared.events.LeadEvents.LeadAssigned;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Escrituras comunes del lead: actividades, asignación (con su aviso) y registro en 21Online. */
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
  private final AdvisorProfileRepository profiles;
  private final ProspectGateway prospects;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  LeadRecorder(LeadActivityRepository activities, AdvisorProfileRepository profiles, ProspectGateway prospects, ApplicationEventPublisher events, Clock clock) {
    this.activities = activities;
    this.profiles = profiles;
    this.prospects = prospects;
    this.events = events;
    this.clock = clock;
  }

  void activity(Lead lead, LeadActivity.Kind kind, String text, UUID actorId, String actorName) {
    activities.save(new LeadActivity(lead.getId(), kind, text, actorId, actorName, clock.instant()));
  }

  /** Asigna, deja constancia y publica el evento para que el asistente avise al asesor por WhatsApp. */
  void assign(Lead lead, UUID advisorId, String advisorName, AssignmentMethod method, boolean reassignment, UUID actorId, String actorName) {
    lead.assignTo(advisorId, method, clock.instant());
    String how =
        switch (method) {
          case PROPERTY_OWNER -> " por ser el asesor de la propiedad en 21Online";
          case ROULETTE -> " por la ruleta de asignación";
          case MANUAL -> " manualmente";
        };
    activity(lead, reassignment ? LeadActivity.Kind.REASSIGNED : LeadActivity.Kind.ASSIGNED, "Asignado a " + advisorName + how + ".", actorId, actorName);
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
