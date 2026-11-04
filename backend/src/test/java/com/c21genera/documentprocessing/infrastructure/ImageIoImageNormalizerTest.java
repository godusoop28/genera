package com.c21genera.documentprocessing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c21genera.documentprocessing.domain.ImageNormalizer.NormalizedImage;
import com.c21genera.documentprocessing.domain.ImageNormalizer.UnreadableImageException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageIoImageNormalizerTest {

  private final ImageIoImageNormalizer normalizer = new ImageIoImageNormalizer();

  @Test
  void reencodesAPlainPngAsJpegPreservingDimensions() throws Exception {
    byte[] png = solidColorPng(800, 600);

    NormalizedImage result = normalizer.normalize(png, "image/png");

    assertThat(result.mimeType()).isEqualTo("image/jpeg");
    assertThat(result.widthPx()).isEqualTo(800);
    assertThat(result.heightPx()).isEqualTo(600);
    assertThat(result.content()).isNotEmpty();
  }

  @Test
  void rejectsUnreadableBytes() {
    byte[] garbage = "not an image".getBytes();

    assertThatThrownBy(() -> normalizer.normalize(garbage, "image/jpeg"))
        .isInstanceOf(UnreadableImageException.class);
  }

  @Test
  void decodesBigPhotosAlreadyReducedAndFitsThemTo2400px() throws Exception {
    BufferedImage big = new BufferedImage(7200, 4800, BufferedImage.TYPE_INT_RGB);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(big, "jpg", out);

    BufferedImage decoded = ImageIoImageNormalizer.decodeReduced(out.toByteArray());
    assertThat(decoded.getWidth()).isEqualTo(2400); // submuestreo 3: nunca se decodifica a 7200 px.

    NormalizedImage result = normalizer.normalize(out.toByteArray(), "image/jpeg");
    assertThat(Math.max(result.widthPx(), result.heightPx())).isEqualTo(2400);
  }

  @Test
  void acceptsTiffBmpAndGif() throws Exception {
    for (String format : new String[] {"tiff", "bmp", "gif"}) {
      BufferedImage image = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      assertThat(ImageIO.write(image, format, out)).isTrue();
      NormalizedImage result = normalizer.normalize(out.toByteArray(), "image/" + format);
      assertThat(result.mimeType()).isEqualTo("image/jpeg");
      assertThat(result.widthPx()).isEqualTo(300);
    }
  }

  @Test
  void convertsFourChannelRastersToRgb() {
    java.awt.image.WritableRaster cmyk =
        java.awt.image.Raster.createInterleavedRaster(java.awt.image.DataBuffer.TYPE_BYTE, 4, 3, 4, null);
    // Papel blanco en CMYK: todo en 0.
    BufferedImage rgb = ImageIoImageNormalizer.fromRaster(cmyk);
    assertThat(rgb.getRGB(0, 0) & 0xFFFFFF).isEqualTo(0xFFFFFF);
  }

  private static byte[] solidColorPng(int width, int height) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    var graphics = image.createGraphics();
    graphics.setColor(java.awt.Color.BLUE);
    graphics.fillRect(0, 0, width, height);
    graphics.dispose();

    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }
}
