package com.c21genera.leads.application;

import com.c21genera.leads.domain.Lead;
import com.c21genera.leads.domain.LeadTask;
import com.c21genera.leads.infrastructure.LeadRepository;
import com.c21genera.leads.infrastructure.LeadTaskRepository;
import com.c21genera.notifications.StaffAlerts;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Control de Cambios #30: recordatorio al asesor antes de cada actividad
 * programada, aviso de "actividad pendiente" cuando vence sin resultado y
 * alerta diaria a dirección de los leads sin actividad reciente. Los avisos
 * llegan a la campana del sistema (y al correo si así se configuró).
 */
@Component
class LeadFollowUpWorker {

  private static final Logger log = LoggerFactory.getLogger(LeadFollowUpWorker.class);
  /** Margen después del fin de la actividad antes de avisar que quedó sin resultado. */
  private static final Duration OVERDUE_GRACE = Duration.ofMinutes(30);
  private static final Set<String> DIRECTION = Set.of("ADMINISTRATOR", "DIRECTOR");

  private final LeadTaskRepository tasks;
  private final LeadRepository leads;
  private final AdvisorRoutingService routing;
  private final StaffAlerts alerts;
  private final TransactionTemplate tx;
  private final Clock clock;

  LeadFollowUpWorker(LeadTaskRepository tasks, LeadRepository leads, AdvisorRoutingService routing, StaffAlerts alerts, TransactionTemplate tx, Clock clock) {
    this.tasks = tasks;
    this.leads = leads;
    this.routing = routing;
    this.alerts = alerts;
    this.tx = tx;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT70S")
  void tick() {
    try {
      tx.executeWithoutResult(status -> {
        boolean email = routing.reminderEmail();
        sendReminders(email);
        flagOverdue(email);
      });
    } catch (RuntimeException e) {
      log.warn("No se pudieron enviar los recordatorios de actividades ({})", e.getClass().getSimpleName());
    }
  }

  @Scheduled(cron = "0 0 9 * * *", zone = "America/Mexico_City")
  void dailyStaleAlert() {
    try {
      tx.executeWithoutResult(status -> staleAlert());
    } catch (RuntimeException e) {
      log.warn("No se pudo generar la alerta de leads sin actividad ({})", e.getClass().getSimpleName());
    }
  }

  /** Visible para pruebas. */
  int sendReminders(boolean email) {
    Instant now = clock.instant();
    int sent = 0;
    for (LeadTask t : tasks.remindersDue(now, now.plus(Duration.ofDays(1)).plusSeconds(60))) {
      if (t.getScheduledAt().minus(Duration.ofMinutes(t.getReminderMinutes())).isAfter(now)) {
        continue;
      }
      Lead lead = leads.findById(t.getLeadId()).orElse(null);
      if (lead != null) {
        String what = LeadTaskService.typeLabel(t.getType()) + (t.getTitle() != null ? " \"" + t.getTitle() + "\"" : "");
        alerts.notifyUser(
            recipient(t, lead),
            "Recordatorio de actividad",
            what + " con " + lead.getName() + " — " + LeadTaskService.when(t.getScheduledAt()),
            body(t, lead),
            "/leads/" + lead.getId(),
            "task-reminder:" + t.getId() + ":" + t.getScheduledAt().getEpochSecond(),
            email);
        sent++;
      }
      t.markReminderSent(now);
    }
    return sent;
  }

  int flagOverdue(boolean email) {
    Instant now = clock.instant();
    int flagged = 0;
    for (LeadTask t : tasks.overdueCandidates(now.minus(OVERDUE_GRACE))) {
      if (!t.isOverdue(now.minus(OVERDUE_GRACE))) {
        continue;
      }
      Lead lead = leads.findById(t.getLeadId()).orElse(null);
      if (lead != null && lead.getStage().isOpen()) {
        alerts.notifyUser(
            recipient(t, lead),
            "Actividad pendiente",
            "Registra el resultado: " + LeadTaskService.typeLabel(t.getType()).toLowerCase(Locale.ROOT) + " con " + lead.getName() + " (" + LeadTaskService.when(t.getScheduledAt()) + ")",
            body(t, lead),
            "/leads/" + lead.getId(),
            "task-overdue:" + t.getId() + ":" + t.getScheduledAt().getEpochSecond(),
            email);
        flagged++;
      }
      t.markOverdueNotified(now);
    }
    return flagged;
  }

  int staleAlert() {
    Instant now = clock.instant();
    int days = routing.staleDays();
    Instant before = now.minus(Duration.ofDays(days));
    List<Lead> stale = leads.findAll().stream().filter(l -> l.getStage().isOpen() && l.getLastActivityAt().isBefore(before)).toList();
    if (stale.isEmpty()) {
      return 0;
    }
    long advanced = stale.stream().filter(l -> l.getStage().isAdvanced()).count();
    String title = stale.size() + (stale.size() == 1 ? " lead lleva" : " leads llevan") + " más de " + days + " días sin actividad";
    StringBuilder body = new StringBuilder(title).append('.');
    if (advanced > 0) {
      body.append(' ').append(advanced).append(" en negociación u oferta aceptada (riesgo de enfriarse).");
    }
    stale.stream().limit(10).forEach(l -> body.append("\n• ").append(l.getName()).append(" — ").append(LeadService.stageLabel(l.getStage())));
    alerts.notifyRoles(DIRECTION, "Leads sin actividad", title, body.toString(), "/leads?grupo=STALE", "stale-leads:" + LocalDate.ofInstant(now, StaffNames.MEXICO_CITY), false);
    return stale.size();
  }

  private static java.util.UUID recipient(LeadTask t, Lead lead) {
    return t.getAdvisorUserId() != null ? t.getAdvisorUserId() : lead.getAdvisorUserId();
  }

  private static String body(LeadTask t, Lead lead) {
    StringBuilder b = new StringBuilder();
    b.append(LeadTaskService.typeLabel(t.getType())).append(" con ").append(lead.getName());
    if (lead.getPhone() != null) {
      b.append(" (").append(lead.getPhone()).append(')');
    }
    b.append("\nCuándo: ").append(LeadTaskService.when(t.getScheduledAt()));
    if (t.getPropertyTitle() != null || t.getPropertyKey() != null) {
      b.append("\nInmueble: ").append(t.getPropertyTitle() != null ? t.getPropertyTitle() : t.getPropertyKey());
    }
    if (t.getNotesBefore() != null) {
      b.append("\nNotas: ").append(t.getNotesBefore());
    }
    return b.toString();
  }
}
