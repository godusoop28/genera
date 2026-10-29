package com.c21genera.extraction.application;

import com.c21genera.shared.config.CoreConfig;
import com.c21genera.shared.jobs.BackgroundJob;
import com.c21genera.shared.jobs.BackgroundJobQueue;
import com.c21genera.shared.jobs.JobStatus;
import com.c21genera.shared.jobs.MemoryRelief;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
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
  private static final String WORKER_ID = "extraction-worker";

  /**
   * Lecturas con IA a la vez. Casi todo el tiempo de una lectura es esperar la respuesta de OpenAI
   * (decenas de segundos), que no ocupa memoria; la parte pesada (renderizar y preparar las
   * imágenes) sigue siendo de una en una gracias a {@code HeavyImageWork}. Antes iban de una en una
   * y diez documentos cargados juntos tardaban diez veces lo de uno (29/10).
   */
  static final int PARALLEL_JOBS = 3;

  private final BackgroundJobQueue queue;
  private final DocumentFieldExtractionService service;
  private final ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_JOBS, CoreConfig.backgroundThreads("ai-extraction-"));
  private final AtomicInteger running = new AtomicInteger();

  public ExtractionWorker(BackgroundJobQueue queue, DocumentFieldExtractionService service) {
    this.queue = queue;
    this.service = service;
  }

  /** Toma tantos jobs como lugares libres haya; el candado de cada job se pone justo al empezar. */
  @Scheduled(fixedDelayString = "PT3S", initialDelayString = "PT15S")
  public void pollAndProcess() {
    int free = PARALLEL_JOBS - running.get();
    if (free <= 0) {
      return;
    }
    for (BackgroundJob job : queue.claimBatch(ExtractionListener.JOB_TYPE, WORKER_ID, free)) {
      running.incrementAndGet();
      executor.execute(
          () -> {
            try {
              handle(job);
            } finally {
              running.decrementAndGet();
            }
          });
    }
  }

  @PreDestroy
  void stop() {
    executor.shutdownNow();
  }

  private void handle(BackgroundJob job) {
    ExtractDocumentFieldsPayload payload = queue.readPayload(job, ExtractDocumentFieldsPayload.class);
    try {
      if (job.exceededAttempts()) {
        throw new IllegalStateException("El servidor se reinició varias veces analizando este archivo");
      }
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
