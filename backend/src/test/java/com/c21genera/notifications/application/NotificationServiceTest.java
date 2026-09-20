package com.c21genera.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.documents.DocumentsApi.AcceptedDocumentView;
import com.c21genera.notifications.domain.UnreadyExpedienteEmailException;
import com.c21genera.shared.config.StorageProperties;
import com.c21genera.shared.jobs.BackgroundJobQueue;
import com.c21genera.shared.storage.FileStorage;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {

  private final DocumentsApi documentsApi = mock(DocumentsApi.class);
  private final BackgroundJobQueue queue = mock(BackgroundJobQueue.class);
  private final FileStorage fileStorage = mock(FileStorage.class);
  private final StorageProperties storageProperties =
      new StorageProperties(null, null, null, null, null, null, false, Duration.ofMinutes(15));
  private final NotificationService service = new NotificationService(documentsApi, queue, fileStorage, storageProperties);

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

  @Test
  void previewBuildsContentWithoutSendingAnything() {
    UUID expedienteId = UUID.randomUUID();
    UUID acceptedDoc = UUID.randomUUID();
    when(documentsApi.acceptedDocumentsOf(expedienteId))
        .thenReturn(List.of(new AcceptedDocumentView(acceptedDoc, "DEED", "processed/doc1.pdf")));
    when(fileStorage.generateTemporaryDownloadUrl(eq("processed/doc1.pdf"), any()))
        .thenReturn(URI.create("https://example.com/signed-download"));

    NotificationService.EmailPreview preview = service.preview(expedienteId);

    assertThat(preview.attachments()).hasSize(1);
    assertThat(preview.attachments().getFirst().fileName()).isEqualTo("DEED.pdf");
    assertThat(preview.attachments().getFirst().downloadUrl()).hasToString("https://example.com/signed-download");
    verify(queue, org.mockito.Mockito.never()).enqueue(any(), any());
  }

  @Test
  void previewThrowsWhenNoDocumentsAreAcceptedYet() {
    UUID expedienteId = UUID.randomUUID();
    when(documentsApi.acceptedDocumentsOf(expedienteId)).thenReturn(List.of());

    assertThatThrownBy(() -> service.preview(expedienteId)).isInstanceOf(UnreadyExpedienteEmailException.class);
  }
}
