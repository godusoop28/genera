package com.c21genera.extraction.application;

import com.c21genera.shared.jobs.BackgroundJob;
import com.c21genera.shared.jobs.BackgroundJobQueue;
import com.c21genera.shared.jobs.JobStatus;
import com.c21genera.shared.jobs.MemoryRelief;
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
  // De uno en uno: el candado del job refleja cuándo empezó de verdad, y si el
  // servidor se reinicia a medias solo queda abandonado ese job (se recupera solo).
  private static final int BATCH_SIZE = 1;
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
    } catch (Exception | OutOfMemoryError e) {
      // OutOfMemoryError: un documento que no cabe en memoria falla solo él (con reintentos), en vez de
      // dejar el servidor inestable (E2E 02/10).
      log.error("Fallo extrayendo campos para documentVersionId={}", payload.documentVersionId(), e);
      String message = e instanceof OutOfMemoryError ? "El documento es demasiado grande para analizarlo con la memoria disponible" : e.getMessage();
      if (queue.markFailedOrRetry(job, message) == JobStatus.FAILED) {
        service.recordCheckFailed(payload);
      }
    } finally {
      MemoryRelief.afterHeavyWork();
    }
  }
}
