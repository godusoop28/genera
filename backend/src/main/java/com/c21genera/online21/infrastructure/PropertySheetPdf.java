package com.c21genera.online21.infrastructure;

import com.c21genera.online21.PropertyDossier.Details;
import com.c21genera.online21.PropertyDossier.Field;
import com.c21genera.online21.PropertyDossier.SheetVariant;
import com.c21genera.online21.PropertyListings.Listing;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Ficha técnica en PDF (carta), como las de 21Online: logo, título y clave, precio, foto principal y
 * galería, características, descripción, ubicación y, según la variante, el contacto del asesor.
 * 21Online no expone sus fichas por API; esta se arma con los mismos datos.
 */
@Component
class PropertySheetPdf {

  private static final Logger log = LoggerFactory.getLogger(PropertySheetPdf.class);
  private static final PDRectangle PAGE = PDRectangle.LETTER;
  private static final float MARGIN = 40;
  private static final Color GOLD = new Color(0xBE, 0xAF, 0x87);
  private static final Color INK = new Color(0x25, 0x28, 0x26);
  private static final Color MUTED = new Color(0x6E, 0x6A, 0x5F);
  private static final int MAX_PHOTOS = 7;
  private static final long MAX_PHOTO_BYTES = 6L * 1024 * 1024;
  /** Lo que se muestra en "Características" de la ficha (el resto de los datos son internos). */
  private static final List<String> FEATURE_LABELS =
      List.of(
          "Terreno (m²)", "Construcción (m²)", "Recámaras", "Baños", "Medios baños", "Estacionamientos", "Niveles construidos", "Edad (años)",
          "Estado de conservación", "Cocina", "Tipo de terreno", "Metros de frente", "Metros de fondo", "Elevadores", "Piso en que se encuentra",
          "Área de oficinas (m²)", "Alberca", "Acepta mascotas", "Amenidades", "Cuota de mantenimiento", "Acepta créditos", "Créditos que acepta");

  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();

  byte[] render(Details d, SheetVariant variant) {
    try (PDDocument pdf = new PDDocument()) {
      new Writer(pdf, d, variant).write();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      pdf.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo generar la ficha técnica", e);
    }
  }

  private final class Writer {
    private final PDDocument pdf;
    private final Details d;
    private final SheetVariant variant;
    private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private PDPageContentStream stream;
    private float y;

    Writer(PDDocument pdf, Details d, SheetVariant variant) {
      this.pdf = pdf;
      this.d = d;
      this.variant = variant;
    }

