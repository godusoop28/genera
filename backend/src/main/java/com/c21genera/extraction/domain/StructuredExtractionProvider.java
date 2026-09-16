package com.c21genera.extraction.domain;

import com.c21genera.shared.domain.DocumentTypeCode;
import java.util.List;

/**
 * Puerto de extracción estructurada de campos a partir de un PDF ya
 * normalizado (ver AGENTS §38-39). Los documentos son contenido NO
 * confiable: cualquier implementación real debe tratar su texto/imagen como
 * datos, nunca como instrucciones (protección contra inyección de prompts).
 */
public interface StructuredExtractionProvider {

  ExtractionResult extract(DocumentTypeCode type, byte[] pdfBytes, List<String> fieldNames);

  record FieldResult(String fieldName, String value, double confidence) {}

  record ExtractionResult(List<FieldResult> fields, List<String> warnings) {

    public static ExtractionResult empty() {
      return new ExtractionResult(List.of(), List.of());
    }
  }
}
