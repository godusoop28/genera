package com.c21genera.documentprocessing.infrastructure;

import java.awt.Image;
import java.awt.image.BufferedImage;

/**
 * Detección determinística de borrosidad (ver AGENTS §36): varianza del
 * filtro Laplaciano en escala de grises. Una imagen nítida tiene bordes
 * marcados (varianza alta); una borrosa o movida los suaviza (varianza
 * baja). Sin IA, sin contenido: solo textura de bordes.
 */
final class BlurDetector {

  // Reescalar antes de medir: hace que el umbral sea comparable entre fotos
  // de distinta resolución (celulares con más megapíxeles no deberían
  // "ganar" nitidez solo por tener más ruido de sensor) y acota el costo.
  private static final int MAX_DIMENSION_FOR_ANALYSIS = 1000;

  private BlurDetector() {}

  static double laplacianVariance(BufferedImage image) {
    BufferedImage scaled = downscale(image, MAX_DIMENSION_FOR_ANALYSIS);
    int width = scaled.getWidth();
    int height = scaled.getHeight();
    if (width < 3 || height < 3) return Double.MAX_VALUE; // demasiado pequeña para medir: no bloquear por esto

    int[] gray = toGrayscale(scaled);

    double sum = 0;
    double sumSquares = 0;
    long count = 0;

    for (int y = 1; y < height - 1; y++) {
      int row = y * width;
      int rowUp = (y - 1) * width;
      int rowDown = (y + 1) * width;
      for (int x = 1; x < width - 1; x++) {
        int laplacian = gray[rowUp + x] + gray[rowDown + x] + gray[row + x - 1] + gray[row + x + 1] - 4 * gray[row + x];
        sum += laplacian;
        sumSquares += (double) laplacian * laplacian;
        count++;
      }
    }

    double mean = sum / count;
    return (sumSquares / count) - (mean * mean);
  }

  /** Brillo medio (0-255) y desviación estándar del brillo: detecta fotos en blanco, grises o casi negras. */
  record Luminance(double mean, double standardDeviation) {}

  static Luminance luminance(BufferedImage image) {
    BufferedImage scaled = downscale(image, MAX_DIMENSION_FOR_ANALYSIS);
    int[] gray = toGrayscale(scaled);
    double sum = 0;
    double sumSquares = 0;
    for (int value : gray) {
      sum += value;
      sumSquares += (double) value * value;
    }
    double mean = sum / gray.length;
    double variance = Math.max(0, (sumSquares / gray.length) - mean * mean);
    return new Luminance(mean, Math.sqrt(variance));
  }

  private static int[] toGrayscale(BufferedImage image) {
    int width = image.getWidth();
    int height = image.getHeight();
    int[] gray = new int[width * height];
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int rgb = image.getRGB(x, y);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        gray[y * width + x] = (r + g + b) / 3;
      }
    }
    return gray;
  }

  private static BufferedImage downscale(BufferedImage image, int maxDimension) {
    int width = image.getWidth();
    int height = image.getHeight();
    int largerSide = Math.max(width, height);
    if (largerSide <= maxDimension) return image;

    double scale = (double) maxDimension / largerSide;
    int newWidth = Math.max(1, (int) (width * scale));
    int newHeight = Math.max(1, (int) (height * scale));

    BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
    var graphics = scaledImage.createGraphics();
    graphics.drawImage(image.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH), 0, 0, null);
    graphics.dispose();
    return scaledImage;
  }
}
