package com.c21genera.closing.application;

import com.c21genera.closing.domain.ClosingCase;
import com.c21genera.closing.domain.ClosingNote;
import com.c21genera.closing.domain.ClosingStatus;
import com.c21genera.closing.domain.ClosingTask;
import com.c21genera.closing.infrastructure.ClosingCaseRepository;
import com.c21genera.closing.infrastructure.ClosingNoteRepository;
import com.c21genera.closing.infrastructure.ClosingTaskRepository;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.events.ContractEvents.ContractDelivered;
import com.c21genera.shared.events.ContractEvents.ContractFullySigned;
import com.c21genera.shared.events.ExpedienteEvents.PropertyAccepted;
import com.c21genera.shared.events.ExpedienteEvents.PropertyRejected;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seguimiento posterior a la firma del contrato (retroalimentación 25/09,
 * hallazgo 17). Al quedar firmado por todas las partes se abre el caso con
 * sus tareas internas; las que dependen de un evento del sistema se
 * completan solas (el contrato firmado queda resguardado, la entrega al
 * cliente, la decisión del inmueble) y el resto las marca el staff.
 */
@Service
@Transactional
public class ClosingService {

  public static final String TASK_ARCHIVE = "ARCHIVE_SIGNED_CONTRACT";
  public static final String TASK_DELIVER = "DELIVER_SIGNED_COPY";
  public static final String TASK_REVOCATION = "REVOCATION_WINDOW";
  public static final String TASK_DECISION = "PROPERTY_DECISION";
  public static final String TASK_PROMOTION = "START_PROMOTION";

  private final ClosingCaseRepository closingCaseRepository;
  private final ClosingNoteRepository closingNoteRepository;
  private final ClosingTaskRepository taskRepository;
  private final Clock clock;

  public ClosingService(
      ClosingCaseRepository closingCaseRepository,
      ClosingNoteRepository closingNoteRepository,
      ClosingTaskRepository taskRepository,
      Clock clock) {
    this.closingCaseRepository = closingCaseRepository;
    this.closingNoteRepository = closingNoteRepository;
    this.taskRepository = taskRepository;
    this.clock = clock;
  }

  @ApplicationModuleListener
  void on(ContractFullySigned event) {
    ClosingCase closingCase = openCase(event.expedienteId());
    closingCase.linkSignedContract(event.contractId());
    LocalDate signedOn = LocalDate.now(clock.withZone(ZoneId.of("America/Mexico_City")));
    addTask(closingCase, TASK_ARCHIVE, "Resguardar el contrato firmado y su constancia de firmas en el expediente", null);
    addTask(
        closingCase,
        TASK_DELIVER,
        "Entregar al cliente un tanto del contrato firmado y sus anexos (obligación del propio contrato)",
        signedOn.plusDays(3));
    addTask(
        closingCase,
        TASK_REVOCATION,
        "Respetar el plazo de revocación de 5 días hábiles del cliente antes de comprometer gastos de promoción",
        plusBusinessDays(signedOn, 5));
    addTask(closingCase, TASK_DECISION, "Decidir sobre el inmueble (aceptarlo o rechazarlo)", null);
    addTask(closingCase, TASK_PROMOTION, "Iniciar la promoción del inmueble en los medios autorizados por el cliente", null);
    // El propio sistema ya guardó el contrato firmado + constancia.
    complete(closingCase, TASK_ARCHIVE, null, "El sistema guardó el contrato firmado con su constancia de firmas.");
    closingCase.changeStatus(ClosingStatus.IN_PROGRESS);
  }

  @ApplicationModuleListener
  void on(ContractDelivered event) {
    closingCaseRepository
        .findByExpedienteId(event.expedienteId())
        .ifPresent(
            c -> {
              c.markContractDelivered(clock.instant());
              complete(c, TASK_DELIVER, event.actor() == null ? null : event.actor().userId(), "Entregado (" + event.method() + ")");
            });
  }

  @ApplicationModuleListener
  void on(PropertyAccepted event) {
    ClosingCase closingCase = openCase(event.expedienteId());
    complete(closingCase, TASK_DECISION, event.decidedByUserId(), "Inmueble aceptado");
  }

  @ApplicationModuleListener
  void on(PropertyRejected event) {
    closingCaseRepository
        .findByExpedienteId(event.expedienteId())
        .ifPresent(c -> complete(c, TASK_DECISION, event.decidedByUserId(), "Inmueble rechazado: " + event.reason()));
  }

  @Transactional(readOnly = true)
  public ClosingCase get(UUID expedienteId) {
    return closingCaseRepository
        .findByExpedienteId(expedienteId)
        .orElseThrow(() -> new NotFoundException("El seguimiento posterior a la firma todavía no inicia para este expediente."));
  }

  @Transactional(readOnly = true)
  public List<ClosingTask> tasksOf(UUID expedienteId) {
    return taskRepository.findByClosingCaseIdOrderByCreatedAtAsc(get(expedienteId).getId());
  }

  @Transactional(readOnly = true)
  public List<ClosingNote> notesOf(UUID expedienteId) {
    ClosingCase closingCase = get(expedienteId);
    return closingNoteRepository.findByClosingCaseIdOrderByCreatedAtDesc(closingCase.getId());
  }

  public ClosingTask completeTask(UUID expedienteId, UUID taskId, UUID userId, String note) {
    ClosingCase closingCase = get(expedienteId);
    ClosingTask task =
        taskRepository
            .findById(taskId)
            .filter(t -> t.getClosingCaseId().equals(closingCase.getId()))
            .orElseThrow(() -> new NotFoundException("Tarea", taskId));
    task.complete(clock.instant(), userId, note);
    refreshStatus(closingCase);
    return task;
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

  private ClosingCase openCase(UUID expedienteId) {
    return closingCaseRepository.findByExpedienteId(expedienteId).orElseGet(() -> closingCaseRepository.save(new ClosingCase(expedienteId)));
  }

  private void addTask(ClosingCase closingCase, String code, String title, LocalDate dueDate) {
    if (taskRepository.findByClosingCaseIdAndCode(closingCase.getId(), code).isEmpty()) {
      taskRepository.save(new ClosingTask(closingCase.getId(), code, title, dueDate, clock.instant()));
    }
  }

  private void complete(ClosingCase closingCase, String code, UUID userId, String note) {
    taskRepository.flush();
    taskRepository
        .findByClosingCaseIdAndCode(closingCase.getId(), code)
        .ifPresent(t -> t.complete(clock.instant(), userId, note));
    refreshStatus(closingCase);
  }

  private void refreshStatus(ClosingCase closingCase) {
    List<ClosingTask> tasks = taskRepository.findByClosingCaseIdOrderByCreatedAtAsc(closingCase.getId());
    if (!tasks.isEmpty() && tasks.stream().allMatch(ClosingTask::isDone)) {
      closingCase.changeStatus(ClosingStatus.COMPLETED);
    }
  }

  static LocalDate plusBusinessDays(LocalDate from, int days) {
    LocalDate date = from;
    int added = 0;
    while (added < days) {
      date = date.plusDays(1);
      if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
        added++;
      }
    }
    return date;
  }
}
