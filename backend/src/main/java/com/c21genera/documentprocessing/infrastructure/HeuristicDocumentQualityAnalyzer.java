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
 * Chequeos determinísticos de calidad (ver AGENTS §36), en tres niveles:
 * <ul>
 *   <li>UNREADABLE: solo lo que no se puede usar para nada (archivo vacío o
 *       que no se decodifica, imagen en blanco/gris uniforme, prácticamente
 *       negra, diminuta, o tan borrosa que no queda ningún borde de letra).</li>
 *   <li>ACCEPTED_WITH_WARNINGS: se lee pero no es ideal (algo borrosa,
 *       oscura, sobreexpuesta, resolución baja). Se le avisa al revisor.</li>
 *   <li>ACCEPTED: todo lo demás, incluida una foto horizontal, inclinada,
 *       con márgenes, fondo o sombras.</li>
 * </ul>
 * Si el contenido se lee lo decide la revisión con IA, que ve la imagen real.
 */
@Component
public class HeuristicDocumentQualityAnalyzer implements DocumentQualityAnalyzer {

  // Por debajo de esto no se distingue texto de documento alguno.
  private static final int UNREADABLE_DIMENSION_PX = 250;
  // Se lee, pero el texto pequeño (CURP, folios) puede costar trabajo.
  private static final int LOW_RESOLUTION_PX = 700;

  // Calibrado con BlurDetectorTest: una foto de documento nítida con texto
  // denso da ~11 000, una muy borrosa ~340; una imagen reducida a 1/16 y
  // vuelta a ampliar (texto convertido en manchas) queda por debajo de 25.
  private static final double UNREADABLE_SHARPNESS = 25.0;
  private static final double SOFT_SHARPNESS = 120.0;

  // Una imagen en blanco, gris uniforme o sin contraste (p. ej. foto de una
  // pared, tapada o totalmente sobreexpuesta) tiene una desviación del brillo
  // casi nula; cualquier documento con texto visible queda muy por encima.
  private static final double UNREADABLE_CONTRAST = 6.0;
  private static final double LOW_CONTRAST = 18.0;

  private static final double UNREADABLE_DARKNESS = 20.0;
  private static final double DARK = 60.0;
  private static final double OVEREXPOSED = 240.0;

  @Override
  public QualityResult analyze(byte[] normalizedImage, String mimeType, int widthPx, int heightPx, DocumentTypeCode documentType) {
    if (normalizedImage == null || normalizedImage.length == 0) {
      return QualityResult.unreadable(List.of("El archivo de imagen está vacío"));
    }
    int shortSide = Math.min(widthPx, heightPx);
    if (shortSide < UNREADABLE_DIMENSION_PX) {
      return QualityResult.unreadable(
          List.of("La imagen es demasiado pequeña (%dx%d px): sube una foto más cercana o de mayor resolución".formatted(widthPx, heightPx)));
    }

    List<String> warnings = new ArrayList<>();
    if (shortSide < LOW_RESOLUTION_PX) {
      warnings.add("Resolución baja (%dx%d px): verifica que los datos pequeños se lean".formatted(widthPx, heightPx));
    }

    BufferedImage image = decode(normalizedImage);
    if (image == null) {
      // Ya es un JPEG normalizado; si aun así no se decodifica, que lo juzgue la IA y el revisor.
      warnings.add("No se pudo medir la calidad de la imagen");
      return QualityResult.withWarnings(warnings);
    }

    BlurDetector.Luminance luminance = BlurDetector.luminance(image);
    if (luminance.standardDeviation() < UNREADABLE_CONTRAST) {
      return QualityResult.unreadable(
          List.of("La imagen está en blanco, gris o sin contraste: no se distingue ningún documento. Vuelve a tomar la foto"));
    }
    if (luminance.mean() < UNREADABLE_DARKNESS) {
      return QualityResult.unreadable(List.of("La foto está prácticamente negra: tómala con luz"));
    }
    double sharpness = BlurDetector.laplacianVariance(image);
    if (sharpness < UNREADABLE_SHARPNESS) {
      return QualityResult.unreadable(List.of("La foto está tan borrosa que no se distingue el texto: vuelve a tomarla con buen enfoque"));
    }

    if (sharpness < SOFT_SHARPNESS) {
      warnings.add("La foto está algo borrosa: confirma que los datos se lean bien");
    }
    if (luminance.mean() < DARK) {
      warnings.add("La foto está oscura");
    } else if (luminance.mean() > OVEREXPOSED && luminance.standardDeviation() < 2 * LOW_CONTRAST) {
      // Una hoja blanca bien escaneada también es muy clara: solo es "reflejo" si además casi no hay contraste.
      warnings.add("La foto está muy clara o con reflejo");
    }
    if (luminance.standardDeviation() < LOW_CONTRAST) {
      warnings.add("La foto tiene poco contraste");
    }
    return QualityResult.withWarnings(warnings);
  }

  /** null si la imagen no se pudo decodificar. */
  private static BufferedImage decode(byte[] normalizedImage) {
    try {
      return ImageIO.read(new ByteArrayInputStream(normalizedImage));
    } catch (Exception e) {
      return null;
    }
  }
}
