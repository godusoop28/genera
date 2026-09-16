package com.c21genera.documents;

import java.util.List;
import java.util.UUID;

/** API pública del módulo documents, usada por documentprocessing/extraction/contracts. */
public interface DocumentsApi {

  List<PageView> pagesOf(UUID documentVersionId);

  void markProcessing(UUID documentVersionId);

  void markProcessed(UUID documentVersionId, String pdfStorageKey, String normalizedStorageKey);

  void markQualityFailed(UUID documentVersionId, String reason);

  void markFailed(UUID documentVersionId, String reason);

  /** Todo documento requerido tiene al menos una versión cargada (no implica que ya esté aceptado). */
  boolean allRequiredUploaded(UUID expedienteId);

  boolean allRequiredAccepted(UUID expedienteId);

  record PageView(int pageNumber, String storageKeyOriginal, String mimeType) {}
}
