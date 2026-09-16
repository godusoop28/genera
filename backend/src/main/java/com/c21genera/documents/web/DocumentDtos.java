package com.c21genera.documents.web;

import com.c21genera.documents.DocumentStatus;
import com.c21genera.documents.ProcessingStatus;
import com.c21genera.documents.domain.Document;
import com.c21genera.documents.domain.DocumentVersion;
import com.c21genera.documents.domain.ReturnReasonCode;
import com.c21genera.documents.domain.ReviewDecision;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class DocumentDtos {

  private DocumentDtos() {}

  public record DocumentResponse(
      UUID id, UUID expedienteId, String requirementCode, String type, UUID participantId, boolean required,
      DocumentStatus status, int currentVersionNumber) {

    public static DocumentResponse from(Document d) {
      return new DocumentResponse(
          d.getId(), d.getExpedienteId(), d.getRequirementCode(), d.getType().name(), d.getParticipantId(),
          d.isRequired(), d.getStatus(), d.getCurrentVersionNumber());
    }
  }

  public record DocumentVersionResponse(
      UUID id, UUID documentId, int versionNumber, Instant uploadedAt, String uploadedVia, ProcessingStatus processingStatus) {

    public static DocumentVersionResponse from(DocumentVersion v) {
      return new DocumentVersionResponse(
          v.getId(), v.getDocumentId(), v.getVersionNumber(), v.getUploadedAt(), v.getUploadedVia().name(), v.getProcessingStatus());
    }
  }

  public record ReviewRequest(@NotNull ReviewDecision decision, ReturnReasonCode reasonCode, String comment) {}
}
