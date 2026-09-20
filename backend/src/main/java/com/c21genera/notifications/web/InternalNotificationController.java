package com.c21genera.notifications.web;

import com.c21genera.notifications.application.NotificationService;
import com.c21genera.notifications.application.NotificationService.EmailPreview;
import com.c21genera.notifications.web.NotificationDtos.SendDocumentsEmailRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAuthority('DOCUMENT_EMAIL_SEND')")
public class InternalNotificationController {

  private final NotificationService notificationService;

  public InternalNotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  /** El envío real a notaría es manual: esto solo arma el contenido, no envía nada. */
  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/notifications/documents-email-preview")
  public EmailPreview preview(@PathVariable UUID expedienteId) {
    return notificationService.preview(expedienteId);
  }

  @PostMapping("/api/v1/internal/expedientes/{expedienteId}/notifications/send-documents")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void sendDocuments(@PathVariable UUID expedienteId, @Valid @RequestBody SendDocumentsEmailRequest request) {
    notificationService.sendAcceptedDocuments(expedienteId, request.recipientEmail());
  }
}