    void write() throws IOException {
      newPage();
      Listing l = d.listing();
      List<PDImageXObject> photos = loadPhotos();

      // Encabezado: logo de la oficina (salvo "sin logo") y clave.
      float headerHeight = 34;
      if (variant != SheetVariant.NO_LOGO) {
        PDImageXObject logo = logo();
        if (logo != null) {
          float h = headerHeight;
          float w = h * logo.getWidth() / logo.getHeight();
          stream.drawImage(logo, MARGIN, y - h, w, h);
        }
      }
      text(bold, 9, MUTED, "Clave " + (l.internalKey() != null ? l.internalKey() : l.key()), PAGE.getWidth() - MARGIN, y - 12, true);
      text(regular, 9, MUTED, "ID 21Online " + l.id(), PAGE.getWidth() - MARGIN, y - 24, true);
      y -= headerHeight + 14;
      rule();

      // Título, ubicación y precio.
      y -= 6;
      paragraph(bold, 17, INK, l.title(), PAGE.getWidth() - 2 * MARGIN, 20);
      String place = join(", ", l.neighborhood(), l.municipality(), l.state());
      if (!place.isBlank()) {
        paragraph(regular, 10.5f, MUTED, place, PAGE.getWidth() - 2 * MARGIN, 14);
      }
      y -= 4;
      String price = price(l);
      paragraph(bold, 16, GOLD.darker(), price + ("renta".equals(l.operation()) ? " / mes" : "") + "  ·  " + capitalize(l.operation()), PAGE.getWidth() - 2 * MARGIN, 20);
      y -= 6;

      // Foto principal y galería.
      if (!photos.isEmpty()) {
        float width = PAGE.getWidth() - 2 * MARGIN;
        float height = Math.min(250, width * 0.56f);
        drawCover(photos.get(0), MARGIN, y - height, width, height);
        y -= height + 6;
        int thumbs = Math.min(photos.size() - 1, 6);
        if (thumbs > 0) {
          float gap = 6;
          float tw = (width - gap * 5) / 6;
          float th = tw * 0.7f;
          for (int i = 0; i < thumbs; i++) {
            drawCover(photos.get(i + 1), MARGIN + i * (tw + gap), y - th, tw, th);
          }
          y -= th + 10;
        }
      }

      // Características en dos columnas.
      List<Field> features =
          d.fields().stream()
              .filter(f -> FEATURE_LABELS.contains(f.label()))
              .sorted(java.util.Comparator.comparingInt(f -> FEATURE_LABELS.indexOf(f.label())))
              .toList();
      if (!features.isEmpty()) {
        section("Características");
        float colWidth = (PAGE.getWidth() - 2 * MARGIN - 20) / 2;
        for (int i = 0; i < features.size(); i += 2) {
          ensure(16);
          float rowY = y;
          featureRow(features.get(i), MARGIN, colWidth);
          float leftY = y;
          if (i + 1 < features.size()) {
            y = rowY;
            featureRow(features.get(i + 1), MARGIN + colWidth + 20, colWidth);
          }
          y = Math.min(y, leftY);
        }
        y -= 4;
      }

      // Descripción.
      if (l.description() != null && !l.description().isBlank()) {
        section("Descripción");
        for (String para : l.description().split("\\r?\\n")) {
          if (para.isBlank()) {
            y -= 5;
            continue;
          }
          paragraph(regular, 10, INK, para.strip(), PAGE.getWidth() - 2 * MARGIN, 13.5f);
        }
      }

      // Contacto del asesor.
      if (variant == SheetVariant.ADVISOR && d.advisor() != null && d.advisor().name() != null) {
        section("Asesor");
        paragraph(bold, 11, INK, d.advisor().name(), PAGE.getWidth() - 2 * MARGIN, 15);
        String contact = join("   ·   ", d.advisor().mobile() != null ? "Cel. " + d.advisor().mobile() : null,
            d.advisor().officePhone() != null ? "Oficina " + d.advisor().officePhone() : null, d.advisor().email());
        if (!contact.isBlank()) {
          paragraph(regular, 10, INK, contact, PAGE.getWidth() - 2 * MARGIN, 14);
        }
      }

      // Pie: si es de la oficina, la leyenda de C21; "sin logo" queda neutra para compartir.
      stream.close();
      int pages = pdf.getNumberOfPages();
      for (int i = 0; i < pages; i++) {
        try (PDPageContentStream footer = new PDPageContentStream(pdf, pdf.getPage(i), PDPageContentStream.AppendMode.APPEND, true)) {
          String left = variant == SheetVariant.NO_LOGO ? "Ficha técnica" : "CENTURY 21 Genera · Cada oficina es de propiedad y operación independiente";
          footerText(footer, left, MARGIN, false);
          footerText(footer, "Página " + (i + 1) + " de " + pages, PAGE.getWidth() - MARGIN, true);
        }
      }
    }

    // --- Utilidades de dibujo -------------------------------------------------

    private void newPage() throws IOException {
      if (stream != null) {
        stream.close();
      }
      PDPage page = new PDPage(PAGE);
      pdf.addPage(page);
      stream = new PDPageContentStream(pdf, page);
      y = PAGE.getHeight() - MARGIN;
    }

    private void ensure(float needed) throws IOException {
      if (y - needed < MARGIN + 24) {
        newPage();
      }
    }

    private void rule() throws IOException {
      stream.setStrokingColor(GOLD);
      stream.setLineWidth(1.2f);
      stream.moveTo(MARGIN, y);
      stream.lineTo(PAGE.getWidth() - MARGIN, y);
      stream.stroke();
    }

    private void section(String title) throws IOException {
      ensure(40);
      y -= 10;
      text(bold, 11.5f, INK, title.toUpperCase(Locale.ROOT), MARGIN, y - 11, false);
      y -= 16;
      rule();
      y -= 10;
    }

    private void featureRow(Field f, float x, float width) throws IOException {
      float labelWidth = width * 0.5f;
      List<String> value = wrap(regular, 9.5f, safe(f.value()), width - labelWidth);
      text(regular, 9.5f, MUTED, f.label(), x, y - 9.5f, false);
      for (String line : value) {
        text(bold, 9.5f, INK, line, x + labelWidth, y - 9.5f, false);
        y -= 13;
      }
    }

    private void paragraph(PDType1Font font, float size, Color color, String text, float width, float leading) throws IOException {
      for (String line : wrap(font, size, safe(text), width)) {
        ensure(leading);
        text(font, size, color, line, MARGIN, y - size, false);
        y -= leading;
      }
    }

    private void text(PDType1Font font, float size, Color color, String text, float x, float baseline, boolean alignRight) throws IOException {
      String s = safe(text);
      float drawX = alignRight ? x - font.getStringWidth(s) / 1000 * size : x;
      stream.beginText();
      stream.setFont(font, size);
      stream.setNonStrokingColor(color);
      stream.newLineAtOffset(drawX, baseline);
      stream.showText(s);
      stream.endText();
    }

    private void footerText(PDPageContentStream footer, String text, float x, boolean alignRight) throws IOException {
      String s = safe(text);
      float size = 8;
      float drawX = alignRight ? x - regular.getStringWidth(s) / 1000 * size : x;
      footer.beginText();
      footer.setFont(regular, size);
      footer.setNonStrokingColor(MUTED);
      footer.newLineAtOffset(drawX, MARGIN - 16);
      footer.showText(s);
      footer.endText();
    }

