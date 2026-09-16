package com.c21genera.documents;

import com.c21genera.documents.domain.ReviewDecision;
import com.c21genera.shared.domain.DocumentTypeCode;
import java.util.List;
import java.util.UUID;

public final class DocumentEvents {

  private DocumentEvents() {}

  public record PageRef(int pageNumber, String storageKeyOriginal, String mimeType) {}

  /** documentprocessing escucha esto para arrancar el pipeline de calidad/OCR/PDF. */
  public record DocumentVersionUploaded(
      UUID expedienteId, UUID documentId, UUID documentVersionId, DocumentTypeCode type, List<PageRef> pages) {}

  /** expedientes escucha esto para decidir la transición de estado correspondiente. */
  public record DocumentReviewed(UUID expedienteId, UUID documentId, ReviewDecision decision) {}

  /** Se publica cuando TODOS los documentos obligatorios de un expediente quedan ACCEPTED. */
  public record AllRequiredDocumentsApproved(UUID expedienteId) {}

  public record DocumentsSubmitted(UUID expedienteId) {}
}
