package com.c21genera.extraction.domain;

import com.c21genera.shared.domain.DocumentTypeCode;
import java.util.List;

/**
 * Puerto de extracción estructurada de campos a partir de un PDF ya
 * normalizado (ver AGENTS §38-39). Los documentos son contenido NO
 * confiable: cualquier implementación real debe tratar su texto/imagen como
 * datos, nunca como instrucciones (protección contra inyección de prompts).
 *
 * <p>Prioridad: EXTRAER. Una extracción parcial es válida, un campo con
 * confianza baja se conserva (significa "revisar", no "descartar"), y si el
 * archivo parece otro documento distinto al solicitado los datos leídos se
 * conservan igual: solo se marca para el revisor.
 */
public interface StructuredExtractionProvider {

  /**
   * @throws AiUnavailableException si el servicio no respondió (caída, tiempo
   *     de espera, límite de uso): quien llama decide reintentar o marcar el
   *     documento como "sin revisión automática", nunca como aprobado.
   */
  ExtractionResult extract(DocumentTypeCode type, byte[] pdfBytes, List<String> fieldNames);

  /** El proveedor de IA no respondió o respondió algo inutilizable. */
  class AiUnavailableException extends RuntimeException {
    public AiUnavailableException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /** page: página del documento (1 = primera) donde se leyó el dato; null si no se sabe. */
  record FieldResult(String fieldName, String value, double confidence, Integer page) {

    public FieldResult(String fieldName, String value, double confidence) {
      this(fieldName, value, confidence, null);
    }
  }

  /**
   * ¿El archivo es realmente el documento solicitado y se puede leer? null en
   * cualquier campo significa "no se pudo determinar" (nunca "sí"): la
   * ausencia de revisión no debe interpretarse como aprobación.
   */
  record ContentAssessment(Boolean matchesExpectedType, Boolean legible, String detectedDocumentKind, String observations) {

    public static ContentAssessment unknown() {
      return new ContentAssessment(null, null, null, null);
    }
  }

  /**
   * warnings: avisos para el revisor (nunca bloquean). pagesAnalyzed /
   * pagesTotal: cuántas páginas del archivo se revisaron (null si no aplica).
   */
  record ExtractionResult(
      List<FieldResult> fields, ContentAssessment assessment, List<String> warnings, Integer pagesAnalyzed, Integer pagesTotal) {

    public ExtractionResult {
      fields = fields == null ? List.of() : List.copyOf(fields);
      warnings = warnings == null ? List.of() : List.copyOf(warnings);
      assessment = assessment == null ? ContentAssessment.unknown() : assessment;
    }

    public ExtractionResult(List<FieldResult> fields, ContentAssessment assessment, List<String> warnings) {
      this(fields, assessment, warnings, null, null);
    }

    public static ExtractionResult empty() {
      return new ExtractionResult(List.of(), ContentAssessment.unknown(), List.of());
    }
  }
}
