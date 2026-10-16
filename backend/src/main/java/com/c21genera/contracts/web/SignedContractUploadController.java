package com.c21genera.contracts.web;

import com.c21genera.contracts.application.SignedContractUploadService;
import com.c21genera.contracts.domain.SignedContractUpload;
import com.c21genera.documents.DocumentsApi.IncomingFile;
import com.c21genera.identity.CurrentUser;
import com.c21genera.shared.security.ExpedienteAccessPolicy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Control de Cambios #20: "Subir contrato firmado" (DocuSign o escaneo de la firma física), también
 * de operaciones anteriores al sistema. El archivo se consulta en el visor con los permisos por rol.
 */
@RestController
@PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
public class SignedContractUploadController {

  private final SignedContractUploadService service;
  private final ExpedienteAccessPolicy accessPolicy;

  public SignedContractUploadController(SignedContractUploadService service, ExpedienteAccessPolicy accessPolicy) {
    this.service = service;
    this.accessPolicy = accessPolicy;
  }

  public record SignedContractResponse(
      UUID id,
      UUID documentId,
      String signatureMethod,
      boolean historical,
      String note,
      String uploadedByName,
      Instant uploadedAt) {

    static SignedContractResponse from(SignedContractUpload u) {
      return new SignedContractResponse(
          u.getId(), u.getDocumentId(), u.getSignatureMethod().name(), u.isHistorical(), u.getNote(), u.getUploadedByName(), u.getUploadedAt());
    }
  }

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/signed-contracts")
  public List<SignedContractResponse> list(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    requireAccess(expedienteId, jwt);
    return service.uploadsOf(expedienteId).stream().map(SignedContractResponse::from).toList();
  }

  @PostMapping("/api/v1/internal/expedientes/{expedienteId}/signed-contracts")
  @PreAuthorize("hasAnyAuthority('DOCUMENT_UPLOAD', 'CONTRACT_GENERATE', 'CONTRACT_SIGN')")
  @ResponseStatus(HttpStatus.CREATED)
  public SignedContractResponse upload(
      @PathVariable UUID expedienteId,
      @RequestParam("files") List<MultipartFile> files,
      @RequestParam("signatureMethod") String signatureMethod,
      @RequestParam(value = "historical", defaultValue = "false") boolean historical,
      @RequestParam(value = "note", required = false) String note,
      @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(expedienteId, jwt);
    List<IncomingFile> incoming = files.stream().map(f -> new IncomingFile(f.getSize(), f.getOriginalFilename(), f)).toList();
    return SignedContractResponse.from(service.upload(expedienteId, incoming, signatureMethod, historical, note, user.toActor()));
  }

  private CurrentUser requireAccess(UUID expedienteId, Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    accessPolicy.requireAccess(expedienteId, user.id(), user.permissions());
    return user;
  }
}
