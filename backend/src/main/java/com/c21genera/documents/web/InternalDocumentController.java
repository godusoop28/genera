package com.c21genera.documents.web;

import com.c21genera.documents.application.DocumentService;
import com.c21genera.documents.application.DocumentService.UploadedFileContent;
import com.c21genera.documents.domain.DocumentVersion;
import com.c21genera.documents.domain.ReviewDecision;
import com.c21genera.documents.domain.UploadedVia;
import com.c21genera.documents.web.DocumentDtos.DocumentResponse;
import com.c21genera.documents.web.DocumentDtos.DocumentVersionResponse;
import com.c21genera.documents.web.DocumentDtos.ReviewRequest;
import com.c21genera.identity.CurrentUser;
import com.c21genera.shared.config.StorageProperties;
import com.c21genera.shared.domain.NotFoundException;
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

@RestController
@PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
public class InternalDocumentController {

  private final DocumentService documentService;
  private final FileStorage fileStorage;
  private final StorageProperties storageProperties;

  public InternalDocumentController(DocumentService documentService, FileStorage fileStorage, StorageProperties storageProperties) {
    this.documentService = documentService;
    this.fileStorage = fileStorage;
    this.storageProperties = storageProperties;
  }

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/documents")
  public List<DocumentResponse> listByExpediente(@PathVariable UUID expedienteId) {
    return documentService.documentsOf(expedienteId).stream().map(DocumentResponse::from).toList();
  }

  @GetMapping("/api/v1/internal/documents/{documentId}")
  public DocumentResponse get(@PathVariable UUID documentId) {
    return DocumentResponse.from(documentService.get(documentId));
  }

  @GetMapping("/api/v1/internal/documents/{documentId}/versions")
  public List<DocumentVersionResponse> versions(@PathVariable UUID documentId) {
    return documentService.versionsOf(documentId).stream().map(DocumentVersionResponse::from).toList();
  }

  @PostMapping("/api/v1/internal/documents/{documentId}/versions")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public DocumentVersionResponse upload(@PathVariable UUID documentId, @RequestParam("files") List<MultipartFile> files) {
    List<UploadedFileContent> contents =
        files.stream().map(InternalDocumentController::readFile).toList();
    return DocumentVersionResponse.from(documentService.uploadVersion(documentId, contents, UploadedVia.INTERNAL));
  }

  private static UploadedFileContent readFile(MultipartFile file) {
    try {
      return new UploadedFileContent(file.getBytes(), file.getOriginalFilename());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @PostMapping("/api/v1/internal/documents/{documentId}/accept")
  @PreAuthorize("hasAuthority('DOCUMENT_ACCEPT')")
  public DocumentResponse accept(@PathVariable UUID documentId, @AuthenticationPrincipal Jwt jwt) {
    return DocumentResponse.from(
        documentService.review(documentId, ReviewDecision.ACCEPTED, null, null, CurrentUser.from(jwt).id()));
  }

  @PostMapping("/api/v1/internal/documents/{documentId}/return")
  @PreAuthorize("hasAuthority('DOCUMENT_RETURN')")
  public DocumentResponse returnDocument(
      @PathVariable UUID documentId, @Valid @RequestBody ReviewRequest request, @AuthenticationPrincipal Jwt jwt) {
    return DocumentResponse.from(
        documentService.review(
            documentId, ReviewDecision.RETURNED, request.reasonCode(), request.comment(), CurrentUser.from(jwt).id()));
  }

  @PostMapping("/api/v1/internal/documents/{documentId}/reject")
  @PreAuthorize("hasAuthority('DOCUMENT_REJECT')")
  public DocumentResponse reject(
      @PathVariable UUID documentId, @Valid @RequestBody ReviewRequest request, @AuthenticationPrincipal Jwt jwt) {
    return DocumentResponse.from(
        documentService.review(
            documentId, ReviewDecision.REJECTED, request.reasonCode(), request.comment(), CurrentUser.from(jwt).id()));
  }

  @GetMapping("/api/v1/internal/document-versions/{versionId}/processing")
  public DocumentVersionResponse processingStatus(@PathVariable UUID versionId) {
    return DocumentVersionResponse.from(documentService.getVersion(versionId));
  }

  public record DownloadResponse(URI url) {}

  /** Nunca se exponen URLs permanentes: siempre firmadas y temporales (ver AGENTS §29/§101). */
  @GetMapping("/api/v1/internal/document-versions/{versionId}/download")
  public DownloadResponse download(@PathVariable UUID versionId) {
    DocumentVersion version = documentService.getVersion(versionId);
    String key = version.getStorageKeyPdf() != null ? version.getStorageKeyPdf() : version.getStorageKeyNormalized();
    if (key == null) {
      throw new NotFoundException("El documento todavía no tiene un archivo procesado disponible para descarga.");
    }
    return new DownloadResponse(fileStorage.generateTemporaryDownloadUrl(key, storageProperties.presignedUrlTtl()));
  }
}
