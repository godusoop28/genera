package com.c21genera.documentprocessing.domain;

import com.c21genera.shared.domain.DocumentTypeCode;
import java.util.List;

/**
 * Verificación determinística de calidad de una imagen normalizada (ver
 * AGENTS §36). Filosofía: aceptar y extraer todo lo posible. Solo una imagen
 * que de verdad no sirve (vacía, en blanco, negra, diminuta o tan borrosa que
 * no se distingue texto) es {@link QualityLevel#UNREADABLE}; lo demás que no
 * es ideal (oscura, algo borrosa, resolución baja) es una advertencia que se
 * le muestra al revisor y nunca detiene el PDF ni la extracción. La
 * orientación, la proporción, los márgenes o el fondo nunca se evalúan: un
 * documento se lee igual de lado o con la mesa alrededor.
 */
public interface DocumentQualityAnalyzer {

  QualityResult analyze(byte[] normalizedImage, String mimeType, int widthPx, int heightPx, DocumentTypeCode documentType);

  enum QualityLevel {
    ACCEPTED,
    ACCEPTED_WITH_WARNINGS,
    UNREADABLE
  }

  /** issues: motivos del rechazo si es UNREADABLE; advertencias si es ACCEPTED_WITH_WARNINGS. */
  record QualityResult(QualityLevel level, List<String> issues) {

    public QualityResult {
      issues = issues == null ? List.of() : List.copyOf(issues);
    }

    /** Se puede seguir con el PDF y la extracción (con o sin advertencias). */
    public boolean acceptable() {
      return level != QualityLevel.UNREADABLE;
    }

    public static QualityResult ok() {
      return new QualityResult(QualityLevel.ACCEPTED, List.of());
    }

    public static QualityResult withWarnings(List<String> warnings) {
      return warnings.isEmpty() ? ok() : new QualityResult(QualityLevel.ACCEPTED_WITH_WARNINGS, warnings);
    }

    public static QualityResult unreadable(List<String> issues) {
      return new QualityResult(QualityLevel.UNREADABLE, issues);
    }
  }
}
