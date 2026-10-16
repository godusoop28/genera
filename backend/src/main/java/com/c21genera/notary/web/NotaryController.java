package com.c21genera.notary.web;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.identity.CurrentUser;
import com.c21genera.notary.application.NotaryService;
import com.c21genera.notary.application.NotaryService.DocumentStageView;
import com.c21genera.notary.application.NotaryService.SelectedDocument;
import com.c21genera.notary.application.NotaryService.SendCommand;
import com.c21genera.notary.domain.ClosingDocumentCheck;
import com.c21genera.notary.domain.NotaryStage;
import com.c21genera.notary.domain.NotarySubmission;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.security.ExpedienteAccessPolicy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Control de Cambios #23: documentos por etapa y envíos a notaría (inicial y de cierre). */
@RestController
@PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
public class NotaryController {

  private final NotaryService service;
  private final DocumentsApi documentsApi;
  private final ExpedienteAccessPolicy accessPolicy;

  public NotaryController(NotaryService service, DocumentsApi documentsApi, ExpedienteAccessPolicy accessPolicy) {
    this.service = service;
    this.documentsApi = documentsApi;
    this.accessPolicy = accessPolicy;
  }

  public record VersionResponse(UUID id, int versionNumber, Instant uploadedAt, boolean current, boolean hasPdf) {}

  public record StageDocumentResponse(
      UUID documentId,
      String type,
      String name,
      UUID participantId,
      boolean required,
      String status,
      boolean exception,
      boolean markedPending,
      List<VersionResponse> versions,
      boolean closingRequired,
      boolean closingValid,
      Boolean closingCheckValid,
      Integer closingCheckedVersion,
      String closingCheckNote,
      String closingCheckedByName,
      Instant closingCheckedAt) {

    static StageDocumentResponse from(DocumentStageView v) {
      var d = v.document();
      ClosingDocumentCheck check = v.closingCheck().orElse(null);
      Integer checkedVersion =
          check == null
              ? null
              : v.versions().stream().filter(x -> x.id().equals(check.getDocumentVersionId())).map(x -> x.versionNumber()).findFirst().orElse(null);
      return new StageDocumentResponse(
          d.documentId(),
          d.type().name(),
          d.displayName(),
          d.participantId(),
          d.required(),
          d.status(),
          d.exception() && !d.markedPending(),
          d.markedPending(),
          v.versions().stream()
              .map(x -> new VersionResponse(x.id(), x.versionNumber(), x.uploadedAt(), x.id().equals(d.currentVersionId()), x.pdfStorageKey() != null))
              .toList(),
          v.closingRequired(),
          v.closingValid(),
          check == null ? null : check.isValid(),
          checkedVersion,
          check == null ? null : check.getNote(),
          check == null ? null : check.getCheckedByName(),
          check == null ? null : check.getCheckedAt());
    }
  }

  public record SubmissionItemResponse(UUID documentId, String documentName, UUID versionId, int versionNumber) {}

  public record SubmissionResponse(
      UUID id, String kind, Instant sentAt, String sentByName, String recipientEmail, String note, String warnings, List<SubmissionItemResponse> items) {

    static SubmissionResponse from(NotarySubmission s) {
      return new SubmissionResponse(
          s.getId(),
          s.getKind().name(),
          s.getSentAt(),
          s.getSentByName(),
          s.getRecipientEmail(),
          s.getNote(),
          s.getWarnings(),
          s.getItems().stream()
              .map(i -> new SubmissionItemResponse(i.getDocumentId(), i.getDocumentName(), i.getDocumentVersionId(), i.getVersionNumber()))
              .toList());
    }
  }

  public record OverviewResponse(
      boolean closingActive,
      Instant offerAcceptedAt,
      String offerAcceptedByName,
      String offerNote,
      List<StageDocumentResponse> documents,
      List<SubmissionResponse> submissions) {}

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/notary")
  public OverviewResponse overview(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    requireAccess(expedienteId, jwt);
    NotaryService.Overview o = service.overview(expedienteId);
    NotaryStage stage = o.stage();
    return new OverviewResponse(
        stage.isClosingActive(),
        stage.getOfferAcceptedAt(),
        stage.getOfferAcceptedByName(),
        stage.getOfferNote(),
        o.documents().stream().map(StageDocumentResponse::from).toList(),
        o.submissions().stream().map(SubmissionResponse::from).toList());
  }

  public record OfferRequest(String note) {}

  @PostMapping("/api/v1/internal/expedientes/{expedienteId}/notary/offer-accepted")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_EDIT', 'DOCUMENT_EMAIL_SEND')")
  public OverviewResponse registerOffer(
      @PathVariable UUID expedienteId, @RequestBody(required = false) OfferRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(expedienteId, jwt);
    service.registerOfferAccepted(expedienteId, request == null ? null : request.note(), user.toActor());
    return overview(expedienteId, jwt);
  }

  @DeleteMapping("/api/v1/internal/expedientes/{expedienteId}/notary/offer-accepted")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_EDIT', 'DOCUMENT_EMAIL_SEND')")
  public OverviewResponse clearOffer(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(expedienteId, jwt);
    service.clearOfferAccepted(expedienteId, user.toActor());
    return overview(expedienteId, jwt);
  }

  public record ClosingCheckRequest(boolean valid, String note) {}

  /** El documento de cierre (su versión vigente) está vigente y al corriente de pago. */
  @PutMapping("/api/v1/internal/documents/{documentId}/closing-check")
  @PreAuthorize("hasAnyAuthority('DOCUMENT_UPLOAD', 'DOCUMENT_ACCEPT')")
  public void checkClosing(@PathVariable UUID documentId, @RequestBody ClosingCheckRequest request, @AuthenticationPrincipal Jwt jwt) {
    UUID expedienteId = documentsApi.expedienteIdOfDocument(documentId).orElseThrow(() -> new NotFoundException("Documento", documentId));
    CurrentUser user = requireAccess(expedienteId, jwt);
    service.checkClosingDocument(documentId, request != null && request.valid(), request == null ? null : request.note(), user.toActor());
  }

  public record SelectedDocumentRequest(UUID documentId, UUID versionId) {}

  public record SendRequest(String kind, List<SelectedDocumentRequest> documents, String recipientEmail, String note, Boolean confirmWarnings) {}

  @PostMapping("/api/v1/internal/expedientes/{expedienteId}/notary/submissions")
  @PreAuthorize("hasAuthority('DOCUMENT_EMAIL_SEND')")
  @ResponseStatus(HttpStatus.CREATED)
  public SubmissionResponse send(@PathVariable UUID expedienteId, @RequestBody SendRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(expedienteId, jwt);
    NotarySubmission.Kind kind;
    try {
      kind = request == null || request.kind() == null ? null : NotarySubmission.Kind.valueOf(request.kind());
    } catch (IllegalArgumentException e) {
      kind = null;
    }
    List<SelectedDocument> documents =
        request == null || request.documents() == null
            ? List.of()
            : request.documents().stream().map(d -> new SelectedDocument(d.documentId(), d.versionId())).toList();
    SendCommand command =
        new SendCommand(
            kind,
            documents,
            request == null ? null : request.recipientEmail(),
            request == null ? null : request.note(),
            request != null && Boolean.TRUE.equals(request.confirmWarnings()));
    return SubmissionResponse.from(service.send(expedienteId, command, user.toActor()));
  }

  private CurrentUser requireAccess(UUID expedienteId, Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    accessPolicy.requireAccess(expedienteId, user.id(), user.permissions());
    return user;
  }
}
