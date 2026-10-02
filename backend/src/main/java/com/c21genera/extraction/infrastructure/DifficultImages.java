package com.c21genera.extraction.infrastructure;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Variante "mejorada" de una página difícil de leer (borrosa, inclinada, con
 * letra chica), para una segunda lectura con IA. Solo se usa cuando la primera
 * lectura dio señales de problema: una imagen que ya funciona no se toca.
 *
 * <p>Pasos: enderezar (perfil de proyección), escala de grises, autocontraste,
 * recorte al contenido, ampliación de hasta 4x y enfoque moderado, y división
 * en franjas horizontales que se traslapan. Las franjas importan porque OpenAI
 * reduce cada imagen a ~768 px por su lado corto: una página entera ampliada
 * volvería a quedar chica, una franja no (E2E 02/10: el texto de la INE borrosa
 * medía ~8 px de alto).
 *
 * <p>Nada de esto agrega información: solo hace más visible lo que ya está.
 */
final class DifficultImages {

  private DifficultImages() {}

  /** Ancho objetivo del contenido ampliado (OpenAI usa hasta 2048 px). */
  static final int TARGET_WIDTH = 1600;
  /** Alto de cada franja: menor que 768 para que OpenAI no la reduzca. */
  static final int BAND_HEIGHT = 640;
  static final int BAND_OVERLAP = 140;
  static final int MAX_BANDS = 6;
  private static final double MAX_SCALE = 4.0;
  private static final int INK = 170;

  /** Franjas mejoradas de la página, de arriba a abajo. */
  static List<BufferedImage> enhancedBands(BufferedImage page) {
    BufferedImage gray = autoContrast(grayscale(page));
    double angle = skewAngle(gray);
    BufferedImage straight = Math.abs(angle) >= 0.4 ? rotateDegrees(gray, -angle) : gray;
    BufferedImage content = cropToText(straight);
    // Se enfoca el recorte (chico) antes de ampliarlo: enfocar la imagen ya ampliada costaría ~16x memoria.
    double scale = Math.max(1.0, Math.min(MAX_SCALE, (double) TARGET_WIDTH / content.getWidth()));
    return bands(scale(sharpen(content, 1), scale));
  }

