package com.c21genera.feasibility.infrastructure;

import com.c21genera.feasibility.domain.PresentationData;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Mapa estático de la lámina 8: teselas reales de OpenStreetMap con el inmueble (marcador
 * dorado) y cada comparable con su número C1..Cn y una forma por grupo (por arriba ▲,
 * similares ●, por debajo ▼). Las ubicaciones aproximadas llevan contorno punteado. Es una
 * imagen PNG incrustada en el PowerPoint, así que se ve sin conexión.
 */
@Component
public class StaticMapRenderer {

  private static final Logger log = LoggerFactory.getLogger(StaticMapRenderer.class);

  public static final int WIDTH = 1520;
  public static final int HEIGHT = 1000;
  private static final int TILE = 256;

  static final Color GOLD = new Color(0xBE, 0xAF, 0x87);
  static final Color INK = new Color(0x25, 0x25, 0x27);
  static final Color GREY = new Color(0x80, 0x82, 0x85);

  public record Point(String label, String group, double lat, double lng, boolean approximate) {}

  private final FeasibilityProperties properties;
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();

  public StaticMapRenderer(FeasibilityProperties properties) {
    this.properties = properties;
  }

  /** subject: el inmueble analizado (puede ser null si no se pudo ubicar). */
  public byte[] render(Point subject, List<Point> comparables) {
    List<Point> all = new java.util.ArrayList<>(comparables);
    if (subject != null) {
      all.add(subject);
    }
    BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setColor(new Color(0xE4, 0xE8, 0xE9));
    g.fillRect(0, 0, WIDTH, HEIGHT);

    if (all.isEmpty()) {
      drawCentered(g, "Sin ubicaciones verificables para mostrar en el mapa", WIDTH / 2, HEIGHT / 2);
      g.dispose();
      return png(image);
    }

    double minLat = all.stream().mapToDouble(Point::lat).min().orElseThrow();
    double maxLat = all.stream().mapToDouble(Point::lat).max().orElseThrow();
    double minLng = all.stream().mapToDouble(Point::lng).min().orElseThrow();
    double maxLng = all.stream().mapToDouble(Point::lng).max().orElseThrow();
    int zoom = 16;
    while (zoom > 7) {
      double w = Math.abs(worldX(maxLng, zoom) - worldX(minLng, zoom));
      double h = Math.abs(worldY(minLat, zoom) - worldY(maxLat, zoom));
      if (w <= WIDTH * 0.78 && h <= HEIGHT * 0.74) {
        break;
      }
      zoom--;
    }
    double centerX = (worldX(minLng, zoom) + worldX(maxLng, zoom)) / 2;
    double centerY = (worldY(maxLat, zoom) + worldY(minLat, zoom)) / 2;
    double originX = centerX - WIDTH / 2.0;
    double originY = centerY - HEIGHT / 2.0;

    boolean tilesOk = drawTiles(g, zoom, originX, originY);
    // Velo claro para que los marcadores resalten sobre el mapa.
    g.setColor(new Color(255, 255, 255, 60));
    g.fillRect(0, 0, WIDTH, HEIGHT);

    for (Point p : comparables) {
      drawComparable(g, p, worldX(p.lng(), zoom) - originX, worldY(p.lat(), zoom) - originY);
    }
    if (subject != null) {
      drawSubject(g, worldX(subject.lng(), zoom) - originX, worldY(subject.lat(), zoom) - originY);
    }

    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
    String attribution = tilesOk ? "© Colaboradores de OpenStreetMap · CARTO" : "Mapa base no disponible al generar; posiciones relativas";
    FontMetrics fm = g.getFontMetrics();
    int aw = fm.stringWidth(attribution) + 16;
    g.setColor(new Color(255, 255, 255, 220));
    g.fillRect(WIDTH - aw, HEIGHT - 30, aw, 30);
    g.setColor(INK);
    g.drawString(attribution, WIDTH - aw + 8, HEIGHT - 8);
    g.dispose();
    return png(image);
  }

  private boolean drawTiles(Graphics2D g, int zoom, double originX, double originY) {
    int firstX = (int) Math.floor(originX / TILE);
    int firstY = (int) Math.floor(originY / TILE);
    int lastX = (int) Math.floor((originX + WIDTH) / TILE);
    int lastY = (int) Math.floor((originY + HEIGHT) / TILE);
    int max = 1 << zoom;
    int loaded = 0;
    for (int tx = firstX; tx <= lastX; tx++) {
      for (int ty = firstY; ty <= lastY; ty++) {
        if (ty < 0 || ty >= max) {
          continue;
        }
        BufferedImage tile = fetchTile(zoom, Math.floorMod(tx, max), ty);
        if (tile != null) {
          g.drawImage(tile, (int) Math.round(tx * TILE - originX), (int) Math.round(ty * TILE - originY), null);
          loaded++;
        }
      }
    }
    return loaded > 0;
  }

