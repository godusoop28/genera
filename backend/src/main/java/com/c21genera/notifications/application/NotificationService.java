package com.c21genera.notifications.application;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.documents.DocumentsApi.AcceptedDocumentView;
import com.c21genera.notifications.application.SendEmailPayload.AttachmentRef;
import com.c21genera.notifications.domain.UnreadyExpedienteEmailException;
import com.c21genera.shared.config.StorageProperties;
import com.c21genera.shared.jobs.BackgroundJobQueue;
import com.c21genera.shared.storage.FileStorage;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Arma el correo con los documentos aceptados de un expediente (ver AGENTS
 * §45-47). El envío real a la notaría es manual: {@link #preview} solo
 * arma el contenido (asunto, cuerpo, ligas de descarga firmadas) para que
 * el staff lo revise y lo envíe él mismo desde su propio correo. El envío
 * SMTP automático de {@link #sendAcceptedDocuments} queda sin usar desde el
 * frontend mientras no se configure un proveedor SMTP real.
 */
@Service
public class NotificationService {

  static final String JOB_TYPE = "SEND_EMAIL";
  private static final String SUBJECT = "CENTURY 21 Genera - Documentos de tu expediente";
  private static final String BODY_INTRO =
      "Hola,\n\nAdjuntamos los documentos aceptados de tu expediente hasta el momento.\n\n"
          + "Este es un mensaje automático del prototipo del Módulo 1, no compartas tus documentos por otros medios.\n\n"
          + "CENTURY 21 Genera";

  private final DocumentsApi documentsApi;
  private final BackgroundJobQueue queue;
  private final FileStorage fileStorage;
  private final StorageProperties storageProperties;

  public NotificationService(
      DocumentsApi documentsApi, BackgroundJobQueue queue, FileStorage fileStorage, StorageProperties storageProperties) {
    this.documentsApi = documentsApi;
    this.queue = queue;
    this.fileStorage = fileStorage;
    this.storageProperties = storageProperties;
  }

  public record EmailAttachmentPreview(String fileName, URI downloadUrl) {}

  public record EmailPreview(String subject, String body, List<EmailAttachmentPreview> attachments) {}

  /** No envía nada: solo arma la vista previa para que el staff decida cómo enviarla manualmente. */
  public EmailPreview preview(UUID expedienteId) {
    List<AcceptedDocumentView> accepted = documentsApi.acceptedDocumentsOf(expedienteId);
    if (accepted.isEmpty()) {
      throw new UnreadyExpedienteEmailException(expedienteId);
    }
    List<EmailAttachmentPreview> attachments =
        accepted.stream()
            .map(
                doc ->
                    new EmailAttachmentPreview(
                        doc.type() + ".pdf", fileStorage.generateTemporaryDownloadUrl(doc.pdfStorageKey(), storageProperties.presignedUrlTtl())))
            .toList();
    return new EmailPreview(SUBJECT, BODY_INTRO, attachments);
  }

  public void sendAcceptedDocuments(UUID expedienteId, String recipientEmail) {
    List<AttachmentRef> attachments = new ArrayList<>();
    int index = 1;
    for (UUID documentId : documentsApi.documentIdsOf(expedienteId)) {
      var pdfKey = documentsApi.currentAcceptedPdfStorageKey(documentId);
      if (pdfKey.isPresent()) {
        attachments.add(new AttachmentRef(pdfKey.get(), "documento-%d.pdf".formatted(index++)));
      }
    }

    if (attachments.isEmpty()) {
      throw new UnreadyExpedienteEmailException(expedienteId);
    }

    queue.enqueue(JOB_TYPE, new SendEmailPayload(recipientEmail, SUBJECT, BODY_INTRO, attachments));
  }
}
