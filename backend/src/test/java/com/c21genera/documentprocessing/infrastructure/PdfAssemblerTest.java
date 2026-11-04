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

  @Test
  void mergesPdfsReadFromDiskIntoAStream() throws Exception {
    byte[] onePage = assembler.assemble(List.of(new PageContent(solidColorJpeg(400, 300), "image/jpeg")));
    java.nio.file.Path a = java.nio.file.Files.createTempFile("a", ".pdf");
    java.nio.file.Path b = java.nio.file.Files.createTempFile("b", ".pdf");
    try {
      java.nio.file.Files.write(a, onePage);
      java.nio.file.Files.write(b, onePage);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      assembler.assemble(
          List.of(
              new PageContent(null, "application/pdf", "k1", a),
              new PageContent(solidColorJpeg(300, 400), "image/jpeg"),
              new PageContent(null, "application/pdf", "k2", b)),
          out);
      try (PDDocument document = Loader.loadPDF(out.toByteArray())) {
        assertThat(document.getNumberOfPages()).isEqualTo(3);
      }
    } finally {
      java.nio.file.Files.deleteIfExists(a);
      java.nio.file.Files.deleteIfExists(b);
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
