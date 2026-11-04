package com.c21genera.documents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c21genera.documents.ProcessingStatus;
import com.c21genera.documents.domain.Document;
import com.c21genera.documents.domain.DocumentVersion;
import com.c21genera.documents.domain.UploadedVia;
import com.c21genera.documents.infrastructure.DocumentRepository;
import com.c21genera.documents.infrastructure.DocumentVersionRepository;
import com.c21genera.shared.domain.DocumentTypeCode;
import com.c21genera.shared.events.DocumentEvents.DocumentReprocessRequested;
import com.c21genera.shared.jobs.BackgroundJobRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 04/11: un documento "IA analizando" para siempre se relanza solo y, si no hay forma, se cierra. */
class StuckDocumentWatchdogTest {

  private final Instant now = Instant.parse("2026-11-04T20:00:00Z");
  private final DocumentVersionRepository versions = mock(DocumentVersionRepository.class);
  private final DocumentRepository documents = mock(DocumentRepository.class);
  private final BackgroundJobRepository jobs = mock(BackgroundJobRepository.class);
  private final DocumentService documentService = mock(DocumentService.class);
  private final List<Object> published = new ArrayList<>();
  private StuckDocumentWatchdog watchdog;

  @BeforeEach
  void setUp() {
    watchdog = new StuckDocumentWatchdog(versions, documents, jobs, documentService, published::add, Clock.fixed(now, ZoneOffset.UTC));
  }

  private DocumentVersion processedWithoutAi() {
    Document document = new Document(UUID.randomUUID(), "ine", DocumentTypeCode.INE, null, true);
    DocumentVersion version = new DocumentVersion(document.getId(), 1, now.minusSeconds(3600), UploadedVia.PUBLIC_PORTAL, null, null);
    version.completeProcessing("doc.pdf", "m.json");
    when(documents.findById(document.getId())).thenReturn(Optional.of(document));
    when(versions.findByDocumentIdAndRemovedAtIsNullOrderByVersionNumberDesc(document.getId())).thenReturn(List.of(version));
    when(versions.findWaiting(any(), any(), any(), any())).thenReturn(List.of(version));
    return version;
  }

  @Test
  void relaunchesTheAiReadingWhenNoJobIsRunning() {
    DocumentVersion version = processedWithoutAi();
    when(jobs.existsByTypeAndStatusInAndPayloadContaining(eq(StuckDocumentWatchdog.EXTRACT_JOB), any(), anyString())).thenReturn(false);
    when(jobs.countByTypeAndPayloadContaining(eq(StuckDocumentWatchdog.EXTRACT_JOB), anyString())).thenReturn(1L);

    watchdog.sweep();

    assertThat(published).singleElement().isInstanceOfSatisfying(DocumentReprocessRequested.class, e -> {
      assertThat(e.documentVersionId()).isEqualTo(version.getId());
      assertThat(e.pdfStorageKey()).isEqualTo("doc.pdf"); // Solo la lectura con IA, no todo el procesamiento.
    });
  }

  @Test
  void leavesItAloneWhileAJobIsStillActive() {
    processedWithoutAi();
    when(jobs.existsByTypeAndStatusInAndPayloadContaining(any(), any(), anyString())).thenReturn(true);

    watchdog.sweep();

    assertThat(published).isEmpty();
  }

  @Test
  void closesItAsNotReviewedAfterTooManyJobs() {
    DocumentVersion version = processedWithoutAi();
    when(jobs.countByTypeAndPayloadContaining(any(), anyString())).thenReturn((long) StuckDocumentWatchdog.MAX_JOBS);

    watchdog.sweep();

    assertThat(published).isEmpty();
    assertThat(version.isAiCheckFailed()).isTrue();
    assertThat(version.getAiAssessedAt()).isEqualTo(now);
  }

  @Test
  void relaunchesProcessingForAVersionStuckBeforeThePdf() {
    Document document = new Document(UUID.randomUUID(), "ine", DocumentTypeCode.INE, null, true);
    DocumentVersion version = new DocumentVersion(document.getId(), 1, now.minusSeconds(3600), UploadedVia.PUBLIC_PORTAL, null, null);
    when(documents.findById(document.getId())).thenReturn(Optional.of(document));
    when(versions.findByDocumentIdAndRemovedAtIsNullOrderByVersionNumberDesc(document.getId())).thenReturn(List.of(version));
    when(versions.findWaiting(any(), any(), any(), any())).thenReturn(List.of(version));
    assertThat(version.getProcessingStatus()).isEqualTo(ProcessingStatus.QUEUED);

    watchdog.sweep();

    assertThat(published).singleElement().isInstanceOfSatisfying(DocumentReprocessRequested.class, e -> assertThat(e.pdfStorageKey()).isNull());
    verify(jobs).countByTypeAndPayloadContaining(eq(StuckDocumentWatchdog.PROCESS_JOB), anyString());
  }
}
