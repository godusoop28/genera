package com.c21genera.documents.web;

import com.c21genera.documents.application.DocumentService.ReviewCommand;
import com.c21genera.documents.application.DocumentService.UploadedFileContent;
import com.c21genera.documents.application.DocumentService;
import com.c21genera.documents.application.DocumentViewer;
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
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
  private final DocumentViewer viewer;
  private final FileStorage fileStorage;
  private final StorageProperties storageProperties;
  private final ExpedienteAccessPolicy accessPolicy;

  public InternalDocumentController(
      DocumentService documentService,
      DocumentViewer viewer,
      FileStorage fileStorage,
      StorageProperties storageProperties,
      ExpedienteAccessPolicy accessPolicy) {
    this.documentService = documentService;
    this.viewer = viewer;
    this.fileStorage = fileStorage;
    this.storageProperties = storageProperties;
    this.accessPolicy = accessPolicy;
  }

  public record CustomDocumentRequest(String name, UUID participantId) {}

  public record NoteRequest(String note) {}

  /** Control de Cambios #1: documento que no está en el listado, con el nombre que se capture. */
  @PostMapping("/api/v1/internal/expedientes/{expedienteId}/documents")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_EDIT', 'DOCUMENT_UPLOAD')")
  @ResponseStatus(HttpStatus.CREATED)
  public DocumentResponse addCustom(
      @PathVariable UUID expedienteId, @RequestBody CustomDocumentRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireExpediente(expedienteId, jwt);
    return toResponse(
        documentService.addCustomDocument(expedienteId, request == null ? null : request.name(), request == null ? null : request.participantId(), user.toActor()));
  }

  /** Control de Cambios #5: falta, pero se aprueba seguir sin él por ahora (con nota). */
  @PutMapping("/api/v1/internal/documents/{documentId}/exception")
  @PreAuthorize("hasAuthority('DOCUMENT_EXCEPTION')")
  public DocumentResponse approveException(@PathVariable UUID documentId, @RequestBody NoteRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    requireDocument(documentId, jwt);
    return toResponse(documentService.approveException(documentId, request == null ? null : request.note(), user.toActor()));
  }

  @DeleteMapping("/api/v1/internal/documents/{documentId}/exception")
  @PreAuthorize("hasAuthority('DOCUMENT_EXCEPTION')")
  public DocumentResponse revokeException(@PathVariable UUID documentId, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    requireDocument(documentId, jwt);
    return toResponse(documentService.revokeException(documentId, user.toActor()));
  }

  /** Control de Cambios #10: pedir al cliente una versión actualizada; la vigente se conserva. */
  @PutMapping("/api/v1/internal/documents/{documentId}/update-request")
  @PreAuthorize("hasAuthority('DOCUMENT_UPDATE_REQUEST')")
  public DocumentResponse requestUpdate(@PathVariable UUID documentId, @RequestBody NoteRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    requireDocument(documentId, jwt);
    return toResponse(documentService.requestUpdate(documentId, request == null ? null : request.note(), user.toActor()));
  }

  @DeleteMapping("/api/v1/internal/documents/{documentId}/update-request")
  @PreAuthorize("hasAuthority('DOCUMENT_UPDATE_REQUEST')")
  public DocumentResponse cancelUpdateRequest(@PathVariable UUID documentId, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    requireDocument(documentId, jwt);
    return toResponse(documentService.cancelUpdateRequest(documentId, user.toActor()));
  }

  public record PendingRequest(String note, java.time.LocalDate dueDate) {}

  /** Control de Cambios #14: "Marcar como pendiente" (no bloquea; al subirse pasa a "Entregado"). */
  @PutMapping("/api/v1/internal/documents/{documentId}/pending")
  @PreAuthorize("hasAnyAuthority('DOCUMENT_UPLOAD', 'DOCUMENT_ACCEPT')")
  public DocumentResponse markPending(
      @PathVariable UUID documentId, @RequestBody(required = false) PendingRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    requireDocument(documentId, jwt);
    return toResponse(
        documentService.markPending(
            documentId, request == null ? null : request.note(), request == null ? null : request.dueDate(), user.toActor()));
  }

  @DeleteMapping("/api/v1/internal/documents/{documentId}/pending")
  @PreAuthorize("hasAnyAuthority('DOCUMENT_UPLOAD', 'DOCUMENT_ACCEPT')")
  public DocumentResponse clearPending(@PathVariable UUID documentId, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    requireDocument(documentId, jwt);
    return toResponse(documentService.clearPending(documentId, user.toActor()));
  }

  public record RenameRequest(String name) {}

  /** Control de Cambios #18: corregir el nombre de un documento agregado a mano (el archivo no cambia). */
  @PutMapping("/api/v1/internal/documents/{documentId}/name")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_EDIT', 'DOCUMENT_UPLOAD')")
  public DocumentResponse rename(@PathVariable UUID documentId, @RequestBody RenameRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    requireDocument(documentId, jwt);
    return toResponse(documentService.rename(documentId, request == null ? null : request.name(), user.toActor()));
  }

  public record AddressProofKindRequest(String kind) {}

  /** Control de Cambios #13: qué comprobante de domicilio se entregó. */
  @PutMapping("/api/v1/internal/documents/{documentId}/address-proof-kind")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_EDIT', 'DOCUMENT_UPLOAD', 'DOCUMENT_ACCEPT')")
  public DocumentResponse changeAddressProofKind(
      @PathVariable UUID documentId, @RequestBody AddressProofKindRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    requireDocument(documentId, jwt);
    return toResponse(documentService.changeAddressProofKind(documentId, request == null ? null : request.kind(), user.toActor()));
  }

  /** canDownload: si quien consulta puede además descargar o imprimir el original. */
  public record ViewerResponse(int pageCount, boolean canDownload) {}

  /** Control de Cambios #6: el visor muestra cada página como imagen con marca de agua, sin el archivo. */
  @GetMapping("/api/v1/internal/document-versions/{versionId}/view")
  public ViewerResponse view(@PathVariable UUID versionId, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireVersion(versionId, jwt);
    return new ViewerResponse(viewer.info(versionId).pageCount(), user.permissions().contains("DOCUMENT_DOWNLOAD"));
  }

  @GetMapping(value = "/api/v1/internal/document-versions/{versionId}/view/pages/{pageNumber}", produces = MediaType.IMAGE_JPEG_VALUE)
  public ResponseEntity<byte[]> viewPage(@PathVariable UUID versionId, @PathVariable int pageNumber, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireVersion(versionId, jwt);
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
        .contentType(MediaType.IMAGE_JPEG)
        .body(viewer.page(versionId, pageNumber, user.name()));
  }

  private CurrentUser requireVersion(UUID versionId, Jwt jwt) {
    DocumentVersion version = documentService.getVersion(versionId);
    requireDocument(version.getDocumentId(), jwt);
    return CurrentUser.from(jwt);
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
      @PathVariable UUID documentId,
      @RequestParam("files") List<MultipartFile> files,
      @RequestParam(value = "addressProofKind", required = false) String addressProofKind,
      @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    requireDocument(documentId, jwt);
    List<UploadedFileContent> contents = files.stream().map(InternalDocumentController::readFile).toList();
    return DocumentVersionResponse.from(
        documentService.uploadVersion(documentId, contents, UploadedVia.INTERNAL, user.toActor(), addressProofKind));
  }

  private static UploadedFileContent readFile(MultipartFile file) {
    // Sin getBytes(): Tomcat ya guardó el archivo en disco y se lee de ahí en flujo.
    return new UploadedFileContent(file.getSize(), file.getOriginalFilename(), file);
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

  public record DeferralRequest(boolean deferred, String reason) {}

  /** Quien prepara la liga decide qué se pide en la primera entrega y qué puede subir el cliente después. */
  @PutMapping("/api/v1/internal/documents/{documentId}/deferral")
  @PreAuthorize("hasAuthority('PUBLIC_LINK_GENERATE')")
  public DocumentResponse changeDeferral(@PathVariable UUID documentId, @RequestBody DeferralRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    requireDocument(documentId, jwt);
    return toResponse(documentService.changeDeferred(documentId, request.deferred(), request.reason(), user.toActor()));
  }

  @PostMapping("/api/v1/internal/documents/{documentId}/request-again")
  @PreAuthorize("hasAuthority('DOCUMENT_MARK_NOT_APPLICABLE')")
  public DocumentResponse requestAgain(@PathVariable UUID documentId, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    requireDocument(documentId, jwt);
    return toResponse(documentService.requestAgain(documentId, user.toActor()));
  }

  public record ReprocessRequest(Boolean full) {}

  public record ReprocessAllResponse(int reprocessed) {}

  public record MoveRequest(UUID targetDocumentId) {}

  /** "Reprocesar con IA" sin que el cliente vuelva a subir el archivo. */
  @PostMapping("/api/v1/internal/documents/{documentId}/reprocess")
  @PreAuthorize("hasAnyAuthority('DOCUMENT_ACCEPT', 'EXTRACTED_DATA_EDIT', 'DOCUMENT_UPLOAD')")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public DocumentResponse reprocess(
      @PathVariable UUID documentId, @RequestBody(required = false) ReprocessRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    requireDocument(documentId, jwt);
    documentService.reprocess(documentId, request != null && Boolean.TRUE.equals(request.full()), user.toActor());
    return toResponse(documentService.get(documentId));
  }

  /** Reprocesa todos los archivos del expediente (útil tras cambiar las reglas de recepción). */
  @PostMapping("/api/v1/internal/expedientes/{expedienteId}/documents/reprocess")
  @PreAuthorize("hasAnyAuthority('DOCUMENT_ACCEPT', 'EXTRACTED_DATA_EDIT', 'DOCUMENT_UPLOAD')")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public ReprocessAllResponse reprocessAll(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireExpediente(expedienteId, jwt);
    return new ReprocessAllResponse(documentService.reprocessAll(expedienteId, user.toActor()));
  }

  /** "Cambiar tipo de documento": mueve el archivo vigente a otro requisito del expediente. */
  @PostMapping("/api/v1/internal/documents/{documentId}/move")
  @PreAuthorize("hasAnyAuthority('DOCUMENT_UPLOAD', 'DOCUMENT_ACCEPT')")
  public DocumentResponse move(@PathVariable UUID documentId, @RequestBody MoveRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    requireDocument(documentId, jwt);
    if (request == null || request.targetDocumentId() == null) {
      throw new com.c21genera.shared.domain.UnprocessableException("TARGET_REQUIRED", "Elige a qué documento corresponde el archivo.");
    }
    requireDocument(request.targetDocumentId(), jwt);
    return toResponse(documentService.moveCurrentFile(documentId, request.targetDocumentId(), user.toActor()));
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

  /**
   * Nunca se exponen URLs permanentes: siempre firmadas y temporales (ver AGENTS §29/§101).
   * Descargar el original es un permiso especial (Control de Cambios #6); los demás usan el visor.
   */
  @GetMapping("/api/v1/internal/document-versions/{versionId}/download")
  @PreAuthorize("hasAuthority('DOCUMENT_DOWNLOAD')")
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
