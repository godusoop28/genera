package com.c21genera.privacy.web;

import com.c21genera.documents.DocumentsApi.IncomingFile;
import com.c21genera.identity.CurrentUser;
import com.c21genera.privacy.application.PrivacyConsentService;
import com.c21genera.privacy.domain.PrivacyConsent;
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
 * Control de Cambios #2: el expediente muestra si el aviso de privacidad está "Pendiente de firma"
 * o "Firmado", y el staff puede cargar el aviso firmado en papel en la oficina.
 */
@RestController
@PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
public class InternalPrivacyController {

  private final PrivacyConsentService privacyConsentService;
  private final ExpedienteAccessPolicy accessPolicy;

  public InternalPrivacyController(PrivacyConsentService privacyConsentService, ExpedienteAccessPolicy accessPolicy) {
    this.privacyConsentService = privacyConsentService;
    this.accessPolicy = accessPolicy;
  }

  /**
   * status: SIGNED (aceptó la finalidad principal) o PENDING. signatureMethod: DIGITAL (en su liga)
   * o PHYSICAL (en papel; signedDocumentId es el escaneo dentro de los documentos del expediente).
   */
  public record PrivacyStatusResponse(
      String status, String signatureMethod, Instant signedAt, Boolean secondaryPurposesAccepted, UUID signedDocumentId) {

    static PrivacyStatusResponse of(PrivacyConsent c) {
      if (c == null || !c.isMainPurposesAccepted()) {
        return new PrivacyStatusResponse("PENDING", null, null, null, null);
      }
      return new PrivacyStatusResponse("SIGNED", c.getSignatureMethod(), c.getAcceptedAt(), c.isSecondaryPurposesAccepted(), c.getSignedDocumentId());
    }
  }

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/privacy")
  public PrivacyStatusResponse status(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    requireAccess(expedienteId, jwt);
    return PrivacyStatusResponse.of(privacyConsentService.consentEntityOf(expedienteId).orElse(null));
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
    return PrivacyStatusResponse.of(
        privacyConsentService.recordPhysicalSignature(expedienteId, incoming, secondaryPurposesAccepted, user.toActor()));
  }

  private CurrentUser requireAccess(UUID expedienteId, Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    accessPolicy.requireAccess(expedienteId, user.id(), user.permissions());
    return user;
  }
}
