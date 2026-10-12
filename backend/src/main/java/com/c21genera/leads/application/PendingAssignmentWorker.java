package com.c21genera.leads.application;

import com.c21genera.leads.AssignmentStatus;
import com.c21genera.leads.application.AssignmentService.Decision;
import com.c21genera.leads.application.AvailabilityService.Snapshot;
import com.c21genera.leads.domain.Lead;
import com.c21genera.leads.infrastructure.LeadRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Procesa los leads en espera ("Pendiente por horario" y reservados para el
 * asesor de la propiedad) en cuanto empieza un turno elegible: por
 * antigüedad, con las mismas reglas, límites y estado de ronda que una
 * asignación nueva. Las asignaciones manuales ya no están pendientes, así que
 * nunca se tocan. Un lead ya asignado no cambia de asesor al terminar el turno.
 */
@Component
class PendingAssignmentWorker {

  private static final Logger log = LoggerFactory.getLogger(PendingAssignmentWorker.class);
  private static final int BATCH = 25;

  private final LeadRepository leads;
  private final AssignmentService assignment;
  private final LeadRecorder recorder;
  private final TransactionTemplate tx;

  PendingAssignmentWorker(LeadRepository leads, AssignmentService assignment, LeadRecorder recorder, TransactionTemplate tx) {
    this.leads = leads;
    this.assignment = assignment;
    this.recorder = recorder;
    this.tx = tx;
  }

  @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT45S")
  void tick() {
    try {
      Integer assigned = tx.execute(status -> processBatch());
      if (assigned != null && assigned > 0) {
        log.info("Pendientes asignados al iniciar turno: {}", assigned);
      }
    } catch (RuntimeException e) {
      log.warn("No se pudieron procesar los leads pendientes ({})", e.getClass().getSimpleName());
    }
  }

  /** Una pasada; devuelve cuántos quedaron asignados. Visible para pruebas. */
  int processBatch() {
    // Mismo orden de bloqueo que una asignación nueva: primero perfiles, luego leads.
    Snapshot snapshot = assignment.lockedSnapshot();
    List<Lead> pending = leads.lockPending(BATCH);
    int assigned = 0;
    for (Lead lead : pending) {
      if (!lead.getStage().isOpen()) {
        lead.markUnassigned("Se cerró antes de asignarse");
        continue;
      }
      Decision decision = assignment.decidePending(lead, snapshot);
      recorder.apply(lead, decision, null, null);
      if (decision.status() == AssignmentStatus.ASSIGNED) {
        assigned++;
        // El siguiente pendiente ve el conteo actualizado (límite diario).
        snapshot.today().merge(decision.advisorId(), 1, Integer::sum);
      }
    }
    return assigned;
  }
}
