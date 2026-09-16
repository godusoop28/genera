package com.c21genera.closing.web;

import com.c21genera.closing.domain.ClosingCase;
import com.c21genera.closing.domain.ClosingNote;
import com.c21genera.closing.domain.ClosingStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class ClosingDtos {

  private ClosingDtos() {}

  public record ClosingCaseResponse(
      UUID id, UUID expedienteId, String status, boolean contractDelivered, Instant contractDeliveredAt) {

    static ClosingCaseResponse from(ClosingCase c) {
      return new ClosingCaseResponse(c.getId(), c.getExpedienteId(), c.getStatus().name(), c.isContractDelivered(), c.getContractDeliveredAt());
    }
  }

  public record NoteResponse(UUID id, UUID authorUserId, String note, Instant createdAt) {

    static NoteResponse from(ClosingNote n) {
      return new NoteResponse(n.getId(), n.getAuthorUserId(), n.getNote(), n.getCreatedAt());
    }
  }

  public record ChangeStatusRequest(@NotNull ClosingStatus status) {}

  public record AddNoteRequest(@NotBlank String note) {}
}