    /** Dibuja la imagen recortada al centro para llenar el recuadro (como object-fit: cover). */
    private void drawCover(PDImageXObject image, float x, float bottom, float width, float height) throws IOException {
      float scale = Math.max(width / image.getWidth(), height / image.getHeight());
      float w = image.getWidth() * scale;
      float h = image.getHeight() * scale;
      stream.saveGraphicsState();
      stream.addRect(x, bottom, width, height);
      stream.clip();
      stream.drawImage(image, x + (width - w) / 2, bottom + (height - h) / 2, w, h);
      stream.restoreGraphicsState();
    }

    private List<String> wrap(PDType1Font font, float size, String text, float width) throws IOException {
      List<String> lines = new ArrayList<>();
      StringBuilder line = new StringBuilder();
      for (String word : text.split("\\s+")) {
        String candidate = line.isEmpty() ? word : line + " " + word;
        if (font.getStringWidth(candidate) / 1000 * size <= width || line.isEmpty()) {
          line.setLength(0);
          line.append(candidate);
        } else {
          lines.add(line.toString());
          line.setLength(0);
          line.append(word);
        }
      }
      if (!line.isEmpty()) {
        lines.add(line.toString());
      }
      return lines;
    }

    /** Helvetica (WinAnsi) no tiene todos los caracteres: lo que no se puede escribir se reemplaza. */
    private String safe(String text) {
      if (text == null) {
        return "";
      }
      StringBuilder out = new StringBuilder(text.length());
      for (char c : text.replace("→", "-").replace("²", "2").replace('\t', ' ').toCharArray()) {
        if (c == '\r' || c == '\n') {
          out.append(' ');
          continue;
        }
        try {
          regular.encode(String.valueOf(c));
          out.append(c);
        } catch (IOException | IllegalArgumentException e) {
          out.append(' ');
        }
      }
      return out.toString();
    }

    private PDImageXObject logo() {
      try (InputStream in = new ClassPathResource("contract/logo.png").getInputStream()) {
        return PDImageXObject.createFromByteArray(pdf, in.readAllBytes(), "logo");
      } catch (IOException e) {
        return null;
      }
    }

    private List<PDImageXObject> loadPhotos() {
      List<PDImageXObject> out = new ArrayList<>();
      for (String url : d.photos()) {
        if (out.size() >= MAX_PHOTOS) {
          break;
        }
        PDImageXObject image = download(url);
        if (image != null) {
          out.add(image);
        }
      }
      return out;
    }

    private PDImageXObject download(String url) {
      try {
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
          return null;
        }
        HttpResponse<byte[]> response =
            http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        byte[] body = response.body();
        if (response.statusCode() != 200 || body == null || body.length == 0 || body.length > MAX_PHOTO_BYTES) {
          return null;
        }
        // Se pasa por ImageIO (lee JPG, PNG y WebP) para que el PDF acepte cualquier formato.
        java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(body));
        if (image == null) {
          return null;
        }
        return LosslessFactory.createFromImage(pdf, downscale(image, 1400));
      } catch (IOException | RuntimeException e) {
        log.debug("No se pudo descargar una foto para la ficha ({})", e.getClass().getSimpleName());
        return null;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
      }
    }
  }

  private static java.awt.image.BufferedImage downscale(java.awt.image.BufferedImage image, int maxSide) {
    int w = image.getWidth();
    int h = image.getHeight();
    double factor = Math.min(1.0, (double) maxSide / Math.max(w, h));
    if (factor >= 1.0 && image.getType() == java.awt.image.BufferedImage.TYPE_INT_RGB) {
      return image;
    }
    int nw = Math.max(1, (int) (w * factor));
    int nh = Math.max(1, (int) (h * factor));
    java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(nw, nh, java.awt.image.BufferedImage.TYPE_INT_RGB);
    java.awt.Graphics2D g = out.createGraphics();
    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.drawImage(image, 0, 0, nw, nh, null);
    g.dispose();
    return out;
  }

  private static String price(Listing l) {
    BigDecimal amount = "renta".equals(l.operation()) ? l.rentPrice() : l.salePrice();
    if (amount == null) {
      return "Precio a consultar";
    }
    NumberFormat f = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX"));
    f.setMaximumFractionDigits(0);
    return f.format(amount) + " " + (l.currency() != null ? l.currency() : "MXN");
  }

  private static String capitalize(String s) {
    return s == null || s.isBlank() ? "Venta" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private static String join(String sep, String... parts) {
    List<String> kept = new ArrayList<>();
    for (String p : parts) {
      if (p != null && !p.isBlank()) {
        kept.add(p.strip());
      }
    }
    return String.join(sep, kept);
  }
}
