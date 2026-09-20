package com.c21genera.documentprocessing.infrastructure;

import com.c21genera.documentprocessing.domain.DocumentQualityAnalyzer;
import com.c21genera.shared.domain.DocumentTypeCode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Chequeos determinísticos de calidad (ver AGENTS §36): resolución mínima,
 * relación de aspecto razonable y orientación vertical para una fotografía
 * de documento. No intenta detectar borrosidad ni contenido: eso queda fuera
 * del alcance del Módulo 1.
 */
@Component
public class HeuristicDocumentQualityAnalyzer implements DocumentQualityAnalyzer {

  private static final int MIN_DIMENSION_PX = 500;
  private static final double MAX_ASPECT_RATIO = 6.0;

  /** Tipos cuyo original físico es naturalmente horizontal (tarjeta/credencial o plano). */
  private static final Set<DocumentTypeCode> LANDSCAPE_ALLOWED_TYPES =
      EnumSet.of(DocumentTypeCode.INE, DocumentTypeCode.PASSPORT, DocumentTypeCode.CADASTRAL_PLAN);

  @Override
  public QualityResult analyze(byte[] normalizedImage, String mimeType, int widthPx, int heightPx, DocumentTypeCode documentType) {
    List<String> issues = new ArrayList<>();

    if (widthPx < MIN_DIMENSION_PX || heightPx < MIN_DIMENSION_PX) {
      issues.add("Resolución insuficiente (%dx%d px, mínimo %dx%d px)".formatted(widthPx, heightPx, MIN_DIMENSION_PX, MIN_DIMENSION_PX));
    }

    double aspectRatio = (double) Math.max(widthPx, heightPx) / Math.min(widthPx, heightPx);
    if (aspectRatio > MAX_ASPECT_RATIO) {
      issues.add("Relación de aspecto inusual (%.1f:1)".formatted(aspectRatio));
    }

    if (widthPx > heightPx && !LANDSCAPE_ALLOWED_TYPES.contains(documentType)) {
      issues.add("La foto debe tomarse en posición vertical (el documento salió acostado)");
    }

    if (normalizedImage.length == 0) {
      issues.add("Archivo de imagen vacío");
    }

    return issues.isEmpty() ? QualityResult.ok() : QualityResult.rejected(issues);
  }
}