  /**
   * Recorta al texto ignorando las líneas (marco de la credencial, bordes de la
   * foto, renglones de un formulario): una línea es una racha de tinta de más
   * del 15% del lado. A diferencia de "fila con tinta en más de la mitad", una
   * racha sigue siendo larga aunque la línea quede ligeramente inclinada (tras
   * enderezar siempre queda una inclinación residual de décimas de grado).
   */
  static BufferedImage cropToText(BufferedImage gray) {
    int w = gray.getWidth();
    int h = gray.getHeight();
    int step = Math.max(1, Math.min(w, h) / 700);
    int sw = (w + step - 1) / step;
    int sh = (h + step - 1) / step;
    boolean[][] ink = new boolean[sh][sw];
    for (int yi = 0; yi < sh; yi++) {
      for (int xi = 0; xi < sw; xi++) {
        ink[yi][xi] = (gray.getRGB(Math.min(w - 1, xi * step), Math.min(h - 1, yi * step)) & 0xFF) < INK;
      }
    }
    boolean[][] line = new boolean[sh][sw];
    int longRow = Math.max(8, sw * 15 / 100);
    int longCol = Math.max(8, sh * 15 / 100);
    for (int yi = 0; yi < sh; yi++) {
      markLongRuns(ink, line, yi, sw, longRow, true);
    }
    for (int xi = 0; xi < sw; xi++) {
      markLongRuns(ink, line, xi, sh, longCol, false);
    }
    int minX = sw, minY = sh, maxX = -1, maxY = -1;
    for (int yi = 0; yi < sh; yi++) {
      for (int xi = 0; xi < sw; xi++) {
        if (ink[yi][xi] && !nearLine(line, yi, xi, sh, sw)) {
          minX = Math.min(minX, xi);
          maxX = Math.max(maxX, xi);
          minY = Math.min(minY, yi);
          maxY = Math.max(maxY, yi);
        }
      }
    }
    if (maxX < 0) {
      return gray; // Sin texto distinguible: se manda tal cual y que la IA lo diga.
    }
    int pad = Math.max(10, Math.min(w, h) / 60);
    int x0 = Math.max(0, minX * step - pad);
    int y0 = Math.max(0, minY * step - pad);
    int x1 = Math.min(w, (maxX + 1) * step + pad);
    int y1 = Math.min(h, (maxY + 1) * step + pad);
    BufferedImage cropped = new BufferedImage(x1 - x0, y1 - y0, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = cropped.createGraphics();
    g.drawImage(gray, 0, 0, x1 - x0, y1 - y0, x0, y0, x1, y1, null);
    g.dispose();
    return cropped;
  }

  private static void markLongRuns(boolean[][] ink, boolean[][] line, int index, int length, int minRun, boolean horizontal) {
    int start = -1;
    for (int i = 0; i <= length; i++) {
      boolean on = i < length && (horizontal ? ink[index][i] : ink[i][index]);
      if (on && start < 0) {
        start = i;
      } else if (!on && start >= 0) {
        if (i - start >= minRun) {
          for (int k = start; k < i; k++) {
            if (horizontal) {
              line[index][k] = true;
            } else {
              line[k][index] = true;
            }
          }
        }
        start = -1;
      }
    }
  }

  /** El píxel es parte de una línea o de su borde (una línea borrosa deja un halo de tinta alrededor). */
  private static boolean nearLine(boolean[][] line, int yi, int xi, int sh, int sw) {
    for (int dy = -2; dy <= 2; dy++) {
      for (int dx = -2; dx <= 2; dx++) {
        int y = yi + dy;
        int x = xi + dx;
        if (y >= 0 && y < sh && x >= 0 && x < sw && line[y][x]) {
          return true;
        }
      }
    }
    return false;
  }

  static BufferedImage grayscale(BufferedImage source) {
    int w = source.getWidth();
    int h = source.getHeight();
    BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int rgb = source.getRGB(x, y);
        int l = luminance(rgb);
        out.setRGB(x, y, (l << 16) | (l << 8) | l);
      }
    }
    return out;
  }

  /**
   * Estira el rango de grises: el papel (percentil 90) queda blanco y la tinta
   * más oscura (percentil 0.2) negra. En una foto borrosa la tinta se ve gris
   * claro; así recupera contraste sin inventar bordes.
   */
  static BufferedImage autoContrast(BufferedImage gray) {
    int w = gray.getWidth();
    int h = gray.getHeight();
    int[] histogram = new int[256];
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        histogram[gray.getRGB(x, y) & 0xFF]++;
      }
    }
    long total = (long) w * h;
    int lo = percentile(histogram, total, 0.002);
    int hi = percentile(histogram, total, 0.90);
    if (hi - lo < 30) {
      return gray; // Página casi uniforme (en blanco o negra): no hay nada que estirar.
    }
    int[] map = new int[256];
    for (int i = 0; i < 256; i++) {
      map[i] = clamp((int) Math.round((i - lo) * 255.0 / (hi - lo)));
    }
    BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int l = map[gray.getRGB(x, y) & 0xFF];
        out.setRGB(x, y, (l << 16) | (l << 8) | l);
      }
    }
    return out;
  }

  /**
   * Ángulo (grados, de -12 a 12) que mejor alinea los renglones: el que hace más
   * "picudo" el perfil de tinta por fila. Se calcula sobre una versión reducida
   * y solo con los píxeles de tinta, así que es barato.
   */
  static double skewAngle(BufferedImage gray) {
    int step = Math.max(1, Math.max(gray.getWidth(), gray.getHeight()) / 900);
    List<int[]> ink = new ArrayList<>();
    for (int y = 0; y < gray.getHeight(); y += step) {
      for (int x = 0; x < gray.getWidth(); x += step) {
        if ((gray.getRGB(x, y) & 0xFF) < INK) {
          ink.add(new int[] {x / step, y / step});
        }
      }
    }
    if (ink.size() < 50) {
      return 0;
    }
    int diagonal = (int) Math.hypot(gray.getWidth() / step, gray.getHeight() / step) + 2;
    double coarse = bestAngle(ink, diagonal, -12, 12, 0.25);
    // Afinado a 0.05°: con pasos de 0.25° quedaba una inclinación residual que dejaba el marco "chueco".
    return bestAngle(ink, diagonal, coarse - 0.25, coarse + 0.25, 0.05);
  }

  private static double bestAngle(List<int[]> ink, int diagonal, double from, double to, double stepDegrees) {
    double best = 0;
    double bestScore = -1;
    for (double angle = from; angle <= to + 1e-9; angle += stepDegrees) {
      double rad = Math.toRadians(angle);
      double sin = Math.sin(rad);
      double cos = Math.cos(rad);
      int[] rows = new int[2 * diagonal];
      for (int[] p : ink) {
        int row = (int) Math.round(p[1] * cos - p[0] * sin) + diagonal;
        if (row >= 0 && row < rows.length) {
          rows[row]++;
        }
      }
      double score = 0;
      for (int r : rows) {
        score += (double) r * r;
      }
      // Ante empate (texto ya derecho) gana el ángulo más chico.
      if (score > bestScore * 1.0005 || (Math.abs(score - bestScore) <= bestScore * 0.0005 && Math.abs(angle) < Math.abs(best))) {
        bestScore = score;
        best = angle;
      }
    }
    return Math.round(best * 100) / 100.0;
  }

  /** Gira la imagen (grados, positivo = sentido horario) sobre fondo blanco, sin recortarla. */
  static BufferedImage rotateDegrees(BufferedImage source, double degrees) {
    double rad = Math.toRadians(degrees);
    double sin = Math.abs(Math.sin(rad));
    double cos = Math.abs(Math.cos(rad));
    int w = source.getWidth();
    int h = source.getHeight();
    int nw = (int) Math.ceil(w * cos + h * sin);
    int nh = (int) Math.ceil(h * cos + w * sin);
    BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = out.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, nw, nh);
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.translate(nw / 2.0, nh / 2.0);
    g.rotate(rad);
    g.drawImage(source, -w / 2, -h / 2, null);
    g.dispose();
    return out;
  }

  static BufferedImage scale(BufferedImage source, double factor) {
    if (factor <= 1.0001) {
      return source;
    }
    int w = (int) Math.round(source.getWidth() * factor);
    int h = (int) Math.round(source.getHeight() * factor);
    BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = out.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.drawImage(source, 0, 0, w, h, null);
    g.dispose();
    return out;
  }

  /** Máscara de enfoque moderada (cantidad 0.7) sobre un desenfoque de caja del radio dado. */
  static BufferedImage sharpen(BufferedImage gray, int radius) {
    int w = gray.getWidth();
    int h = gray.getHeight();
    int[] src = new int[w * h];
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        src[y * w + x] = gray.getRGB(x, y) & 0xFF;
      }
    }
    int[] blurred = boxBlur(boxBlur(src, w, h, radius, true), w, h, radius, false);
    BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    for (int i = 0; i < src.length; i++) {
      int l = clamp((int) Math.round(src[i] + 0.7 * (src[i] - blurred[i])));
      out.setRGB(i % w, i / w, (l << 16) | (l << 8) | l);
    }
    return out;
  }

  /** Franjas horizontales que se traslapan (un renglón nunca queda cortado en todas). */
  static List<BufferedImage> bands(BufferedImage image) {
    List<BufferedImage> result = new ArrayList<>();
    int h = image.getHeight();
    if (h <= BAND_HEIGHT + BAND_OVERLAP) {
      result.add(image);
      return result;
    }
    int stride = BAND_HEIGHT - BAND_OVERLAP;
    for (int y = 0; y < h && result.size() < MAX_BANDS; y += stride) {
      int bottom = Math.min(h, y + BAND_HEIGHT);
      result.add(image.getSubimage(0, y, image.getWidth(), bottom - y));
      if (bottom == h) {
        break;
      }
    }
    return result;
  }

  private static int[] boxBlur(int[] src, int w, int h, int r, boolean horizontal) {
    int[] out = new int[src.length];
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int sum = 0;
        int n = 0;
        for (int k = -r; k <= r; k++) {
          int xx = horizontal ? x + k : x;
          int yy = horizontal ? y : y + k;
          if (xx >= 0 && xx < w && yy >= 0 && yy < h) {
            sum += src[yy * w + xx];
            n++;
          }
        }
        out[y * w + x] = sum / n;
      }
    }
    return out;
  }

  private static int percentile(int[] histogram, long total, double fraction) {
    long target = (long) Math.ceil(total * fraction);
    long seen = 0;
    for (int i = 0; i < 256; i++) {
      seen += histogram[i];
      if (seen >= target) {
        return i;
      }
    }
    return 255;
  }

  private static int luminance(int rgb) {
    return (int) Math.round(0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF));
  }

  private static int clamp(int v) {
    return Math.max(0, Math.min(255, v));
  }
}
