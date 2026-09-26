package com.c21genera.notifications.application;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.documents.DocumentsApi.AcceptedDocumentView;
import com.c21genera.notifications.NotificationsApi;
import com.c21genera.notifications.application.SendEmailPayload.AttachmentRef;
import com.c21genera.notifications.domain.NotificationLog;
import com.c21genera.notifications.domain.UnreadyExpedienteEmailException;
import com.c21genera.notifications.infrastructure.NotificationLogRepository;
import com.c21genera.shared.config.StorageProperties;
import com.c21genera.shared.domain.DocumentTypeCode;
import com.c21genera.shared.domain.DocumentTypeLabels;
import com.c21genera.shared.jobs.BackgroundJobQueue;
import com.c21genera.shared.storage.FileStorage;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Envío de correos por outbox (ver AGENTS §45-47): cada aviso se registra en
 * notification_log y se encola; {@link NotificationWorker} lo envía y
 * actualiza el registro con el resultado (o el error de entrega).
 */
@Service
public class NotificationService implements NotificationsApi {

  static final String JOB_TYPE = "SEND_EMAIL";
  private static final String SUBJECT = "CENTURY 21 Genera - Documentos de tu expediente";
  private static final String BODY_INTRO =
      "Hola,\n\nAdjuntamos los documentos aceptados de tu expediente hasta el momento.\n\n"
          + "Este es un mensaje automático, no compartas tus documentos por otros medios.\n\n"
          + "CENTURY 21 Genera";

  private final DocumentsApi documentsApi;
  private final BackgroundJobQueue queue;
  private final FileStorage fileStorage;
  private final StorageProperties storageProperties;
  private final NotificationLogRepository logRepository;
  private final Clock clock;

  public NotificationService(
      DocumentsApi documentsApi,
      BackgroundJobQueue queue,
      FileStorage fileStorage,
      StorageProperties storageProperties,
      NotificationLogRepository logRepository,
      Clock clock) {
    this.documentsApi = documentsApi;
    this.queue = queue;
    this.fileStorage = fileStorage;
    this.storageProperties = storageProperties;
    this.logRepository = logRepository;
    this.clock = clock;
  }

  /**
   * Transacción propia: el registro debe quedar guardado aunque quien lo
   * llama sea un listener asíncrono cuya transacción todavía no confirma
   * (igual que el encolado del job).
   */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void notify(UUID expedienteId, String kind, String recipientEmail, String subject, String body) {
    queueEmail(expedienteId, kind, recipientEmail, subject, body, List.of());
  }

  private void queueEmail(
      UUID expedienteId, String kind, String recipientEmail, String subject, String body, List<AttachmentRef> attachments) {
    String recipient = recipientEmail == null || recipientEmail.isBlank() ? null : recipientEmail.strip();
    NotificationLog log = logRepository.save(new NotificationLog(expedienteId, kind, recipient, subject, clock.instant()));
    if (log.getStatus() == NotificationLog.Status.SKIPPED) {
      return;
    }
    queue.enqueue(JOB_TYPE, new SendEmailPayload(log.getId(), expedienteId, recipient, subject, body, attachments));
  }

  @Transactional(readOnly = true)
  public List<NotificationLog> historyOf(UUID expedienteId) {
    return logRepository.findByExpedienteIdOrderByCreatedAtDesc(expedienteId);
  }

  public record EmailAttachmentPreview(String fileName, URI downloadUrl) {}

  /**
   * documentTypesWithoutFile: documentos ya ACCEPTED por el staff pero cuya
   * versión nunca generó un PDF (p. ej. se aceptó por excepción una foto que
   * no pasó la calidad); no hay nada que adjuntar todavía para esos, y el
   * staff debe saberlo antes de enviar el correo, no descubrirlo después con
   * la notaría.
   */
  public record EmailPreview(
      String subject, String body, List<EmailAttachmentPreview> attachments, List<String> documentTypesWithoutFile) {}

  /** No envía nada: solo arma la vista previa para que el staff decida cómo enviarla manualmente. */
  @Transactional(readOnly = true)
  public EmailPreview preview(UUID expedienteId) {
    List<AcceptedDocumentView> accepted = documentsApi.acceptedDocumentsOf(expedienteId);
    if (accepted.isEmpty()) {
      throw new UnreadyExpedienteEmailException(expedienteId);
    }
    List<EmailAttachmentPreview> attachments =
        accepted.stream()
            .filter(doc -> doc.pdfStorageKey() != null)
            .map(
                doc ->
                    new EmailAttachmentPreview(
                        fileName(doc.type()),
                        fileStorage.generateTemporaryDownloadUrl(doc.pdfStorageKey(), storageProperties.presignedUrlTtl())))
            .toList();
    List<String> withoutFile = accepted.stream().filter(doc -> doc.pdfStorageKey() == null).map(doc -> label(doc.type())).toList();
    return new EmailPreview(SUBJECT, BODY_INTRO, attachments, withoutFile);
  }

  @Transactional
  public void sendAcceptedDocuments(UUID expedienteId, String recipientEmail) {
    List<AttachmentRef> attachments = new ArrayList<>();
    for (AcceptedDocumentView doc : documentsApi.acceptedDocumentsOf(expedienteId)) {
      if (doc.pdfStorageKey() != null) {
        attachments.add(new AttachmentRef(doc.pdfStorageKey(), fileName(doc.type())));
      }
    }
    if (attachments.isEmpty()) {
      throw new UnreadyExpedienteEmailException(expedienteId);
    }
    queueEmail(expedienteId, "Documentos aceptados", recipientEmail, SUBJECT, BODY_INTRO, attachments);
  }

  private static String label(String type) {
    try {
      return DocumentTypeLabels.of(DocumentTypeCode.valueOf(type));
    } catch (IllegalArgumentException e) {
      return type;
    }
  }

  private static String fileName(String type) {
    String base =
        java.text.Normalizer.normalize(label(type), java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replaceAll("[^A-Za-z0-9]+", "-")
            .replaceAll("(^-|-$)", "")
            .toLowerCase(java.util.Locale.ROOT);
    return base + ".pdf";
  }
}
