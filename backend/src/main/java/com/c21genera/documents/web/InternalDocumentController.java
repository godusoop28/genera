package com.c21genera.documents.web;

import com.c21genera.documents.application.DocumentService;
import com.c21genera.documents.application.DocumentService.ReviewCommand;
import com.c21genera.documents.application.DocumentService.UploadedFileContent;
import com.c21genera.documents.domain.Document;
import com.c21genera.documents.domain.DocumentVersion;
import com.c21genera.documents.domain.UploadedVia;
import com.c21genera.documents.web.DocumentDtos.AcceptRequest;
import com.c21genera.documents.web.DocumentDtos.DocumentResponse;
import com.c21genera.documents.web.DocumentDtos.DocumentVersionResponse;
import com.c21genera.documents.web.DocumentDtos.NotApplicableRequest;
import com.c21genera.documents.web.DocumentDtos.ReviewHistoryResponse;
import com.c21genera.documents.web.DocumentDtos.ReviewRequest;
import com.c21genera.identity.CurrentUser;
import com.c21genera.shared.config.StorageProperties;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.ReviewDecision;
import com.c21genera.shared.security.ExpedienteAccessPolicy;
import com.c21genera.shared.storage.FileStorage;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Todo endpoint resuelve primero el expediente al que pertenece el recurso
 * (documento o versión) y verifica que el usuario pueda verlo: un asesor
 * con EXPEDIENT_VIEW_OWN no puede consultar documentos de expedientes
 * ajenos aunque conozca el id.
 */
@RestController
@PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
public class InternalDocumentController {

  private final DocumentService documentService;
  private final FileStorage fileStorage;
  private final StorageProperties storageProperties;
  private final ExpedienteAccessPolicy accessPolicy;

  public InternalDocumentController(
      DocumentService documentService, FileStorage fileStorage, StorageProperties storageProperties, ExpedienteAccessPolicy accessPolicy) {
    this.documentService = documentService;
    this.fileStorage = fileStorage;
    this.storageProperties = storageProperties;
    this.accessPolicy = accessPolicy;
  }

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/documents")
  public List<DocumentResponse> listByExpediente(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    requireExpediente(expedienteId, jwt);
    return documentService.documentsOf(expedienteId).stream().map(this::toResponse).toList();
  }

  @GetMapping("/api/v1/internal/documents/{documentId}")
  public DocumentResponse get(@PathVariable UUID documentId, @AuthenticationPrincipal Jwt jwt) {
    return toResponse(requireDocument(documentId, jwt));
  }

  @GetMapping("/api/v1/internal/documents/{documentId}/versions")
  public List<DocumentVersionResponse> versions(@PathVariable UUID documentId, @AuthenticationPrincipal Jwt jwt) {
    requireDocument(documentId, jwt);
    return documentService.versionsOf(documentId).stream().map(DocumentVersionResponse::from).toList();
  }

  /** Historial de decisiones (incluye aceptaciones por excepción con su justificación). */
  @GetMapping("/api/v1/internal/documents/{documentId}/reviews")
  public List<ReviewHistoryResponse> reviews(@PathVariable UUID documentId, @AuthenticationPrincipal Jwt jwt) {
    requireDocument(documentId, jwt);
    return documentService.reviewsOf(documentId).stream().map(ReviewHistoryResponse::from).toList();
  }

  @PostMapping("/api/v1/internal/documents/{documentId}/versions")
  @PreAuthorize("hasAuthority('DOCUMENT_UPLOAD')")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public DocumentVersionResponse upload(
      @PathVariable UUID documentId, @RequestParam("files") List<MultipartFile> files, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    requireDocument(documentId, jwt);
    List<UploadedFileContent> contents = files.stream().map(InternalDocumentController::readFile).toList();
    return DocumentVersionResponse.from(documentService.uploadVersion(documentId, contents, UploadedVia.INTERNAL, user.toActor()));
  }

