package com.c21genera.notifications.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.notifications.domain.UnreadyExpedienteEmailException;
import com.c21genera.shared.jobs.BackgroundJobQueue;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {

  private final DocumentsApi documentsApi = mock(DocumentsApi.class);
  private final BackgroundJobQueue queue = mock(BackgroundJobQueue.class);
  private final NotificationService service = new NotificationService(documentsApi, queue);

  @Test
  void enqueuesOneEmailWithOnlyAcceptedDocumentAttachments() {
    UUID expedienteId = UUID.randomUUID();
    UUID acceptedDoc = UUID.randomUUID();
    UUID pendingDoc = UUID.randomUUID();
    when(documentsApi.documentIdsOf(expedienteId)).thenReturn(List.of(acceptedDoc, pendingDoc));
    when(documentsApi.currentAcceptedPdfStorageKey(acceptedDoc)).thenReturn(Optional.of("processed/doc1.pdf"));
    when(documentsApi.currentAcceptedPdfStorageKey(pendingDoc)).thenReturn(Optional.empty());

    service.sendAcceptedDocuments(expedienteId, "cliente@example.com");

    verify(queue)
        .enqueue(
            eq("SEND_EMAIL"),
            any(SendEmailPayload.class));
  }

  @Test
  void throwsWhenNoDocumentsAreAcceptedYet() {
    UUID expedienteId = UUID.randomUUID();
    when(documentsApi.documentIdsOf(expedienteId)).thenReturn(List.of(UUID.randomUUID()));
    when(documentsApi.currentAcceptedPdfStorageKey(any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.sendAcceptedDocuments(expedienteId, "cliente@example.com"))
        .isInstanceOf(UnreadyExpedienteEmailException.class);
  }
}
