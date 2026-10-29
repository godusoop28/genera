package com.c21genera.shared.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

/** 29/10: una captura de pantalla larga en Flate pedía ~115 MB de heap para decodificarse. */
class LeanPdfRendererTest {

  @Test
  void bigFlateImagesLookTheSameAsWithPdfBox() throws Exception {
    BufferedImage source = new BufferedImage(1000, 2400, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = source.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, 1000, 2400);
    g.setColor(new Color(200, 30, 60));
    g.fillRect(100, 300, 400, 500);
    g.setColor(Color.BLACK);
    g.fillRect(600, 1800, 300, 300);
    g.dispose();

    try (PDDocument document = PdfFiles.create()) {
      PDPage page = new PDPage(new PDRectangle(500, 1200));
      document.addPage(page);
      PDImageXObject image = LosslessFactory.createFromImage(document, source);
      try (PDPageContentStream content = new PDPageContentStream(document, page)) {
        content.drawImage(image, 0, 0, 500, 1200);
      }

      assertThat(LeanPdfRenderer.leanComponents(image)).isEqualTo(3);
      BufferedImage lean = PdfFiles.renderer(document).renderImageWithDPI(0, 72, ImageType.RGB);
      BufferedImage pdfBox = new PDFRenderer(document).renderImageWithDPI(0, 72, ImageType.RGB);

      assertThat(lean.getWidth()).isEqualTo(pdfBox.getWidth());
      assertThat(lean.getHeight()).isEqualTo(pdfBox.getHeight());
      for (int[] p : new int[][] {{150, 275}, {375, 1025}, {10, 10}, {490, 1190}}) {
        assertThat(lean.getRGB(p[0], p[1])).as("pixel %s,%s", p[0], p[1]).isEqualTo(pdfBox.getRGB(p[0], p[1]));
      }
    }
  }

  @Test
  void subsamplingTakesOneOfEachRowsAndColumns() throws Exception {
    BufferedImage source = new BufferedImage(1500, 1500, BufferedImage.TYPE_INT_RGB);
    source.setRGB(0, 0, 0x123456);
    try (PDDocument document = PdfFiles.create()) {
      PDImageXObject image = LosslessFactory.createFromImage(document, source);

      BufferedImage half = LeanPdfRenderer.decode(image, 3, 2);

      assertThat(half.getWidth()).isEqualTo(750);
      assertThat(half.getHeight()).isEqualTo(750);
      assertThat(half.getRGB(0, 0) & 0xFFFFFF).isEqualTo(0x123456);
    }
  }

  @Test
  void smallImagesStayWithPdfBox() throws Exception {
    try (PDDocument document = PdfFiles.create()) {
      PDImageXObject image = LosslessFactory.createFromImage(document, new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB));
      assertThat(LeanPdfRenderer.leanComponents(image)).isZero();
    }
  }
}
