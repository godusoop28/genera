package com.c21genera.documents;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** API pública del módulo documents, usada por documentprocessing/extraction/contracts/notifications. */
public interface DocumentsApi {

  List<PageView> pagesOf(UUID documentVersionId);

  void markProcessing(UUID documentVersionId);

  void markProcessed(UUID documentVersionId, String pdfStorageKey, String normalizedStorageKey);

  void markQualityFailed(UUID documentVersionId, String reason);

  void markFailed(UUID documentVersionId, String reason);

  /** Todo documento requerido tiene al menos una versión cargada (no implica que ya esté aceptado). */
  boolean allRequiredUploaded(UUID expedienteId);

  boolean allRequiredAccepted(UUID expedienteId);

  /**
   * PDF de la versión ACTUAL de un documento, solo si el documento ya está
   * ACCEPTED (ver AGENTS §46-47: notifications nunca adjunta versiones
   * rechazadas ni no autorizadas sin acción explícita). Vacío si no aplica.
   */
  Optional<String> currentAcceptedPdfStorageKey(UUID documentId);

  List<UUID> documentIdsOf(UUID expedienteId);

  /**
   * Documentos ya ACCEPTED de un expediente, con su tipo y el PDF de la
   * versión vigente. pdfStorageKey es null cuando el staff aceptó una
   * versión que nunca llegó a generar PDF (p. ej. anuló un QUALITY_FAILED).
   */
  List<AcceptedDocumentView> acceptedDocumentsOf(UUID expedienteId);

  record PageView(int pageNumber, String storageKeyOriginal, String mimeType) {}

  record AcceptedDocumentView(UUID documentId, String type, String pdfStorageKey) {}
}
