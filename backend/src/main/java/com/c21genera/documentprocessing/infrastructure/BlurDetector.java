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

  private static final int TILE = 32;
  /** Un recuadro "tiene texto" si su brillo varía al menos esto (en blanco/gris uniforme no varía). */
  private static final double TILE_MIN_STDDEV = 12.0;

  /**
   * Nitidez del TEXTO, no de la hoja: varianza del Laplaciano en los recuadros
   * de 32x32 que tienen contenido, percentil 90. El promedio de toda la imagen
   * castigaba a los documentos con poco texto (una INE en una hoja blanca daba
   * "borrosa" aunque se leyera perfecto) porque el blanco diluía el valor.
   * Calibración (E2E 02/10, fixtures de QA): nítidas ~6 000-7 000, ligeramente
   * borrosa ~790, ilegible ~3, sin recuadros con contenido 0.
   */
  static double textSharpness(BufferedImage image) {
    BufferedImage scaled = downscale(image, MAX_DIMENSION_FOR_ANALYSIS);
    int width = scaled.getWidth();
    int height = scaled.getHeight();
    if (width < TILE + 2 || height < TILE + 2) {
      return laplacianVariance(image);
    }
    int[] gray = toGrayscale(scaled);
    java.util.List<Double> tiles = new java.util.ArrayList<>();
    for (int ty = 1; ty + TILE < height - 1; ty += TILE) {
      for (int tx = 1; tx + TILE < width - 1; tx += TILE) {
        double sum = 0;
        double sumSq = 0;
        for (int y = ty; y < ty + TILE; y++) {
          for (int x = tx; x < tx + TILE; x++) {
            int v = gray[y * width + x];
            sum += v;
            sumSq += (double) v * v;
          }
        }
        int n = TILE * TILE;
        double mean = sum / n;
        if (Math.sqrt(Math.max(0, sumSq / n - mean * mean)) < TILE_MIN_STDDEV) {
          continue;
        }
        double lSum = 0;
        double lSq = 0;
        for (int y = ty; y < ty + TILE; y++) {
          for (int x = tx; x < tx + TILE; x++) {
            int lap = gray[(y - 1) * width + x] + gray[(y + 1) * width + x] + gray[y * width + x - 1] + gray[y * width + x + 1] - 4 * gray[y * width + x];
            lSum += lap;
            lSq += (double) lap * lap;
          }
        }
        double lMean = lSum / n;
        tiles.add(lSq / n - lMean * lMean);
      }
    }
    if (tiles.isEmpty()) {
      return 0;
    }
    java.util.Collections.sort(tiles);
    return tiles.get((int) Math.floor(0.9 * (tiles.size() - 1)));
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
