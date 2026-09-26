package com.c21genera.documents.web;

import com.c21genera.documents.DocumentStatus;
import com.c21genera.documents.ProcessingStatus;
import com.c21genera.documents.domain.Document;
import com.c21genera.documents.domain.DocumentReview;
import com.c21genera.documents.domain.DocumentVersion;
import com.c21genera.documents.domain.ReturnReasonCode;
import com.c21genera.shared.domain.ReviewDecision;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DocumentDtos {

  private DocumentDtos() {}

  public record LatestVersionSummary(
      UUID id,
      int versionNumber,
      Instant uploadedAt,
      String uploadedVia,
      String uploadedByName,
      ProcessingStatus processingStatus,
      String processingError,
      Boolean aiTypeMatches,
      Boolean aiLegible,
      String aiDetectedKind,
      String aiObservations,
      Instant aiAssessedAt,
      List<String> blockingIssues) {

    static LatestVersionSummary from(DocumentVersion v) {
      if (v == null) {
        return null;
      }
      return new LatestVersionSummary(
          v.getId(),
          v.getVersionNumber(),
          v.getUploadedAt(),
          v.getUploadedVia().name(),
          v.getUploadedByName(),
          v.getProcessingStatus(),
          v.getProcessingError(),
          v.getAiTypeMatches(),
          v.getAiLegible(),
          v.getAiDetectedKind(),
          v.getAiObservations(),
          v.getAiAssessedAt(),
          v.blockingIssues());
    }
  }

  public record DocumentResponse(
      UUID id,
      UUID expedienteId,
      String requirementCode,
      String type,
      UUID participantId,
      boolean required,
      DocumentStatus status,
      int currentVersionNumber,
      String notApplicableJustification,
      Instant notApplicableAt,
      ReviewDecision lastReviewDecision,
      ReturnReasonCode lastReviewReasonCode,
      String lastReviewComment,
      Instant lastReviewedAt,
      LatestVersionSummary latestVersion) {

    public static DocumentResponse from(Document d, DocumentVersion latest) {
      return new DocumentResponse(
          d.getId(),
          d.getExpedienteId(),
          d.getRequirementCode(),
          d.getType().name(),
          d.getParticipantId(),
          d.isRequired(),
          d.getStatus(),
          d.getCurrentVersionNumber(),
          d.getNotApplicableJustification(),
          d.getNotApplicableAt(),
          d.getLastReviewDecision(),
          d.getLastReviewReasonCode(),
          d.getLastReviewComment(),
          d.getLastReviewedAt(),
          LatestVersionSummary.from(latest));
    }
  }

  /**
   * Lo que ve el cliente en su liga: estado del documento y, si hay que
   * corregirlo, qué y por qué (motivo de devolución, foto que no pasó la
   * verificación de calidad o archivo que no parece ser el documento
   * solicitado). No incluye datos internos del staff.
   */
  public record PublicDocumentResponse(
      UUID id,
      String type,
      UUID participantId,
      boolean required,
      DocumentStatus status,
      int currentVersionNumber,
      ReturnReasonCode correctionReasonCode,
      String correctionComment,
      String qualityIssue,
      boolean looksLikeWrongDocument,
      boolean processing) {

    public static PublicDocumentResponse from(Document d, DocumentVersion latest) {
      boolean needsCorrection = d.getStatus() == DocumentStatus.RETURNED || d.getStatus() == DocumentStatus.REJECTED;
      boolean latestIsCurrent = latest != null && latest.getVersionNumber() == d.getCurrentVersionNumber();
      String qualityIssue =
          latestIsCurrent
                  && (latest.getProcessingStatus() == ProcessingStatus.QUALITY_FAILED
                      || latest.getProcessingStatus() == ProcessingStatus.FAILED)
                  && d.getStatus() != DocumentStatus.ACCEPTED
              ? latest.getProcessingError()
              : null;
      boolean wrongDocument =
          latestIsCurrent && Boolean.FALSE.equals(latest.getAiTypeMatches()) && d.getStatus() != DocumentStatus.ACCEPTED;
      boolean processing =
          latestIsCurrent
              && (latest.getProcessingStatus() == ProcessingStatus.QUEUED || latest.getProcessingStatus() == ProcessingStatus.PROCESSING);
      return new PublicDocumentResponse(
          d.getId(),
          d.getType().name(),
          d.getParticipantId(),
          d.isRequired(),
          d.getStatus(),
          d.getCurrentVersionNumber(),
          needsCorrection ? d.getLastReviewReasonCode() : null,
          needsCorrection ? d.getLastReviewComment() : null,
          qualityIssue,
          wrongDocument,
          processing);
    }
  }

  public record DocumentVersionResponse(
      UUID id,
      UUID documentId,
      int versionNumber,
      Instant uploadedAt,
      String uploadedVia,
      String uploadedByName,
      ProcessingStatus processingStatus,
      String processingError) {

    public static DocumentVersionResponse from(DocumentVersion v) {
      return new DocumentVersionResponse(
          v.getId(),
          v.getDocumentId(),
          v.getVersionNumber(),
          v.getUploadedAt(),
          v.getUploadedVia().name(),
          v.getUploadedByName(),
          v.getProcessingStatus(),
          v.getProcessingError());
    }
  }

  public record ReviewHistoryResponse(
      UUID id,
      UUID documentVersionId,
      ReviewDecision decision,
      ReturnReasonCode reasonCode,
      String comment,
      UUID reviewedBy,
      Instant reviewedAt,
      String overrideJustification,
      String overriddenIssues) {

    public static ReviewHistoryResponse from(DocumentReview r) {
      return new ReviewHistoryResponse(
          r.getId(),
          r.getDocumentVersionId(),
          r.getDecision(),
          r.getReasonCode(),
          r.getComment(),
          r.getReviewedBy(),
          r.getReviewedAt(),
          r.getOverrideJustification(),
          r.getOverriddenIssues());
    }
  }

  public record ReviewRequest(@NotNull ReturnReasonCode reasonCode, String comment) {}

  public record AcceptRequest(String overrideJustification) {}

  public record NotApplicableRequest(String justification) {}
}
