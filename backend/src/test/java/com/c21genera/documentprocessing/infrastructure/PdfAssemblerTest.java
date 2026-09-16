package com.c21genera.documentprocessing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.documentprocessing.infrastructure.PdfAssembler.PageContent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

class PdfAssemblerTest {

  private final PdfAssembler assembler = new PdfAssembler();

  @Test
  void assemblesOnePagePerImage() throws Exception {
    byte[] jpeg1 = solidColorJpeg(800, 600);
    byte[] jpeg2 = solidColorJpeg(600, 800);

    byte[] pdf = assembler.assemble(List.of(new PageContent(jpeg1, "image/jpeg"), new PageContent(jpeg2, "image/jpeg")));

    try (PDDocument document = Loader.loadPDF(pdf)) {
      assertThat(document.getNumberOfPages()).isEqualTo(2);
    }
  }

  private static byte[] solidColorJpeg(int width, int height) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    graphics.setColor(java.awt.Color.RED);
    graphics.fillRect(0, 0, width, height);
    graphics.dispose();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "jpg", out);
    return out.toByteArray();
  }
}
