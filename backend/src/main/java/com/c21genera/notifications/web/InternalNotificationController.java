package com.c21genera.notifications.web;

import com.c21genera.identity.CurrentUser;
import com.c21genera.notifications.application.NotificationService;
import com.c21genera.notifications.application.NotificationService.EmailPreview;
import com.c21genera.notifications.domain.NotificationLog;
import com.c21genera.notifications.web.NotificationDtos.SendDocumentsEmailRequest;
import com.c21genera.shared.security.ExpedienteAccessPolicy;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalNotificationController {

  private final NotificationService notificationService;
  private final ExpedienteAccessPolicy accessPolicy;

  public InternalNotificationController(NotificationService notificationService, ExpedienteAccessPolicy accessPolicy) {
    this.notificationService = notificationService;
    this.accessPolicy = accessPolicy;
  }

  /** Historial de avisos del expediente, con su estado de entrega y el error si falló. */
  public record NotificationResponse(
      UUID id, String kind, String recipient, String subject, String status, int attempts, String lastError, Instant createdAt, Instant sentAt) {

    static NotificationResponse from(NotificationLog n) {
      return new NotificationResponse(
          n.getId(),
          n.getKind(),
          n.maskedRecipient(),
          n.getSubject(),
          n.getStatus().name(),
          n.getAttempts(),
          n.getLastError(),
          n.getCreatedAt(),
          n.getSentAt());
    }
  }

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/notifications")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
  public List<NotificationResponse> history(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    requireAccess(expedienteId, jwt);
    return notificationService.historyOf(expedienteId).stream().map(NotificationResponse::from).toList();
  }

  /** El envío real a notaría es manual: esto solo arma el contenido, no envía nada. */
  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/notifications/documents-email-preview")
  @PreAuthorize("hasAuthority('DOCUMENT_EMAIL_SEND')")
  public EmailPreview preview(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    requireAccess(expedienteId, jwt);
    return notificationService.preview(expedienteId);
  }

  @PostMapping("/api/v1/internal/expedientes/{expedienteId}/notifications/send-documents")
  @PreAuthorize("hasAuthority('DOCUMENT_EMAIL_SEND')")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void sendDocuments(
      @PathVariable UUID expedienteId, @Valid @RequestBody SendDocumentsEmailRequest request, @AuthenticationPrincipal Jwt jwt) {
    requireAccess(expedienteId, jwt);
    notificationService.sendAcceptedDocuments(expedienteId, request.recipientEmail());
  }

  private void requireAccess(UUID expedienteId, Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    accessPolicy.requireAccess(expedienteId, user.id(), user.permissions());
  }
}
