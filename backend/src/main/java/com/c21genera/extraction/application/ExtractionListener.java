package com.c21genera.extraction.application;

import com.c21genera.shared.events.DocumentEvents.DocumentVersionProcessed;
import com.c21genera.shared.jobs.BackgroundJobQueue;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** Arranca la extracción de campos en cuanto documentprocessing entrega un PDF listo (ver AGENTS §38). */
@Component
class ExtractionListener {

  static final String JOB_TYPE = "EXTRACT_DOCUMENT_FIELDS";

  private final BackgroundJobQueue queue;

  ExtractionListener(BackgroundJobQueue queue) {
    this.queue = queue;
  }

  @ApplicationModuleListener
  void on(DocumentVersionProcessed event) {
    queue.enqueue(
        JOB_TYPE,
        new ExtractDocumentFieldsPayload(
            event.expedienteId(), event.documentId(), event.documentVersionId(), event.type(), event.pdfStorageKey()));
  }
}