  private static UploadedFileContent readFile(MultipartFile file) {
    try {
      return new UploadedFileContent(file.getBytes(), file.getOriginalFilename());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Si la versión vigente tiene alertas (calidad, archivo ilegible o que no
   * corresponde a lo solicitado), solo se acepta con DOCUMENT_QUALITY_OVERRIDE
   * y una justificación.
   */
  @PostMapping("/api/v1/internal/documents/{documentId}/accept")
  @PreAuthorize("hasAuthority('DOCUMENT_ACCEPT')")
  public DocumentResponse accept(
      @PathVariable UUID documentId, @RequestBody(required = false) AcceptRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    requireDocument(documentId, jwt);
    Document document =
        documentService.review(
            new ReviewCommand(
                documentId,
                ReviewDecision.ACCEPTED,
                null,
                null,
                request == null ? null : request.overrideJustification(),
                user.hasPermission("DOCUMENT_QUALITY_OVERRIDE"),
                user.toActor()));
    return toResponse(document);
  }

  @PostMapping("/api/v1/internal/documents/{documentId}/return")
  @PreAuthorize("hasAuthority('DOCUMENT_RETURN')")
  public DocumentResponse returnDocument(
      @PathVariable UUID documentId, @Valid @RequestBody ReviewRequest request, @AuthenticationPrincipal Jwt jwt) {
    return toResponse(review(documentId, ReviewDecision.RETURNED, request, jwt));
  }

  @PostMapping("/api/v1/internal/documents/{documentId}/reject")
  @PreAuthorize("hasAuthority('DOCUMENT_REJECT')")
  public DocumentResponse reject(
      @PathVariable UUID documentId, @Valid @RequestBody ReviewRequest request, @AuthenticationPrincipal Jwt jwt) {
    return toResponse(review(documentId, ReviewDecision.REJECTED, request, jwt));
  }

  @PostMapping("/api/v1/internal/documents/{documentId}/not-applicable")
  @PreAuthorize("hasAuthority('DOCUMENT_MARK_NOT_APPLICABLE')")
  public DocumentResponse markNotApplicable(
      @PathVariable UUID documentId, @RequestBody NotApplicableRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    requireDocument(documentId, jwt);
    return toResponse(documentService.markNotApplicable(documentId, request.justification(), user.toActor()));
  }

  @PostMapping("/api/v1/internal/documents/{documentId}/request-again")
  @PreAuthorize("hasAuthority('DOCUMENT_MARK_NOT_APPLICABLE')")
  public DocumentResponse requestAgain(@PathVariable UUID documentId, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    requireDocument(documentId, jwt);
    return toResponse(documentService.requestAgain(documentId, user.toActor()));
  }

  @PostMapping("/api/v1/internal/expedientes/{expedienteId}/reception/sign")
  @PreAuthorize("hasAuthority('RECEPTION_SIGN')")
  public void signReception(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireExpediente(expedienteId, jwt);
    documentService.signReception(expedienteId, user.toActor());
  }

  @GetMapping("/api/v1/internal/document-versions/{versionId}/processing")
  public DocumentVersionResponse processingStatus(@PathVariable UUID versionId, @AuthenticationPrincipal Jwt jwt) {
    DocumentVersion version = documentService.getVersion(versionId);
    requireDocument(version.getDocumentId(), jwt);
    return DocumentVersionResponse.from(version);
  }

  public record DownloadResponse(URI url) {}

  /** Nunca se exponen URLs permanentes: siempre firmadas y temporales (ver AGENTS §29/§101). */
  @GetMapping("/api/v1/internal/document-versions/{versionId}/download")
  public DownloadResponse download(@PathVariable UUID versionId, @AuthenticationPrincipal Jwt jwt) {
    DocumentVersion version = documentService.getVersion(versionId);
    requireDocument(version.getDocumentId(), jwt);
    String key = version.getStorageKeyPdf() != null ? version.getStorageKeyPdf() : version.getStorageKeyNormalized();
    if (key == null) {
      // Sin PDF procesado (p. ej. no pasó la calidad): se descarga la primera página original para poder revisarla.
      key =
          documentService.pagesOf(versionId).stream()
              .findFirst()
              .map(p -> p.storageKeyOriginal())
              .orElseThrow(() -> new NotFoundException("El documento todavía no tiene un archivo disponible para descarga."));
    }
    return new DownloadResponse(fileStorage.generateTemporaryDownloadUrl(key, storageProperties.presignedUrlTtl()));
  }

  private Document review(UUID documentId, ReviewDecision decision, ReviewRequest request, Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    requireDocument(documentId, jwt);
    return documentService.review(
        new ReviewCommand(documentId, decision, request.reasonCode(), request.comment(), null, false, user.toActor()));
  }

  private DocumentResponse toResponse(Document document) {
    return DocumentResponse.from(document, documentService.latestVersion(document.getId()).orElse(null));
  }

  private CurrentUser requireExpediente(UUID expedienteId, Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    accessPolicy.requireAccess(expedienteId, user.id(), user.permissions());
    return user;
  }

  private Document requireDocument(UUID documentId, Jwt jwt) {
    Document document = documentService.get(documentId);
    requireExpediente(document.getExpedienteId(), jwt);
    return document;
  }
}
