package com.c21genera.documents.application;

import com.c21genera.documents.ProcessingStatus;
import com.c21genera.documents.domain.Document;
import com.c21genera.documents.domain.DocumentVersion;
import com.c21genera.documents.infrastructure.DocumentRepository;
import com.c21genera.documents.infrastructure.DocumentVersionRepository;
import com.c21genera.shared.events.Actor;
import com.c21genera.shared.events.DocumentEvents.DocumentReprocessRequested;
import com.c21genera.shared.jobs.BackgroundJobRepository;
import com.c21genera.shared.jobs.JobStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Red de seguridad para que ningún documento se quede "procesando" o "IA analizando" para siempre
 * (04/11: un documento de un cliente no terminaba de leerse con IA).
 *
 * <p>Cada pocos minutos busca versiones vigentes que llevan más de {@link #GRACE} esperando y que no
 * tienen ningún trabajo en cola ni en curso: su evento se perdió (reinicio o despliegue a media
 * operación) o el trabajo terminó sin dejar resultado. Esas se vuelven a lanzar. Si ya se lanzaron
 * {@link #MAX_JOBS} veces, se cierran: el procesamiento como fallido y la revisión con IA como "no
 * se pudo revisar", para que el staff lo vea, lo reprocese o capture los datos a mano en vez de
 * esperar indefinidamente.
 */
@Component
class StuckDocumentWatchdog {

  private static final Logger log = LoggerFactory.getLogger(StuckDocumentWatchdog.class);

  /** Mismos nombres que en documentprocessing y extraction (el módulo documents no depende de ellos). */
  static final String PROCESS_JOB = "PROCESS_DOCUMENT_VERSION";

  static final String EXTRACT_JOB = "EXTRACT_DOCUMENT_FIELDS";

  /** Holgado frente a un procesamiento normal (segundos) y a una lectura con IA larga con reintentos. */
  static final Duration GRACE = Duration.ofMinutes(15);

  /** Solo se revisan cargas recientes: lo anterior ya lo atendió el staff. */
  static final Duration LOOK_BACK = Duration.ofDays(30);

  /** Trabajos por versión y etapa antes de darla por cerrada (el original y hasta dos rescates). */
  static final int MAX_JOBS = 3;

  private static final List<JobStatus> ACTIVE = List.of(JobStatus.QUEUED, JobStatus.PROCESSING);

  private final DocumentVersionRepository versions;
  private final DocumentRepository documents;
  private final BackgroundJobRepository jobs;
  private final DocumentService documentService;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  StuckDocumentWatchdog(
      DocumentVersionRepository versions,
      DocumentRepository documents,
      BackgroundJobRepository jobs,
      DocumentService documentService,
      ApplicationEventPublisher events,
      Clock clock) {
    this.versions = versions;
    this.documents = documents;
    this.jobs = jobs;
    this.documentService = documentService;
    this.events = events;
    this.clock = clock;
  }

  @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT3M")
  @Transactional
  public void sweep() {
    Instant now = clock.instant();
    List<DocumentVersion> waiting =
        versions.findWaiting(
            now.minus(LOOK_BACK), now.minus(GRACE), List.of(ProcessingStatus.QUEUED, ProcessingStatus.PROCESSING), ProcessingStatus.PROCESSED);
    for (DocumentVersion version : waiting) {
      try {
        rescue(version, now);
      } catch (RuntimeException e) {
        log.warn("No se pudo rescatar la versión {}", version.getId(), e);
      }
    }
  }

  void rescue(DocumentVersion version, Instant now) {
    Optional<Document> document = documents.findById(version.getDocumentId());
    if (document.isEmpty() || !isCurrent(version)) {
      return; // Solo importa la versión vigente: las anteriores no se muestran.
    }
    boolean processing = version.getProcessingStatus() != ProcessingStatus.PROCESSED;
    String jobType = processing ? PROCESS_JOB : EXTRACT_JOB;
    String needle = "\"documentVersionId\":\"" + version.getId() + "\"";
    if (jobs.existsByTypeAndStatusInAndPayloadContaining(jobType, ACTIVE, needle)) {
      return; // Sigue en marcha; si su proceso murió, la cola lo recupera sola.
    }
    long launched = jobs.countByTypeAndPayloadContaining(jobType, needle);
    if (launched >= MAX_JOBS) {
      giveUp(version, processing, now);
      return;
    }
    if (!processing && version.getStorageKeyPdf() == null) {
      giveUp(version, false, now);
      return;
    }
    log.warn(
        "Documento atorado: versión {} ({}) sin trabajo activo; se vuelve a lanzar {} (intento {} de {})",
        version.getId(),
        version.getProcessingStatus(),
        jobType,
        launched + 1,
        MAX_JOBS);
    Document doc = document.get();
    events.publishEvent(
        new DocumentReprocessRequested(
            doc.getExpedienteId(), doc.getId(), version.getId(), doc.getType(), processing ? null : version.getStorageKeyPdf(), Actor.system()));
  }

  private boolean isCurrent(DocumentVersion version) {
    return versions.findByDocumentIdAndRemovedAtIsNullOrderByVersionNumberDesc(version.getDocumentId()).stream()
        .findFirst()
        .map(v -> v.getId().equals(version.getId()))
        .orElse(false);
  }

  private void giveUp(DocumentVersion version, boolean processing, Instant now) {
    log.warn("Documento atorado: versión {} se cierra tras {} trabajos sin resultado", version.getId(), MAX_JOBS);
    if (processing) {
      documentService.markFailed(
          version.getId(), "No se pudo terminar de procesar el archivo tras varios intentos; vuelve a subirlo o reprocésalo");
    } else {
      version.recordAiAssessment(
          null,
          null,
          null,
          null,
          true,
          now,
          new DocumentVersion.AiDetails(
              List.of("La IA no terminó de leer este archivo tras varios intentos: reprocésalo con IA o captura los datos a mano"),
              null,
              null,
              null,
              null));
    }
  }
}
