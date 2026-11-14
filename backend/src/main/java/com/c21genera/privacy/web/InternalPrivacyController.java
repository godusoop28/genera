package com.c21genera.privacy.web;

import com.c21genera.documents.DocumentsApi.IncomingFile;
import com.c21genera.identity.CurrentUser;
import com.c21genera.documents.DocumentsApi;
import com.c21genera.privacy.application.PrivacyConsentService;
import com.c21genera.privacy.application.PrivacySignLinkService;
import com.c21genera.privacy.application.PrivacySignLinkService.IssuedSignLink;
import com.c21genera.privacy.application.PrivacySignLinkService.SignLinkStatus;
import com.c21genera.privacy.domain.PrivacyConsent;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Control de Cambios #2: el expediente muestra si el aviso de privacidad está "Pendiente de firma"
 * o "Firmado", y el staff puede cargar el aviso firmado en papel en la oficina.
 */
@RestController
@PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
public class InternalPrivacyController {

  private final PrivacyConsentService privacyConsentService;
  private final PrivacySignLinkService signLinkService;
  private final DocumentsApi documentsApi;
  private final ExpedienteAccessPolicy accessPolicy;

  public InternalPrivacyController(
      PrivacyConsentService privacyConsentService,
      PrivacySignLinkService signLinkService,
      DocumentsApi documentsApi,
      ExpedienteAccessPolicy accessPolicy) {
    this.privacyConsentService = privacyConsentService;
    this.signLinkService = signLinkService;
    this.documentsApi = documentsApi;
    this.accessPolicy = accessPolicy;
  }

  /**
   * status: SIGNED, PENDING_VERIFICATION o PENDING (Control de Cambios #45: cargar un escaneo no es
   * una firma confirmada; queda «Firma por verificar» hasta que se acepta el documento en
   * Documentos). signatureMethod: DIGITAL (en una liga) o PHYSICAL (en papel). signedDocumentId:
   * el documento «Aviso de Privacidad» del expediente (escaneo o copia firmada digitalmente).
   * signatureChannel: PORTAL o SIGN_LINK. signLink: la liga exclusiva más reciente (CC#44).
   */
  public record PrivacyStatusResponse(
      String status,
      String signatureMethod,
      Instant signedAt,
      Boolean secondaryPurposesAccepted,
      UUID signedDocumentId,
      String signatureChannel,
      SignLinkStatus signLink) {}

  private PrivacyStatusResponse statusOf(UUID expedienteId) {
    PrivacyConsent c = privacyConsentService.consentEntityOf(expedienteId).orElse(null);
    SignLinkStatus link = signLinkService.statusOf(expedienteId).orElse(null);
    if (c == null || !c.isMainPurposesAccepted()) {
      return new PrivacyStatusResponse("PENDING", null, null, null, null, null, link);
    }
    String status = "SIGNED";
    if ("PHYSICAL".equals(c.getSignatureMethod())) {
      boolean accepted =
          c.getSignedDocumentId() != null
              && documentsApi.requirementStatusOf(expedienteId).stream()
                  .anyMatch(d -> d.documentId().equals(c.getSignedDocumentId()) && "ACCEPTED".equals(d.status()));
      status = accepted ? "SIGNED" : "PENDING_VERIFICATION";
    }
    return new PrivacyStatusResponse(
        status, c.getSignatureMethod(), c.getAcceptedAt(), c.isSecondaryPurposesAccepted(), c.getSignedDocumentId(), c.getSignatureChannel(), link);
  }

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/privacy")
  public PrivacyStatusResponse status(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    requireAccess(expedienteId, jwt);
    return statusOf(expedienteId);
  }

  /**
   * Control de Cambios #44: liga exclusiva para que el cliente firme el aviso. Generar otra
   * desactiva la anterior (sirve para reenviar). No cambia el estado del aviso.
   */
  @PostMapping("/api/v1/internal/expedientes/{expedienteId}/privacy/sign-link")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_EDIT', 'DOCUMENT_UPLOAD')")
  @ResponseStatus(HttpStatus.CREATED)
  public IssuedSignLink issueSignLink(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(expedienteId, jwt);
    return signLinkService.issue(expedienteId, user.toActor());
  }

  @DeleteMapping("/api/v1/internal/expedientes/{expedienteId}/privacy/sign-link")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_EDIT', 'DOCUMENT_UPLOAD')")
  public PrivacyStatusResponse revokeSignLink(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(expedienteId, jwt);
    signLinkService.revoke(expedienteId, user.toActor());
    return statusOf(expedienteId);
  }

  /** El titular firmó el aviso impreso en la oficina: se carga el escaneo o la foto. */
  @PostMapping("/api/v1/internal/expedientes/{expedienteId}/privacy/physical-signature")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_EDIT', 'DOCUMENT_UPLOAD')")
  @ResponseStatus(HttpStatus.CREATED)
  public PrivacyStatusResponse physicalSignature(
      @PathVariable UUID expedienteId,
      @RequestParam("files") List<MultipartFile> files,
      @RequestParam(value = "secondaryPurposesAccepted", defaultValue = "false") boolean secondaryPurposesAccepted,
      @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(expedienteId, jwt);
    List<IncomingFile> incoming = files.stream().map(f -> new IncomingFile(f.getSize(), f.getOriginalFilename(), f)).toList();
    privacyConsentService.recordPhysicalSignature(expedienteId, incoming, secondaryPurposesAccepted, user.toActor());
    return statusOf(expedienteId);
  }

  private CurrentUser requireAccess(UUID expedienteId, Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    accessPolicy.requireAccess(expedienteId, user.id(), user.permissions());
    return user;
  }
}
