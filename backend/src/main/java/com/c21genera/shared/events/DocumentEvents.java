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

  /**
   * documentprocessing escucha esto para arrancar el pipeline de calidad/OCR/PDF. actor: quién cargó el archivo.
   * previousStatus: estado del documento antes de esta carga (p. ej. RETURNED si es una corrección).
   * deferred: el asesor lo había dejado para que el cliente lo subiera después.
   */
  public record DocumentVersionUploaded(
      UUID expedienteId,
      UUID documentId,
      UUID documentVersionId,
      DocumentTypeCode type,
      List<PageRef> pages,
      UUID participantId,
      Actor actor,
      String previousStatus,
      boolean deferred) {}

  /**
   * expedientes escucha esto para decidir la transición de estado
   * correspondiente; audit y notifications lo usan para la bitácora y para
   * avisarle al cliente qué debe corregir. overrideJustification solo viene
   * cuando se aceptó un archivo marcado como ilegible o inconsistente.
   */
  public record DocumentReviewed(
      UUID expedienteId,
      UUID documentId,
      DocumentTypeCode type,
      ReviewDecision decision,
      UUID reviewedByUserId,
      Actor actor,
      String reasonCode,
      String comment,
      String overrideJustification) {}

  /** El staff marcó un requisito como "No aplica" (con justificación) o lo volvió a solicitar. */
  public record DocumentApplicabilityChanged(
      UUID expedienteId, UUID documentId, DocumentTypeCode type, boolean notApplicable, String justification, Actor actor) {}

  /** deferred=true: el cliente puede subirlo después; false: se pide desde la primera entrega. */
  public record DocumentDeferralChanged(UUID expedienteId, UUID documentId, DocumentTypeCode type, boolean deferred, Actor actor) {}

  /** Se publica cuando TODOS los documentos obligatorios de un expediente quedan ACCEPTED. */
  public record AllRequiredDocumentsApproved(UUID expedienteId) {}

  /** Se publica cuando TODOS los documentos obligatorios tienen al menos una versión cargada. */
  public record AllRequiredDocumentsUploaded(UUID expedienteId) {}

  /** documentprocessing publica esto al terminar normalización/calidad/PDF; extraction escucha para OCR. */
  public record DocumentVersionProcessed(
      UUID expedienteId, UUID documentId, UUID documentVersionId, DocumentTypeCode type, String pdfStorageKey) {}

  /** documentprocessing publica esto cuando el archivo no pasó la verificación automática de calidad. */
  public record DocumentQualityFailed(UUID expedienteId, UUID documentId, UUID documentVersionId, DocumentTypeCode type, String reason) {}

  /**
   * extraction publica esto tras revisar el contenido con IA: si el archivo
   * parece corresponder al documento solicitado y si es legible. null
   * significa "no se pudo determinar" (p. ej. IA deshabilitada o sin
   * respuesta), nunca "sí".
   */
  public record DocumentContentAssessed(
      UUID expedienteId,
      UUID documentId,
      UUID documentVersionId,
      DocumentTypeCode type,
      Boolean matchesExpectedType,
      Boolean legible,
      String detectedDocumentKind,
      String observations,
      /* La IA no respondió después de todos los reintentos: el contenido quedó sin verificar. */
      boolean checkFailed,
      /* Avisos para el revisor (p. ej. "solo se ve el frente"); nunca bloquean. */
      List<String> warnings,
      Integer pagesAnalyzed,
      Integer pagesTotal,
      Integer fieldsExpected,
      Integer fieldsFound) {

    public DocumentContentAssessed {
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
  }

  /**
   * El staff pidió volver a procesar una versión ya cargada sin que el cliente
   * la suba de nuevo. pdfStorageKey null = desde los archivos originales
   * (calidad, PDF y extracción); con PDF = solo repetir la extracción con IA.
   */
  public record DocumentReprocessRequested(
      UUID expedienteId, UUID documentId, UUID documentVersionId, DocumentTypeCode type, String pdfStorageKey, Actor actor) {}

  /** El staff movió el archivo cargado en un requisito a otro (p. ej. un predial cargado como acta de matrimonio). */
  public record DocumentFileMoved(
      UUID expedienteId, UUID fromDocumentId, DocumentTypeCode fromType, UUID toDocumentId, DocumentTypeCode toType, Actor actor) {}

  /** Confirmación explícita de staff (ver AGENTS §87); expedientes escucha para transicionar a RECEPTION_SIGNED. */
  public record ReceptionSigned(UUID expedienteId, UUID signedByUserId, Actor actor) {}

  /**
   * Tras recalcular requisitos (p. ej. se agregó un copropietario), hay al
   * menos un documento obligatorio que todavía no está aceptado: si el
   * expediente ya había aprobado su documentación, debe volver a correcciones.
   */
  public record RequiredDocumentsReopened(UUID expedienteId) {}
}
