package com.c21genera.documentprocessing.infrastructure;

import com.c21genera.documentprocessing.domain.DocumentQualityAnalyzer;
import com.c21genera.shared.domain.DocumentTypeCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * Chequeos determinísticos de calidad (ver AGENTS §36). Deliberadamente
 * permisivos: solo rechazan una imagen que no sirve para nada (vacía, en
 * blanco/gris, casi negra, diminuta o extremadamente borrosa). Si el
 * documento se lee, lo decide la revisión con IA, que ve la imagen real;
 * rechazar aquí por posición, proporción o desenfoque leve devolvía al
 * cliente fotos que la IA sí podía leer.
 */
@Component
public class HeuristicDocumentQualityAnalyzer implements DocumentQualityAnalyzer {

  // Por debajo de esto no se distingue texto de documento alguno.
  private static final int MIN_DIMENSION_PX = 250;

  // Calibrado con BlurDetectorTest: una foto de documento nítida con texto
  // denso da ~11 000 y una muy borrosa ~340. Solo se rechaza lo
  // prácticamente ilegible; el desenfoque leve lo evalúa la IA.
  private static final double MIN_SHARPNESS_VARIANCE = 25.0;

  // Una imagen en blanco, gris uniforme o sin contraste (p. ej. foto de una
  // pared, tapada o totalmente sobreexpuesta) tiene una desviación del brillo
  // casi nula; cualquier documento con texto visible queda muy por encima.
  private static final double MIN_LUMINANCE_STDDEV = 6.0;

  // Foto prácticamente negra (lente tapada, sin luz).
  private static final double MIN_MEAN_LUMINANCE = 20.0;

  @Override
  public QualityResult analyze(byte[] normalizedImage, String mimeType, int widthPx, int heightPx, DocumentTypeCode documentType) {
    List<String> issues = new ArrayList<>();

    if (widthPx < MIN_DIMENSION_PX || heightPx < MIN_DIMENSION_PX) {
      issues.add("La imagen es demasiado pequeña (%dx%d px): sube una foto más cercana o de mayor resolución".formatted(widthPx, heightPx));
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
          issues.add("La foto está muy borrosa, vuelve a tomarla con buen enfoque");
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
