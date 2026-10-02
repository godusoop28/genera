package com.c21genera.documentprocessing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.documentprocessing.domain.DocumentQualityAnalyzer.QualityLevel;
import com.c21genera.documentprocessing.domain.DocumentQualityAnalyzer.QualityResult;
import com.c21genera.shared.domain.DocumentTypeCode;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Filosofía: aceptar y extraer todo lo posible. Solo es UNREADABLE lo que no
 * sirve para nada; lo que no es ideal es una advertencia que nunca detiene la
 * extracción, y la orientación o la proporción nunca se evalúan.
 */
class HeuristicDocumentQualityAnalyzerTest {

  private final HeuristicDocumentQualityAnalyzer analyzer = new HeuristicDocumentQualityAnalyzer();

  @Test
  void acceptsAVerticalPhoto() throws IOException {
    QualityResult result = analyze(documentLikeImage(1200, 1600), DocumentTypeCode.DEED);

    assertThat(result.level()).isEqualTo(QualityLevel.ACCEPTED);
    assertThat(result.issues()).isEmpty();
  }

  @Test
  void acceptsAHorizontalPhotoOfAPageDocument() throws IOException {
    // Antes: "La foto debe tomarse en posición vertical". Ahora una foto acostada se acepta igual.
    QualityResult result = analyze(documentLikeImage(1600, 1200), DocumentTypeCode.MARRIAGE_CERTIFICATE);

    assertThat(result.acceptable()).isTrue();
    assertThat(result.issues()).noneMatch(i -> i.contains("vertical"));
  }

  @Test
  void acceptsAPanoramicOrTiltedPhotoWithBackground() throws IOException {
    BufferedImage onTable = new BufferedImage(2400, 900, BufferedImage.TYPE_INT_RGB);
    var g = onTable.createGraphics();
    g.setColor(new Color(110, 80, 60)); // la mesa
    g.fillRect(0, 0, onTable.getWidth(), onTable.getHeight());
    g.setTransform(AffineTransform.getRotateInstance(Math.toRadians(4), 1200, 450));
    g.drawImage(documentLikeImage(1000, 800), 700, 50, null);
    g.dispose();

    QualityResult result = analyze(onTable, DocumentTypeCode.PROPERTY_TAX);

    assertThat(result.acceptable()).isTrue();
  }

  @Test
  void acceptsASlightlyBlurryButLegiblePhotoWithAtMostAWarning() throws IOException {
    QualityResult result = analyze(heavilyBlur(documentLikeImage(1200, 1600), 3), DocumentTypeCode.DEED);

    assertThat(result.acceptable()).isTrue();
    assertThat(result.level()).isIn(QualityLevel.ACCEPTED, QualityLevel.ACCEPTED_WITH_WARNINGS);
  }

  @Test
  void aLowResolutionButReadablePhotoIsAWarningNotARejection() throws IOException {
    QualityResult result = analyze(documentLikeImage(600, 800), DocumentTypeCode.INE);

    assertThat(result.level()).isEqualTo(QualityLevel.ACCEPTED_WITH_WARNINGS);
    assertThat(result.issues()).anyMatch(i -> i.contains("Resolución baja"));
  }

  @Test
  void rejectsATotallyIllegibleSmear() throws IOException {
    QualityResult result = analyze(smear(documentLikeImage(1200, 1600), 16), DocumentTypeCode.DEED);

    assertThat(result.level()).isEqualTo(QualityLevel.UNREADABLE);
    assertThat(result.issues()).anyMatch(issue -> issue.contains("borrosa"));
  }

  @Test
  void rejectsABlankOrUniformGrayImage() throws IOException {
    BufferedImage gray = new BufferedImage(1200, 1600, BufferedImage.TYPE_INT_RGB);
    var g = gray.createGraphics();
    g.setColor(new Color(128, 128, 128));
    g.fillRect(0, 0, gray.getWidth(), gray.getHeight());
    g.dispose();

    QualityResult result = analyze(gray, DocumentTypeCode.INE);

    assertThat(result.level()).isEqualTo(QualityLevel.UNREADABLE);
    assertThat(result.issues()).anyMatch(issue -> issue.contains("gris o sin contraste"));
  }

  @Test
  void rejectsAnAlmostBlackPhoto() throws IOException {
    BufferedImage dark = documentLikeImage(1200, 1600);
    var g = dark.createGraphics();
    g.setColor(new Color(0, 0, 0, 250));
    g.fillRect(0, 0, dark.getWidth(), dark.getHeight());
    g.dispose();

    QualityResult result = analyze(dark, DocumentTypeCode.DEED);

    assertThat(result.level()).isEqualTo(QualityLevel.UNREADABLE);
  }

  @Test
  void rejectsATinyImageAndAnEmptyFile() {
    assertThat(analyzer.analyze(new byte[] {1, 2, 3}, "image/jpeg", 200, 240, DocumentTypeCode.DEED).level()).isEqualTo(QualityLevel.UNREADABLE);
    assertThat(analyzer.analyze(new byte[0], "image/jpeg", 1200, 1600, DocumentTypeCode.DEED).level()).isEqualTo(QualityLevel.UNREADABLE);
  }

  // ---------------------------------------------------------------------

  private QualityResult analyze(BufferedImage image, DocumentTypeCode type) throws IOException {
    return analyzer.analyze(encodeJpeg(image), "image/jpeg", image.getWidth(), image.getHeight(), type);
  }

  static BufferedImage documentLikeImage(int width, int height) {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    var g = image.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, width, height);
    g.setColor(Color.BLACK);
    g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(14, width / 40)));
    for (int y = 60; y < height - 40; y += Math.max(30, height / 32)) {
      g.drawString("ESCRITURA PUBLICA NUMERO 12345 - TEXTO DE PRUEBA", 30, y);
    }
    g.drawRect(15, 15, width - 30, height - 30);
    g.dispose();
    return image;
  }

  private static BufferedImage heavilyBlur(BufferedImage source, int passes) {
    float[] kernelData = {
      1f / 16, 2f / 16, 1f / 16,
      2f / 16, 4f / 16, 2f / 16,
      1f / 16, 2f / 16, 1f / 16,
    };
    BufferedImageOp op = new ConvolveOp(new Kernel(3, 3, kernelData), ConvolveOp.EDGE_NO_OP, null);
    BufferedImage current = source;
    for (int i = 0; i < passes; i++) {
      BufferedImage output = new BufferedImage(current.getWidth(), current.getHeight(), BufferedImage.TYPE_INT_RGB);
      op.filter(current, output);
      current = output;
    }
    return current;
  }

  /** Reduce la imagen 1/factor y la vuelve a ampliar: el texto queda como manchas ilegibles. */
  static BufferedImage smear(BufferedImage source, int factor) {
    int w = source.getWidth() / factor;
    int h = source.getHeight() / factor;
    BufferedImage tiny = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    var g = tiny.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(source, 0, 0, w, h, null);
    g.dispose();
    BufferedImage big = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
    g = big.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(tiny, 0, 0, source.getWidth(), source.getHeight(), null);
    g.dispose();
    return big;
  }

  static byte[] encodeJpeg(BufferedImage image) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "jpg", out);
    return out.toByteArray();
  }
}
