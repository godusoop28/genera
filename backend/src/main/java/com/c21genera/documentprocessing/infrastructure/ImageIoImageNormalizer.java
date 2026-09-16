package com.c21genera.documentprocessing.infrastructure;

import com.c21genera.documentprocessing.domain.ImageNormalizer;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import java.awt.Color;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * Corrige la orientación EXIF (fotos tomadas con el celular del cliente, ver
 * AGENTS §36) y reencodea a JPEG consistente. Sin IA: puramente
 * determinístico.
 */
@Component
public class ImageIoImageNormalizer implements ImageNormalizer {

  private static final float JPEG_QUALITY = 0.9f;

  @Override
  public NormalizedImage normalize(byte[] original, String sourceMimeType) {
    BufferedImage image;
    try {
      image = ImageIO.read(new ByteArrayInputStream(original));
    } catch (Exception e) {
      throw new UnreadableImageException("No se pudo leer la imagen (" + sourceMimeType + ")", e);
    }
    if (image == null) {
      throw new UnreadableImageException("Formato de imagen no soportado: " + sourceMimeType, null);
    }

    int orientation = readExifOrientation(original);
    BufferedImage corrected = applyOrientation(image, orientation);
    BufferedImage flattened = flattenToRgb(corrected);

    byte[] jpeg = encodeJpeg(flattened);
    return new NormalizedImage(jpeg, "image/jpeg", flattened.getWidth(), flattened.getHeight());
  }

  private int readExifOrientation(byte[] original) {
    try {
      Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(original));
      ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
      if (directory != null && directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
        return directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
      }
    } catch (Exception ignored) {
      // Sin metadatos EXIF (p. ej. PNG): se asume orientación normal.
    }
    return 1;
  }

  private BufferedImage applyOrientation(BufferedImage image, int orientation) {
    int width = image.getWidth();
    int height = image.getHeight();
    AffineTransform transform = new AffineTransform();

    switch (orientation) {
      case 3 -> {
        transform.translate(width, height);
        transform.rotate(Math.PI);
      }
      case 6 -> {
        transform.translate(height, 0);
        transform.rotate(Math.PI / 2);
      }
      case 8 -> {
        transform.translate(0, width);
        transform.rotate(-Math.PI / 2);
      }
      default -> {
        return image; // 1 = normal; valores exóticos (2,4,5,7) se dejan sin transformar.
      }
    }

    boolean swapsDimensions = orientation == 6 || orientation == 8;
    BufferedImage rotated =
        new BufferedImage(
            swapsDimensions ? height : width, swapsDimensions ? width : height, BufferedImage.TYPE_INT_RGB);
    AffineTransformOp op = new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR);
    op.filter(image, rotated);
    return rotated;
  }

  private BufferedImage flattenToRgb(BufferedImage image) {
    if (image.getType() == BufferedImage.TYPE_INT_RGB && !image.getColorModel().hasAlpha()) {
      return image;
    }
    BufferedImage flattened = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
    var graphics = flattened.createGraphics();
    graphics.setColor(Color.WHITE);
    graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
    graphics.drawImage(image, 0, 0, null);
    graphics.dispose();
    return flattened;
  }

  private byte[] encodeJpeg(BufferedImage image) {
    try {
      var writers = ImageIO.getImageWritersByFormatName("jpg");
      var writer = writers.next();
      var params = writer.getDefaultWriteParam();
      params.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
      params.setCompressionQuality(JPEG_QUALITY);

      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (var ios = ImageIO.createImageOutputStream(baos)) {
        writer.setOutput(ios);
        writer.write(null, new javax.imageio.IIOImage(image, null, null), params);
      } finally {
        writer.dispose();
      }
      return baos.toByteArray();
    } catch (Exception e) {
      throw new UnreadableImageException("No se pudo reencodear la imagen normalizada", e);
    }
  }
}
