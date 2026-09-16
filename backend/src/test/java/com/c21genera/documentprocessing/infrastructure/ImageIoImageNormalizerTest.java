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
