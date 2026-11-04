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

  // Una foto de celular de 12-50 MP no aporta legibilidad extra a un
  // documento y multiplica memoria, tamaño del PDF y de la petición a la IA.
  private static final int MAX_LONG_SIDE_PX = 2400;

  @Override
  public NormalizedImage normalize(byte[] original, String sourceMimeType) {
    BufferedImage image;
    try {
      image = decodeReduced(original);
    } catch (UnreadableImageException e) {
      throw e;
    } catch (Exception | OutOfMemoryError e) {
      throw new UnreadableImageException("No se pudo leer la imagen (" + sourceMimeType + ")", e);
    }
    if (image == null) {
      throw new UnreadableImageException("Formato de imagen no soportado: " + sourceMimeType, null);
    }

    int orientation = readExifOrientation(original);
    BufferedImage corrected = applyOrientation(image, orientation);
    BufferedImage flattened = downscale(flattenToRgb(corrected));

    byte[] jpeg = encodeJpeg(flattened);
    return new NormalizedImage(jpeg, "image/jpeg", flattened.getWidth(), flattened.getHeight());
  }

  /**
   * Decodifica la imagen ya reducida (submuestreo del lector): una foto de 50 MP decodificada completa
   * ocupaba ~200 MB, más que todo el heap del servidor, y el documento nunca terminaba de procesarse
   * (04/11). Así se decodifica a un tamaño cercano a {@link #MAX_LONG_SIDE_PX} y luego se ajusta.
   * Los JPEG en CMYK (escáneres, Photoshop), que ImageIO no convierte solo, se leen como raster.
   */
  static BufferedImage decodeReduced(byte[] original) throws java.io.IOException {
    try (javax.imageio.stream.ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(original))) {
      if (in == null) {
        return null;
      }
      java.util.Iterator<javax.imageio.ImageReader> readers = ImageIO.getImageReaders(in);
      if (!readers.hasNext()) {
        return null;
      }
      javax.imageio.ImageReader reader = readers.next();
      try {
        reader.setInput(in, true, true);
        int longSide = Math.max(reader.getWidth(0), reader.getHeight(0));
        int factor = Math.max(1, longSide / MAX_LONG_SIDE_PX);
        javax.imageio.ImageReadParam param = reader.getDefaultReadParam();
        param.setSourceSubsampling(factor, factor, 0, 0);
        try {
          return reader.read(0, param);
        } catch (javax.imageio.IIOException e) {
          if (!reader.canReadRaster()) {
            throw e;
          }
          return fromRaster(reader.readRaster(0, param));
        }
      } finally {
        reader.dispose();
      }
    }
  }

  /** Raster de 4 canales (CMYK) o de 1-3 canales sin modelo de color: se convierte a RGB legible. */
  static BufferedImage fromRaster(java.awt.image.Raster raster) {
    int w = raster.getWidth();
    int h = raster.getHeight();
    int bands = raster.getNumBands();
    BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    int[] px = new int[bands];
    long sum = 0;
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        raster.getPixel(x + raster.getMinX(), y + raster.getMinY(), px);
        int r;
        int g;
        int b;
        if (bands >= 4) {
          int k = px[3];
          r = (255 - px[0]) * (255 - k) / 255;
          g = (255 - px[1]) * (255 - k) / 255;
          b = (255 - px[2]) * (255 - k) / 255;
        } else if (bands == 3) {
          r = px[0];
          g = px[1];
          b = px[2];
        } else {
          r = g = b = px[0];
        }
        sum += (r + g + b) / 3;
        rgb.setRGB(x, y, (clamp(r) << 16) | (clamp(g) << 8) | clamp(b));
      }
    }
    // Los CMYK de Adobe vienen invertidos: si todo quedó casi negro, es eso.
    if (bands >= 4 && w > 0 && h > 0 && sum / ((long) w * h) < 60) {
      for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
          rgb.setRGB(x, y, ~rgb.getRGB(x, y) & 0xFFFFFF);
        }
      }
    }
    return rgb;
  }

  private static int clamp(int v) {
    return Math.max(0, Math.min(255, v));
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

  private BufferedImage downscale(BufferedImage image) {
    int longSide = Math.max(image.getWidth(), image.getHeight());
    if (longSide <= MAX_LONG_SIDE_PX) {
      return image;
    }
    double scale = (double) MAX_LONG_SIDE_PX / longSide;
    int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
    int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
    BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    var graphics = scaled.createGraphics();
    graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
    graphics.drawImage(image, 0, 0, width, height, null);
    graphics.dispose();
    return scaled;
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
