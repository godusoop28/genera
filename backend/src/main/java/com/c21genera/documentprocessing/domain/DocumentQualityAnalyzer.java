package com.c21genera.documentprocessing.domain;

import com.c21genera.shared.domain.DocumentTypeCode;
import java.util.List;

/**
 * Verificación determinística de calidad de una imagen normalizada (ver
 * AGENTS §36): resolución mínima, relación de aspecto razonable, orientación
 * vertical (salvo tipos de documento que son naturalmente horizontales, como
 * una identificación tipo tarjeta). No es un análisis de contenido ni usa IA;
 * eso lo hace extraction/AI por separado.
 */
public interface DocumentQualityAnalyzer {

  QualityResult analyze(byte[] normalizedImage, String mimeType, int widthPx, int heightPx, DocumentTypeCode documentType);

  record QualityResult(boolean acceptable, List<String> issues) {

    public static QualityResult ok() {
      return new QualityResult(true, List.of());
    }

    public static QualityResult rejected(List<String> issues) {
      return new QualityResult(false, issues);
    }
  }
}
