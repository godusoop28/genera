package com.c21genera.extraction.application;

import com.c21genera.shared.jobs.BackgroundJob;
import com.c21genera.shared.jobs.BackgroundJobQueue;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Consumidor del job EXTRACT_DOCUMENT_FIELDS. A diferencia de
 * documentprocessing, un fallo aquí nunca bloquea la revisión manual del
 * documento (ver AGENTS §38: la extracción es información de apoyo, el
 * staff decide independientemente).
 */
@Component
public class ExtractionWorker {

  private static final Logger log = LoggerFactory.getLogger(ExtractionWorker.class);
  private static final int BATCH_SIZE = 5;
  private static final String WORKER_ID = "extraction-worker";

  private final BackgroundJobQueue queue;
  private final DocumentFieldExtractionService service;

  public ExtractionWorker(BackgroundJobQueue queue, DocumentFieldExtractionService service) {
    this.queue = queue;
    this.service = service;
  }

  @Scheduled(fixedDelayString = "PT7S", initialDelayString = "PT15S")
  public void pollAndProcess() {
    List<BackgroundJob> jobs = queue.claimBatch(ExtractionListener.JOB_TYPE, WORKER_ID, BATCH_SIZE);
    for (BackgroundJob job : jobs) {
      handle(job);
    }
  }

  private void handle(BackgroundJob job) {
    ExtractDocumentFieldsPayload payload = queue.readPayload(job, ExtractDocumentFieldsPayload.class);
    try {
      service.extract(payload);
      queue.markDone(job);
    } catch (Exception e) {
      log.error("Fallo extrayendo campos para documentVersionId={}", payload.documentVersionId(), e);
      queue.markFailedOrRetry(job, e.getMessage());
    }
  }
}
