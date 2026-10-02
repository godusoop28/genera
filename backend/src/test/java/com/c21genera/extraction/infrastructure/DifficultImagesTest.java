package com.c21genera.extraction.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.documentprocessing.infrastructure.ImageIoImageNormalizer;
import com.c21genera.documentprocessing.infrastructure.PdfAssembler;
import com.c21genera.shared.pdf.PdfFiles;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;

/**
 * La versión mejorada de los fixtures de QA, pasando por el mismo camino que en
 * producción (normalización -> PDF -> render). Deja las franjas en
 * target/difficult-bands para revisarlas a ojo.
 */
class DifficultImagesTest {

  private static BufferedImage renderedPage(String fixture) throws Exception {
    byte[] original;
    try (InputStream in = DifficultImagesTest.class.getResourceAsStream("/qa-fixtures/" + fixture)) {
      original = in.readAllBytes();
    }
    var normalized = new ImageIoImageNormalizer().normalize(original, "image/jpeg");
    byte[] pdf = new PdfAssembler().assemble(List.of(new PdfAssembler.PageContent(normalized.content(), normalized.mimeType())));
    try (PDDocument document = PdfFiles.open(pdf)) {
      float longSide = Math.max(document.getPage(0).getCropBox().getWidth(), document.getPage(0).getCropBox().getHeight());
      return new PDFRenderer(document).renderImageWithDPI(0, (int) (1800 * 72 / longSide), ImageType.RGB);
    }
  }

  private static List<BufferedImage> bandsOf(String fixture) throws Exception {
    List<BufferedImage> bands = DifficultImages.enhancedBands(renderedPage(fixture));
    File dir = new File("target/difficult-bands");
    dir.mkdirs();
    for (int i = 0; i < bands.size(); i++) {
      ImageIO.write(bands.get(i), "png", new File(dir, fixture.replace(".jpg", "") + "-" + (i + 1) + ".png"));
    }
    return bands;
  }

  @Test
  void theTiltOfTheInclinedIdIsDetectedAndAStraightCardIsLeftAlone() throws Exception {
    double tilted = DifficultImages.skewAngle(DifficultImages.grayscale(renderedPage("INE_INCLINADA_LEGIBLE.jpg")));
    double straight = DifficultImages.skewAngle(DifficultImages.grayscale(renderedPage("INE_FOTO_HORIZONTAL_LEGIBLE.jpg")));

    assertThat(Math.abs(tilted)).isBetween(2.0, 8.0);
    assertThat(Math.abs(straight)).isLessThan(0.4);
  }

  @Test
  void smallBlurryTextIsEnlargedIntoBandsTheModelWillNotShrink() throws Exception {
    List<BufferedImage> bands = bandsOf("INE_LIGERAMENTE_BORROSA_PERO_LEGIBLE.jpg");

    assertThat(bands).hasSizeBetween(2, DifficultImages.MAX_BANDS);
    assertThat(bands).allSatisfy(b -> {
      assertThat(b.getHeight()).isLessThanOrEqualTo(DifficultImages.BAND_HEIGHT + DifficultImages.BAND_OVERLAP);
      assertThat(b.getWidth()).isBetween(1200, 2048);
    });
  }

  @Test
  void theInclinedIdIsStraightenedAndCroppedToItsText() throws Exception {
    List<BufferedImage> bands = bandsOf("INE_INCLINADA_LEGIBLE.jpg");

    // Antes, el marco inclinado impedía recortar: el texto quedaba en una imagen de página completa.
    assertThat(bands).hasSizeGreaterThanOrEqualTo(2);
    assertThat(bands.getFirst().getWidth()).isBetween(1200, 2048);
  }

  @Test
  void anImageThatIsAlmostUniformIsNotStretched() {
    BufferedImage blank = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
    var g = blank.createGraphics();
    g.setColor(java.awt.Color.WHITE);
    g.fillRect(0, 0, 400, 300);
    g.dispose();

    assertThat(DifficultImages.autoContrast(DifficultImages.grayscale(blank)).getRGB(10, 10) & 0xFF).isEqualTo(255);
    assertThat(DifficultImages.skewAngle(DifficultImages.grayscale(blank))).isZero();
  }
}
