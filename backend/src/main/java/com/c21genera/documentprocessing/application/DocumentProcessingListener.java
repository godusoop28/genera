package com.c21genera.documentprocessing.application;

import com.c21genera.shared.events.DocumentEvents.DocumentVersionUploaded;
import com.c21genera.shared.jobs.BackgroundJobQueue;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Arranca el pipeline asíncrono de calidad/normalización/PDF (ver AGENTS §34-37). */
@Component
class DocumentProcessingListener {

  static final String JOB_TYPE = "PROCESS_DOCUMENT_VERSION";

  private final BackgroundJobQueue queue;

  DocumentProcessingListener(BackgroundJobQueue queue) {
    this.queue = queue;
  }

  @ApplicationModuleListener
  void on(DocumentVersionUploaded event) {
    queue.enqueue(
        JOB_TYPE,
        new ProcessDocumentVersionPayload(
            event.expedienteId(), event.documentId(), event.documentVersionId(), event.type()));
  }
}
