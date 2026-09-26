package com.c21genera.documentprocessing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.documentprocessing.domain.DocumentQualityAnalyzer.QualityResult;
import com.c21genera.shared.domain.DocumentTypeCode;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class HeuristicDocumentQualityAnalyzerTest {

  private final HeuristicDocumentQualityAnalyzer analyzer = new HeuristicDocumentQualityAnalyzer();

  @Test
  void acceptsAGoodResolutionVerticalPhoto() {
    QualityResult result = analyzer.analyze(new byte[]{1, 2, 3}, "image/jpeg", 1200, 1600, DocumentTypeCode.DEED);

    assertThat(result.acceptable()).isTrue();
    assertThat(result.issues()).isEmpty();
  }

  @Test
  void rejectsALowResolutionPhoto() {
    QualityResult result = analyzer.analyze(new byte[]{1, 2, 3}, "image/jpeg", 200, 300, DocumentTypeCode.DEED);

    assertThat(result.acceptable()).isFalse();
    assertThat(result.issues()).isNotEmpty();
  }

  @Test
  void rejectsAnExtremeAspectRatio() {
    QualityResult result = analyzer.analyze(new byte[]{1, 2, 3}, "image/jpeg", 5000, 500, DocumentTypeCode.DEED);

    assertThat(result.acceptable()).isFalse();
  }

  @Test
  void rejectsAnEmptyFile() {
    QualityResult result = analyzer.analyze(new byte[0], "image/jpeg", 1200, 1600, DocumentTypeCode.DEED);

    assertThat(result.acceptable()).isFalse();
  }

  @Test
  void rejectsALandscapePhotoForADocumentThatShouldBeVertical() {
    QualityResult result = analyzer.analyze(new byte[]{1, 2, 3}, "image/jpeg", 1600, 1200, DocumentTypeCode.PROOF_OF_ADDRESS);

    assertThat(result.acceptable()).isFalse();
    assertThat(result.issues()).anyMatch(issue -> issue.contains("posición vertical"));
  }

  @Test
  void acceptsALandscapePhotoForACardShapedDocument() {
    QualityResult result = analyzer.analyze(new byte[]{1, 2, 3}, "image/jpeg", 1600, 1200, DocumentTypeCode.INE);

    assertThat(result.acceptable()).isTrue();
  }

  @Test
  void acceptsARealSharpDocumentPhoto() throws IOException {
    byte[] jpeg = encodeJpeg(documentLikeImage());

    QualityResult result = analyzer.analyze(jpeg, "image/jpeg", 1200, 1600, DocumentTypeCode.DEED);

    assertThat(result.acceptable()).isTrue();
  }

  @Test
  void rejectsAPlainGrayImageExplainingThatNoDocumentIsVisible() throws IOException {
    BufferedImage gray = new BufferedImage(1200, 1600, BufferedImage.TYPE_INT_RGB);
    var g = gray.createGraphics();
    g.setColor(new Color(128, 128, 128));
    g.fillRect(0, 0, gray.getWidth(), gray.getHeight());
    g.dispose();

    QualityResult result = analyzer.analyze(encodeJpeg(gray), "image/jpeg", 1200, 1600, DocumentTypeCode.INE);

    assertThat(result.acceptable()).isFalse();
    assertThat(result.issues()).anyMatch(issue -> issue.contains("gris o sin contraste"));
  }

  @Test
  void rejectsAnAlmostBlackPhoto() throws IOException {
    BufferedImage dark = documentLikeImage();
    var g = dark.createGraphics();
    g.setColor(new Color(0, 0, 0, 235));
    g.fillRect(0, 0, dark.getWidth(), dark.getHeight());
    g.dispose();

    QualityResult result = analyzer.analyze(encodeJpeg(dark), "image/jpeg", 1200, 1600, DocumentTypeCode.DEED);

    assertThat(result.acceptable()).isFalse();
    assertThat(result.issues()).anyMatch(issue -> issue.contains("oscura") || issue.contains("contraste"));
  }

  @Test
  void rejectsARealBlurryDocumentPhoto() throws IOException {
    byte[] jpeg = encodeJpeg(heavilyBlur(documentLikeImage(), 20));

    QualityResult result = analyzer.analyze(jpeg, "image/jpeg", 1200, 1600, DocumentTypeCode.DEED);

    assertThat(result.acceptable()).isFalse();
    assertThat(result.issues()).anyMatch(issue -> issue.contains("borrosa"));
  }

  private static BufferedImage documentLikeImage() {
    BufferedImage image = new BufferedImage(1200, 1600, BufferedImage.TYPE_INT_RGB);
    var g = image.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, image.getWidth(), image.getHeight());
    g.setColor(Color.BLACK);
    g.setFont(new Font("SansSerif", Font.PLAIN, 28));
    for (int y = 100; y < 1550; y += 50) {
      g.drawString("ESCRITURA PUBLICA NUMERO 12345 - TEXTO DE PRUEBA", 60, y);
    }
    g.drawRect(30, 30, image.getWidth() - 60, image.getHeight() - 60);
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

  private static byte[] encodeJpeg(BufferedImage image) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "jpg", out);
    return out.toByteArray();
  }
}