  /** OpenStreetMap; si no entrega la tesela (bloqueo, límite o red), el mapa base alterno. */
  private BufferedImage fetchTile(int z, int x, int y) {
    BufferedImage tile = fetchTile(properties.tileUrl(), z, x, y);
    return tile != null ? tile : fetchTile(properties.tileFallbackUrl(), z, x, y);
  }

  private BufferedImage fetchTile(String template, int z, int x, int y) {
    String url = template.replace("{z}", String.valueOf(z)).replace("{x}", String.valueOf(x)).replace("{y}", String.valueOf(y));
    try {
      HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("User-Agent", properties.userAgent()).GET().build();
      HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() != 200) {
        return null;
      }
      return ImageIO.read(new ByteArrayInputStream(response.body()));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    } catch (Exception e) {
      log.debug("Tesela {}/{}/{} no disponible: {}", z, x, y, e.getMessage());
      return null;
    }
  }

  private void drawSubject(Graphics2D g, double x, double y) {
    int r = 42;
    g.setColor(new Color(0, 0, 0, 70));
    g.fill(new Ellipse2D.Double(x - r + 3, y - r + 4, r * 2, r * 2));
    g.setColor(GOLD);
    g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
    g.setColor(INK);
    g.setStroke(new BasicStroke(5f));
    g.draw(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
    g.fill(star(x, y, 24, 10));
    label(g, "Tu propiedad", x, y + r + 30, GOLD, INK);
  }

  private void drawComparable(Graphics2D g, Point p, double x, double y) {
    int r = 32;
    Color fill = PresentationData.BELOW.equals(p.group()) ? GREY : INK;
    java.awt.Shape shape =
        switch (p.group()) {
          case PresentationData.ABOVE -> triangle(x, y, r + 4, true);
          case PresentationData.BELOW -> triangle(x, y, r + 4, false);
          default -> new Ellipse2D.Double(x - r, y - r, r * 2, r * 2);
        };
    g.setColor(fill);
    g.fill(shape);
    g.setColor(PresentationData.SIMILAR.equals(p.group()) ? GOLD : Color.WHITE);
    g.setStroke(p.approximate() ? new BasicStroke(3.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {6f, 5f}, 0f) : new BasicStroke(3.5f));
    g.draw(shape);
    g.setColor(Color.WHITE);
    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, p.label().length() > 2 ? 22 : 25));
    FontMetrics fm = g.getFontMetrics();
    double dy = PresentationData.ABOVE.equals(p.group()) ? 10 : PresentationData.BELOW.equals(p.group()) ? -6 : 0;
    g.drawString(p.label(), (float) (x - fm.stringWidth(p.label()) / 2.0), (float) (y + dy + fm.getAscent() / 2.0 - 2));
  }

  private static void label(Graphics2D g, String text, double x, double y, Color bg, Color fg) {
    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
    FontMetrics fm = g.getFontMetrics();
    int w = fm.stringWidth(text) + 24;
    g.setColor(bg);
    g.fillRoundRect((int) (x - w / 2.0), (int) (y - 26), w, 38, 12, 12);
    g.setColor(fg);
    g.drawString(text, (float) (x - fm.stringWidth(text) / 2.0), (float) (y + 4));
  }

  private static void drawCentered(Graphics2D g, String text, int x, int y) {
    g.setColor(INK);
    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 30));
    FontMetrics fm = g.getFontMetrics();
    g.drawString(text, x - fm.stringWidth(text) / 2, y);
  }

  private static Polygon triangle(double x, double y, int r, boolean up) {
    Polygon p = new Polygon();
    int dir = up ? -1 : 1;
    p.addPoint((int) x, (int) (y + dir * r));
    p.addPoint((int) (x - r * 0.95), (int) (y - dir * r * 0.65));
    p.addPoint((int) (x + r * 0.95), (int) (y - dir * r * 0.65));
    return p;
  }

  private static Polygon star(double cx, double cy, double outer, double inner) {
    Polygon p = new Polygon();
    for (int i = 0; i < 10; i++) {
      double r = i % 2 == 0 ? outer : inner;
      double a = Math.PI / 5 * i - Math.PI / 2;
      p.addPoint((int) Math.round(cx + r * Math.cos(a)), (int) Math.round(cy + r * Math.sin(a)));
    }
    return p;
  }

  static double worldX(double lng, int zoom) {
    return (lng + 180.0) / 360.0 * TILE * (1 << zoom);
  }

  static double worldY(double lat, int zoom) {
    double sin = Math.sin(Math.toRadians(lat));
    return (0.5 - Math.log((1 + sin) / (1 - sin)) / (4 * Math.PI)) * TILE * (1 << zoom);
  }

  private static byte[] png(BufferedImage image) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ImageIO.write(image, "png", out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo generar el mapa", e);
    }
  }
}
