package com.c21genera.closing.web;

import com.c21genera.closing.domain.ClosingCase;
import com.c21genera.closing.domain.ClosingNote;
import com.c21genera.closing.domain.ClosingStatus;
import com.c21genera.closing.domain.ClosingTask;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ClosingDtos {

  private ClosingDtos() {}

  public record ClosingCaseResponse(
      UUID id,
      UUID expedienteId,
      String status,
      boolean contractDelivered,
      Instant contractDeliveredAt,
      UUID signedContractId,
      // CC#32: fecha de cierre (null mientras no se completa).
      LocalDate closedOn) {

    static ClosingCaseResponse from(ClosingCase c) {
      return new ClosingCaseResponse(
          c.getId(), c.getExpedienteId(), c.getStatus().name(), c.isContractDelivered(), c.getContractDeliveredAt(), c.getSignedContractId(), c.getClosedOn());
    }
  }

  public record TaskResponse(
      UUID id, String code, String title, LocalDate dueDate, boolean done, Instant doneAt, UUID doneByUserId, String doneNote) {

    static TaskResponse from(ClosingTask t) {
      return new TaskResponse(t.getId(), t.getCode(), t.getTitle(), t.getDueDate(), t.isDone(), t.getDoneAt(), t.getDoneByUserId(), t.getDoneNote());
    }
  }

  public record NoteResponse(UUID id, UUID authorUserId, String note, Instant createdAt) {

    static NoteResponse from(ClosingNote n) {
      return new NoteResponse(n.getId(), n.getAuthorUserId(), n.getNote(), n.getCreatedAt());
    }
  }

  /** closedOn (CC#32, opcional): fecha de cierre al completarlo; si no llega, hoy. */
  public record ChangeStatusRequest(@NotNull ClosingStatus status, LocalDate closedOn) {}

  public record AddNoteRequest(@NotBlank String note) {}

  public record CompleteTaskRequest(String note) {}
}
