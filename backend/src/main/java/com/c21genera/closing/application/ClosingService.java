package com.c21genera.closing.application;

import com.c21genera.closing.domain.ClosingCase;
import com.c21genera.closing.domain.ClosingNote;
import com.c21genera.closing.domain.ClosingStatus;
import com.c21genera.closing.infrastructure.ClosingCaseRepository;
import com.c21genera.closing.infrastructure.ClosingNoteRepository;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.events.ExpedienteEvents.PropertyAccepted;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seguimiento de cierre (ver AGENTS §50): arranca cuando el inmueble es
 * aceptado. Deliberadamente genérico: no inventa documentos legalmente
 * requeridos que no estén especificados para el Módulo 1.
 */
@Service
@Transactional
public class ClosingService {

  private final ClosingCaseRepository closingCaseRepository;
  private final ClosingNoteRepository closingNoteRepository;
  private final Clock clock;

  public ClosingService(ClosingCaseRepository closingCaseRepository, ClosingNoteRepository closingNoteRepository, Clock clock) {
    this.closingCaseRepository = closingCaseRepository;
    this.closingNoteRepository = closingNoteRepository;
    this.clock = clock;
  }

  @ApplicationModuleListener
  void on(PropertyAccepted event) {
    if (!closingCaseRepository.existsByExpedienteId(event.expedienteId())) {
      closingCaseRepository.save(new ClosingCase(event.expedienteId()));
    }
  }

  @Transactional(readOnly = true)
  public ClosingCase get(UUID expedienteId) {
    return closingCaseRepository
        .findByExpedienteId(expedienteId)
        .orElseThrow(() -> new NotFoundException("Caso de cierre para el expediente", expedienteId));
  }

  @Transactional(readOnly = true)
  public List<ClosingNote> notesOf(UUID expedienteId) {
    ClosingCase closingCase = get(expedienteId);
    return closingNoteRepository.findByClosingCaseIdOrderByCreatedAtDesc(closingCase.getId());
  }

  public ClosingCase changeStatus(UUID expedienteId, ClosingStatus status) {
    ClosingCase closingCase = get(expedienteId);
    closingCase.changeStatus(status);
    return closingCase;
  }

  public ClosingNote addNote(UUID expedienteId, UUID authorUserId, String note) {
    ClosingCase closingCase = get(expedienteId);
    ClosingNote created = new ClosingNote(closingCase.getId(), authorUserId, note, clock.instant());
    closingNoteRepository.save(created);
    return created;
  }

  public ClosingCase markContractDelivered(UUID expedienteId) {
    ClosingCase closingCase = get(expedienteId);
    closingCase.markContractDelivered(clock.instant());
    return closingCase;
  }
}
