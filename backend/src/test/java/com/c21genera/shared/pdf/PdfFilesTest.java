package com.c21genera.shared.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

/** 28/10: una INE con dos fotos de 12 MP no cabía en el heap al renderizarse. */
class PdfFilesTest {

  @Test
  void bigPhotosAreDecodedAlreadyReducedToTheRenderedSize() throws Exception {
    try (PDDocument document = PdfFiles.create()) {
      PDPage page = new PDPage(new PDRectangle(960, 540));
      document.addPage(page);
      var photo = JPEGFactory.createFromImage(document, new BufferedImage(4032, 3024, BufferedImage.TYPE_INT_RGB));
      try (PDPageContentStream content = new PDPageContentStream(document, page)) {
        content.drawImage(photo, 0, 0, 480, 360);
      }

      PDFRenderer renderer = PdfFiles.renderer(document);
      BufferedImage rendered = renderer.renderImageWithDPI(0, 110, ImageType.RGB);

      assertThat(renderer.isSubsamplingAllowed()).isTrue();
      assertThat(rendered.getWidth()).isEqualTo(1466);
    }
  }

  /** 29/10: una captura de pantalla larga del iPhone (1265×4327 pt) pedía 204 MB a 220 DPI. */
  @Test
  void hugePagesAreRenderedWithinThePixelCap() throws Exception {
    try (PDDocument document = PdfFiles.create()) {
      document.addPage(new PDPage(new PDRectangle(1265, 4327)));

      BufferedImage rendered = PdfFiles.renderPage(PdfFiles.renderer(document), document, 0, 220);

      assertThat((long) rendered.getWidth() * rendered.getHeight()).isLessThanOrEqualTo(PdfFiles.MAX_RENDER_PIXELS);
      assertThat(rendered.getHeight()).isGreaterThan(4000);
    }
  }

  @Test
  void normalPagesKeepTheirDpi() {
    assertThat(PdfFiles.cappedDpi(PDRectangle.LETTER, 220)).isEqualTo(220f);
  }
}
