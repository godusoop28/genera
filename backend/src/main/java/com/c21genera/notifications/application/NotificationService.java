package com.c21genera.notifications.application;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.notifications.application.SendEmailPayload.AttachmentRef;
import com.c21genera.notifications.domain.UnreadyExpedienteEmailException;
import com.c21genera.shared.jobs.BackgroundJobQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Encola el envío del correo con los documentos aceptados de un expediente
 * (ver AGENTS §45-47): el envío SMTP real ocurre después, en
 * {@link NotificationWorker}, nunca de forma síncrona dentro del request.
 */
@Service
public class NotificationService {

  static final String JOB_TYPE = "SEND_EMAIL";

  private final DocumentsApi documentsApi;
  private final BackgroundJobQueue queue;

  public NotificationService(DocumentsApi documentsApi, BackgroundJobQueue queue) {
    this.documentsApi = documentsApi;
    this.queue = queue;
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

    String subject = "CENTURY 21 Genera - Documentos de tu expediente";
    String body =
        "Hola,\n\nAdjuntamos los documentos aceptados de tu expediente hasta el momento.\n\n"
            + "Este es un mensaje automático del prototipo del Módulo 1, no compartas tus documentos por otros medios.\n\n"
            + "CENTURY 21 Genera";

    queue.enqueue(JOB_TYPE, new SendEmailPayload(recipientEmail, subject, body, attachments));
  }
}
