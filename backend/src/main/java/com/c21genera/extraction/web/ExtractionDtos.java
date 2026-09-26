package com.c21genera.extraction.web;

import com.c21genera.extraction.domain.DataConflict;
import com.c21genera.extraction.domain.ExtractedFieldObservation;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public final class ExtractionDtos {

  private ExtractionDtos() {}

  public record ObservationResponse(
      UUID id,
      UUID documentId,
      UUID documentVersionId,
      String fieldName,
      String detectedValue,
      String confirmedValue,
      String origin,
      Double confidence,
      Instant updatedAt) {

    public static ObservationResponse from(ExtractedFieldObservation o) {
      return new ObservationResponse(
          o.getId(),
          o.getDocumentId(),
          o.getDocumentVersionId(),
          o.getFieldName(),
          o.getDetectedValue(),
          o.getConfirmedValue(),
          o.getOrigin().name(),
          o.getConfidence(),
          o.getUpdatedAt());
    }
  }

  public record ConfirmFieldRequest(@NotBlank String confirmedValue) {}

  public record ConflictResponse(
      UUID id,
      String fieldName,
      String description,
      boolean resolved,
      boolean resolvedAutomatically,
      String resolutionNote,
      UUID resolvedByUserId,
      Instant detectedAt,
      Instant resolvedAt) {

    public static ConflictResponse from(DataConflict c) {
      return new ConflictResponse(
          c.getId(),
          c.getFieldName(),
          c.getDescription(),
          c.isResolved(),
          c.isResolvedAutomatically(),
          c.getResolutionNote(),
          c.getResolvedByUserId(),
          c.getDetectedAt(),
          c.getResolvedAt());
    }
  }

  /** Resolver una diferencia exige explicar por qué es aceptable (p. ej. "el predial está a nombre del copropietario"). */
  public record ResolveConflictRequest(@NotBlank String note) {}
}
