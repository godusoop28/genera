package com.c21genera.documentprocessing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import org.junit.jupiter.api.Test;

class BlurDetectorTest {

  @Test
  void sharpDocumentPhotoHasMuchHigherVarianceThanBlurredOne() {
    BufferedImage sharp = documentLikeImage();
    BufferedImage blurred = heavilyBlur(sharp, 6);

    double sharpVariance = BlurDetector.laplacianVariance(sharp);
    double blurredVariance = BlurDetector.laplacianVariance(blurred);

    System.out.println("sharpVariance=" + sharpVariance + " blurredVariance=" + blurredVariance);

    assertThat(sharpVariance).isGreaterThan(blurredVariance * 5);
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
}
