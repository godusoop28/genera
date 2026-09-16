package com.c21genera.documentprocessing.infrastructure;

import com.c21genera.documentprocessing.domain.DocumentQualityAnalyzer;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Chequeos determinísticos de calidad (ver AGENTS §36): resolución mínima y
 * relación de aspecto razonable para una fotografía de documento. No intenta
 * detectar borrosidad ni contenido: eso queda fuera del alcance del Módulo 1.
 */
@Component
public class HeuristicDocumentQualityAnalyzer implements DocumentQualityAnalyzer {

  private static final int MIN_DIMENSION_PX = 500;
  private static final double MAX_ASPECT_RATIO = 6.0;

  @Override
  public QualityResult analyze(byte[] normalizedImage, String mimeType, int widthPx, int heightPx) {
    List<String> issues = new ArrayList<>();

    if (widthPx < MIN_DIMENSION_PX || heightPx < MIN_DIMENSION_PX) {
      issues.add("Resolución insuficiente (%dx%d px, mínimo %dx%d px)".formatted(widthPx, heightPx, MIN_DIMENSION_PX, MIN_DIMENSION_PX));
    }

    double aspectRatio = (double) Math.max(widthPx, heightPx) / Math.min(widthPx, heightPx);
    if (aspectRatio > MAX_ASPECT_RATIO) {
      issues.add("Relación de aspecto inusual (%.1f:1)".formatted(aspectRatio));
    }

    if (normalizedImage.length == 0) {
      issues.add("Archivo de imagen vacío");
    }

    return issues.isEmpty() ? QualityResult.ok() : QualityResult.rejected(issues);
  }
}
