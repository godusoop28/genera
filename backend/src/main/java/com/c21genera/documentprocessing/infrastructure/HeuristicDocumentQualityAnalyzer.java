package com.c21genera.documentprocessing.infrastructure;

import com.c21genera.documentprocessing.domain.DocumentQualityAnalyzer;
import com.c21genera.shared.domain.DocumentTypeCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * Chequeos determinísticos de calidad (ver AGENTS §36): resolución mínima,
 * relación de aspecto razonable, orientación vertical y nitidez (varianza
 * del filtro Laplaciano) para una fotografía de documento. No intenta
 * entender el contenido: eso queda fuera del alcance del Módulo 1.
 */
@Component
public class HeuristicDocumentQualityAnalyzer implements DocumentQualityAnalyzer {

  private static final int MIN_DIMENSION_PX = 500;
  private static final double MAX_ASPECT_RATIO = 6.0;

  // Calibrado con BlurDetectorTest: una foto de documento nítida con texto
  // denso da ~11 000, una muy borrosa (desenfoque gaussiano fuerte
  // simulado) da ~340. Se deja el umbral bajo a propósito para esta primera
  // versión (sin fotos reales con las que calibrar todavía): prioriza no
  // rechazar fotos legítimas sobre atrapar todo el desenfoque leve. El
  // staff siempre puede aceptar a mano un documento marcado como borroso si
  // a simple vista se ve bien.
  private static final double MIN_SHARPNESS_VARIANCE = 150.0;

  // Una imagen en blanco, gris uniforme o sin contraste (p. ej. foto de una
  // pared, tapada o totalmente sobreexpuesta) tiene una desviación del brillo
  // casi nula; cualquier documento con texto visible queda muy por encima.
  private static final double MIN_LUMINANCE_STDDEV = 10.0;

  // Foto prácticamente negra (lente tapada, sin luz).
  private static final double MIN_MEAN_LUMINANCE = 30.0;

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
    } else {
      BufferedImage image = decode(normalizedImage);
      if (image != null) {
        BlurDetector.Luminance luminance = BlurDetector.luminance(image);
        if (luminance.standardDeviation() < MIN_LUMINANCE_STDDEV) {
          // Si no hay contraste tampoco tiene sentido reportarla además como "borrosa".
          issues.add("La imagen está en blanco, gris o sin contraste: no se distingue ningún documento. Vuelve a tomar la foto");
        } else if (luminance.mean() < MIN_MEAN_LUMINANCE) {
          issues.add("La foto está demasiado oscura: tómala con mejor iluminación");
        } else if (BlurDetector.laplacianVariance(image) < MIN_SHARPNESS_VARIANCE) {
          issues.add("La foto está borrosa o movida, vuelve a tomarla con buen enfoque e iluminación");
        }
      }
    }

    return issues.isEmpty() ? QualityResult.ok() : QualityResult.rejected(issues);
  }

  /** null si la imagen no se pudo decodificar (no debería pasar: ya es un JPEG normalizado válido). */
  private static BufferedImage decode(byte[] normalizedImage) {
    try {
      return ImageIO.read(new ByteArrayInputStream(normalizedImage));
    } catch (Exception e) {
      return null;
    }
  }
}
