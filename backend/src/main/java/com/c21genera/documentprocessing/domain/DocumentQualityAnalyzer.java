package com.c21genera.documentprocessing.domain;

import java.util.List;

/**
 * Verificación determinística de calidad de una imagen normalizada (ver
 * AGENTS §36): resolución mínima, relación de aspecto razonable. No es un
 * análisis de contenido ni usa IA; eso lo hace extraction/AI por separado.
 */
public interface DocumentQualityAnalyzer {

  QualityResult analyze(byte[] normalizedImage, String mimeType, int widthPx, int heightPx);

  record QualityResult(boolean acceptable, List<String> issues) {

    public static QualityResult ok() {
      return new QualityResult(true, List.of());
    }

    public static QualityResult rejected(List<String> issues) {
      return new QualityResult(false, issues);
    }
  }
}
