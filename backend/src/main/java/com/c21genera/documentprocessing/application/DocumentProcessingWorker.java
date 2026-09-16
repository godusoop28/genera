package com.c21genera.documentprocessing.application;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.shared.jobs.BackgroundJob;
import com.c21genera.shared.jobs.BackgroundJobQueue;
import com.c21genera.shared.jobs.JobStatus;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Consumidor del job PROCESS_DOCUMENT_VERSION (ver AGENTS §35: sin cola
 * externa por ahora, pero con SKIP LOCKED para poder escalar horizontalmente
 * después sin cambiar este código).
 */
@Component
public class DocumentProcessingWorker {

  private static final Logger log = LoggerFactory.getLogger(DocumentProcessingWorker.class);
  private static final int BATCH_SIZE = 5;
  private static final String WORKER_ID = "documentprocessing-worker";

  private final BackgroundJobQueue queue;
  private final DocumentVersionProcessor processor;
  private final DocumentsApi documentsApi;

  public DocumentProcessingWorker(BackgroundJobQueue queue, DocumentVersionProcessor processor, DocumentsApi documentsApi) {
    this.queue = queue;
    this.processor = processor;
    this.documentsApi = documentsApi;
  }

  @Scheduled(fixedDelayString = "PT5S", initialDelayString = "PT10S")
  public void pollAndProcess() {
    List<BackgroundJob> jobs = queue.claimBatch(DocumentProcessingListener.JOB_TYPE, WORKER_ID, BATCH_SIZE);
    for (BackgroundJob job : jobs) {
      handle(job);
    }
  }

  private void handle(BackgroundJob job) {
    ProcessDocumentVersionPayload payload = queue.readPayload(job, ProcessDocumentVersionPayload.class);
    try {
      processor.process(payload);
      queue.markDone(job);
    } catch (Exception e) {
      log.error("Fallo procesando documentVersionId={}", payload.documentVersionId(), e);
      JobStatus finalStatus = queue.markFailedOrRetry(job, e.getMessage());
      if (finalStatus == JobStatus.FAILED) {
        documentsApi.markFailed(payload.documentVersionId(), "Procesamiento falló tras reintentos: " + e.getMessage());
      }
    }
  }
}
