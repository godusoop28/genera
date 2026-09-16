package com.c21genera.shared.events;

import com.c21genera.shared.domain.DocumentTypeCode;
import com.c21genera.shared.domain.ReviewDecision;
import java.util.List;
import java.util.UUID;

/**
 * Eventos de integración publicados por documents (ver AGENTS §84). Viven
 * en shared por la misma razón que {@link ExpedienteEvents}: expedientes
 * escucha varios de estos y documents escucha eventos de expedientes,
 * evitando así una dependencia cíclica entre módulos (ver AGENTS §7).
 */
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

  /** Se publica cuando TODOS los documentos obligatorios tienen al menos una versión cargada. */
  public record AllRequiredDocumentsUploaded(UUID expedienteId) {}

  /** documentprocessing publica esto al terminar normalización/calidad/PDF; extraction escucha para OCR. */
  public record DocumentVersionProcessed(
      UUID expedienteId, UUID documentId, UUID documentVersionId, DocumentTypeCode type, String pdfStorageKey) {}
}
