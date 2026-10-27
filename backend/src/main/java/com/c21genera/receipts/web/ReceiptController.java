package com.c21genera.receipts.web;

import com.c21genera.identity.CurrentUser;
import com.c21genera.receipts.application.ReceiptService;
import com.c21genera.receipts.application.ReceiptService.Preview;
import com.c21genera.receipts.application.ReceiptService.ReceiptView;
import com.c21genera.receipts.application.ReceiptService.SettingsView;
import com.c21genera.shared.security.ExpedienteAccessPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Control de Cambios #31: acuses de recepción del expediente. El acceso sigue
 * los permisos del expediente (CC#6, #16 y #25); generar y enviar requiere
 * RECEIPT_GENERATE, firmar RECEIPT_SIGN y los textos RECEIPT_MANAGE.
 */
@RestController
public class ReceiptController {

  private final ReceiptService receipts;
  private final ExpedienteAccessPolicy accessPolicy;

  public ReceiptController(ReceiptService receipts, ExpedienteAccessPolicy accessPolicy) {
    this.receipts = receipts;
    this.accessPolicy = accessPolicy;
  }

  public record SignRequest(@NotBlank @Size(max = 200) String password, @NotBlank @Size(max = 2_000_000) String signaturePng, @Size(max = 120) String title) {}

  public record SendRequest(@Size(max = 255) String email) {}

  public record DownloadResponse(URI url) {}

  public record SettingsRequest(
      @NotBlank @Size(max = 300) String responsibleEntity,
      @Size(max = 200) String representativeName,
      @Size(max = 120) String representativeTitle,
      @NotBlank @Size(max = 8000) String responsibilityText,
      @NotBlank @Size(max = 8000) String privacyText) {}

  public record ApproveRequest(boolean approved) {}

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/receipts")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
  public List<ReceiptView> list(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    access(expedienteId, jwt);
    return receipts.list(expedienteId);
  }

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/receipts/preview")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
  public Preview preview(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    access(expedienteId, jwt);
    return receipts.preview(expedienteId);
  }

  @PostMapping("/api/v1/internal/expedientes/{expedienteId}/receipts")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('RECEIPT_GENERATE')")
  public ReceiptView generate(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    return receipts.generate(expedienteId, access(expedienteId, jwt));
  }

  @PostMapping("/api/v1/internal/receipts/{receiptId}/request-signature")
  @PreAuthorize("hasAuthority('RECEIPT_GENERATE')")
  public ReceiptView requestSignature(@PathVariable UUID receiptId, @AuthenticationPrincipal Jwt jwt) {
    return receipts.requestSignature(receiptId, accessToReceipt(receiptId, jwt));
  }

  @PostMapping("/api/v1/internal/receipts/{receiptId}/sign")
  @PreAuthorize("hasAuthority('RECEIPT_SIGN')")
  public ReceiptView sign(@PathVariable UUID receiptId, @Valid @RequestBody SignRequest r, @AuthenticationPrincipal Jwt jwt) {
    return receipts.signElectronically(receiptId, r.password(), r.signaturePng(), r.title(), accessToReceipt(receiptId, jwt));
  }

  @PostMapping("/api/v1/internal/receipts/{receiptId}/signed-file")
  @PreAuthorize("hasAuthority('RECEIPT_SIGN')")
  public ReceiptView uploadSigned(@PathVariable UUID receiptId, @RequestParam("file") MultipartFile file, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = accessToReceipt(receiptId, jwt);
    try {
      return receipts.uploadSigned(receiptId, file.getBytes(), user);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @PostMapping("/api/v1/internal/receipts/{receiptId}/send")
  @PreAuthorize("hasAuthority('RECEIPT_GENERATE')")
  public ReceiptView send(@PathVariable UUID receiptId, @Valid @RequestBody SendRequest r, @AuthenticationPrincipal Jwt jwt) {
    return receipts.send(receiptId, r.email(), accessToReceipt(receiptId, jwt));
  }

  /** which=signed: el acuse firmado; si no, el borrador (con marca de agua). */
  @GetMapping("/api/v1/internal/receipts/{receiptId}/download")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
  public DownloadResponse download(@PathVariable UUID receiptId, @RequestParam(defaultValue = "draft") String which, @AuthenticationPrincipal Jwt jwt) {
    accessToReceipt(receiptId, jwt);
    return new DownloadResponse(receipts.download(receiptId, "signed".equals(which)));
  }

  @GetMapping("/api/v1/internal/receipts/settings")
  @PreAuthorize("hasAnyAuthority('RECEIPT_MANAGE', 'RECEIPT_SIGN', 'RECEIPT_GENERATE')")
  public SettingsView settings() {
    return receipts.settingsView();
  }

  @PutMapping("/api/v1/internal/receipts/settings")
  @PreAuthorize("hasAuthority('RECEIPT_MANAGE')")
  public SettingsView updateSettings(@Valid @RequestBody SettingsRequest r, @AuthenticationPrincipal Jwt jwt) {
    return receipts.updateSettings(r.responsibleEntity(), r.representativeName(), r.representativeTitle(), r.responsibilityText(), r.privacyText(), CurrentUser.from(jwt));
  }

  /** El área legal aprueba (o retira la aprobación de) los textos vigentes. */
  @PostMapping("/api/v1/internal/receipts/settings/approval")
  @PreAuthorize("hasAuthority('RECEIPT_MANAGE')")
  public SettingsView approve(@Valid @RequestBody ApproveRequest r, @AuthenticationPrincipal Jwt jwt) {
    return receipts.approve(r.approved(), CurrentUser.from(jwt));
  }

  private CurrentUser access(UUID expedienteId, Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    accessPolicy.requireAccess(expedienteId, user.id(), user.permissions());
    return user;
  }

  private CurrentUser accessToReceipt(UUID receiptId, Jwt jwt) {
    return access(receipts.expedienteOf(receiptId), jwt);
  }
}
