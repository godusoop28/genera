package com.c21genera.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.documents.DocumentsApi.AcceptedDocumentView;
import com.c21genera.notifications.domain.NotificationLog;
import com.c21genera.notifications.domain.UnreadyExpedienteEmailException;
import com.c21genera.notifications.infrastructure.NotificationLogRepository;
import com.c21genera.shared.config.StorageProperties;
import com.c21genera.shared.jobs.BackgroundJobQueue;
import com.c21genera.shared.storage.FileStorage;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationServiceTest {

  private final DocumentsApi documentsApi = mock(DocumentsApi.class);
  private final BackgroundJobQueue queue = mock(BackgroundJobQueue.class);
  private final FileStorage fileStorage = mock(FileStorage.class);
  private final NotificationLogRepository logRepository = mock(NotificationLogRepository.class);
  private final StorageProperties storageProperties =
      new StorageProperties(null, null, null, null, null, null, false, Duration.ofMinutes(15));
  private final NotificationService service =
      new NotificationService(documentsApi, queue, fileStorage, storageProperties, logRepository, Clock.systemUTC());

  {
    when(logRepository.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void enqueuesOneEmailWithOnlyAcceptedDocumentAttachmentsAndLogsIt() {
    UUID expedienteId = UUID.randomUUID();
    when(documentsApi.acceptedDocumentsOf(expedienteId))
        .thenReturn(
            List.of(
                new AcceptedDocumentView(UUID.randomUUID(), "DEED", null, "processed/doc1.pdf"),
                new AcceptedDocumentView(UUID.randomUUID(), "INE", null, null)));

    service.sendAcceptedDocuments(expedienteId, "cliente@example.com");

    ArgumentCaptor<SendEmailPayload> payload = ArgumentCaptor.forClass(SendEmailPayload.class);
    verify(queue).enqueue(eq("SEND_EMAIL"), payload.capture());
    assertThat(payload.getValue().attachments()).hasSize(1);
    assertThat(payload.getValue().notificationId()).isNotNull();
    verify(logRepository).save(any(NotificationLog.class));
  }

  @Test
  void throwsWhenNoDocumentsAreAcceptedYet() {
    UUID expedienteId = UUID.randomUUID();
    when(documentsApi.acceptedDocumentsOf(expedienteId)).thenReturn(List.of(new AcceptedDocumentView(UUID.randomUUID(), "INE", null, null)));

    assertThatThrownBy(() -> service.sendAcceptedDocuments(expedienteId, "cliente@example.com"))
        .isInstanceOf(UnreadyExpedienteEmailException.class);
  }

  @Test
  void notificationWithoutRecipientIsLoggedAsSkippedAndNotQueued() {
    ArgumentCaptor<NotificationLog> saved = ArgumentCaptor.forClass(NotificationLog.class);

    service.notify(UUID.randomUUID(), "Documento devuelto (cliente)", " ", "Asunto", "Cuerpo");

    verify(logRepository).save(saved.capture());
    assertThat(saved.getValue().getStatus()).isEqualTo(NotificationLog.Status.SKIPPED);
    assertThat(saved.getValue().getLastError()).contains("correo");
    verify(queue, never()).enqueue(any(), any());
  }

  @Test
  void previewBuildsContentWithoutSendingAnything() {
    UUID expedienteId = UUID.randomUUID();
    when(documentsApi.acceptedDocumentsOf(expedienteId))
        .thenReturn(
            List.of(
                new AcceptedDocumentView(UUID.randomUUID(), "DEED", null, "processed/doc1.pdf"),
                new AcceptedDocumentView(UUID.randomUUID(), "INE", null, null)));
    when(fileStorage.generateTemporaryDownloadUrl(eq("processed/doc1.pdf"), any()))
        .thenReturn(URI.create("https://example.com/signed-download"));

    NotificationService.EmailPreview preview = service.preview(expedienteId);

    assertThat(preview.attachments()).hasSize(1);
    assertThat(preview.attachments().getFirst().fileName()).isEqualTo("escritura.pdf");
    assertThat(preview.attachments().getFirst().downloadUrl()).hasToString("https://example.com/signed-download");
    assertThat(preview.documentTypesWithoutFile()).containsExactly("Identificación oficial (INE)");
    verify(queue, never()).enqueue(any(), any());
  }

  @Test
  void previewThrowsWhenNoDocumentsAreAcceptedYet() {
    UUID expedienteId = UUID.randomUUID();
    when(documentsApi.acceptedDocumentsOf(expedienteId)).thenReturn(List.of());

    assertThatThrownBy(() -> service.preview(expedienteId)).isInstanceOf(UnreadyExpedienteEmailException.class);
  }

  @Test
  void recipientIsMaskedForTheActivityLog() {
    NotificationLog log = new NotificationLog(UUID.randomUUID(), "x", "juanperez@gmail.com", "s", java.time.Instant.now());
    assertThat(log.maskedRecipient()).isEqualTo("ju***@gmail.com");
  }
}
