package com.c21genera.receipts.infrastructure;

import com.c21genera.receipts.domain.DocumentReceipt.Header;
import com.c21genera.receipts.domain.ReceivedItem;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.util.Matrix;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Control de Cambios #31: PDF del acuse de recepción (PDFBox, carta). El
 * borrador lleva marca de agua "BORRADOR — SIN FIRMA" y, si los textos no
 * están aprobados por el área legal, una leyenda que lo indica; solo la
 * versión firmada lleva la firma del representante.
 */
@Component
public class ReceiptPdfRenderer {

  private static final PDRectangle PAGE = PDRectangle.LETTER;
  private static final float MARGIN = 60;
  private static final float BODY = 9.5f;
  private static final ZoneId MX = ZoneId.of("America/Mexico_City");
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-MX"));
  private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-MX"));

  /** Firma del representante (solo en la versión firmada en el sistema). */
  public record Signature(String name, String title, byte[] png, Instant at, String draftSha256) {}

  public byte[] render(Header h, String number, List<ReceivedItem> items, Instant issuedAt, Signature signature) {
    try (PDDocument pdf = new PDDocument()) {
      new Writer(pdf, h, number, items, issuedAt, signature).write();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      pdf.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static final class Writer {
    private final PDDocument pdf;
    private final Header h;
    private final String number;
    private final List<ReceivedItem> items;
    private final Instant issuedAt;
    private final Signature signature;
    private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final PDImageXObject logo;
    private final List<PDPage> pages = new ArrayList<>();
    private PDPageContentStream cs;
    private float y;

    Writer(PDDocument pdf, Header h, String number, List<ReceivedItem> items, Instant issuedAt, Signature signature) throws IOException {
      this.pdf = pdf;
      this.h = h;
      this.number = number;
      this.items = items;
      this.issuedAt = issuedAt;
      this.signature = signature;
      this.logo = loadLogo();
    }

    private PDImageXObject loadLogo() {
      try (InputStream in = new ClassPathResource("contract/logo.png").getInputStream()) {
        return PDImageXObject.createFromByteArray(pdf, in.readAllBytes(), "logo");
      } catch (IOException e) {
        return null;
      }
    }

    void write() throws IOException {
      newPage();
      title("ACUSE DE RECEPCIÓN DE DOCUMENTOS");
      paragraph("y responsabilidad de su custodia, resguardo y tratamiento", regular, 10, Color.DARK_GRAY);
      y -= 8;
      keyValue("Acuse número", number);
      keyValue("Fecha de emisión", DATE.format(issuedAt.atZone(MX)));
      keyValue("Cliente", h.clientName());
      keyValue("Expediente", h.folio() + (h.propertyKey() != null ? "  ·  Clave del inmueble " + h.propertyKey() : ""));
      keyValue("Entidad responsable", h.responsibleEntity());
      y -= 6;

      section("1. Documentos recibidos");
      paragraph(
          "Se relacionan todos los archivos efectivamente recibidos en el expediente hasta la fecha de emisión, incluidos los documentos adicionales y las versiones actualizadas. Los documentos pendientes no figuran en esta relación.",
          regular,
          BODY,
          Color.BLACK);
      y -= 4;
      table();

      section("2. Aviso de privacidad");
      String notice =
          h.privacyNoticeVersion() == null
              ? "Aviso de privacidad vigente de " + h.responsibleEntity() + "."
              : "Aviso de privacidad versión " + h.privacyNoticeVersion() + (h.privacyNoticeSha256() != null ? " (huella SHA-256 " + h.privacyNoticeSha256().substring(0, 16) + "…)" : "") + ".";
      paragraph(notice + (h.privacyAcceptedAt() != null ? " Aceptado por el cliente el " + DATE_TIME.format(h.privacyAcceptedAt().atZone(MX)) + "." : ""), regular, BODY, Color.BLACK);
      paragraph(h.privacyText(), regular, BODY, Color.BLACK);

      section("3. Constancia de recepción y responsabilidad");
      paragraph(h.responsibilityText(), regular, BODY, Color.BLACK);
      paragraph(
          "Este acuse no sustituye la aceptación o firma del aviso de privacidad por parte del cliente ni los consentimientos que correspondan.", regular, 8.5f, Color.DARK_GRAY);

      signatureBlock();
      decorate();
    }

    private void newPage() throws IOException {
      if (cs != null) {
        cs.close();
      }
      PDPage page = new PDPage(PAGE);
      pdf.addPage(page);
      pages.add(page);
      cs = new PDPageContentStream(pdf, page);
      y = PAGE.getHeight() - MARGIN;
      if (logo != null) {
        float w = 120;
        float hgt = w * logo.getHeight() / logo.getWidth();
        cs.drawImage(logo, PAGE.getWidth() - MARGIN - w, y - hgt + 10, w, hgt);
        y -= Math.max(hgt, 30);
      }
      y -= 10;
    }

    private void ensure(float needed) throws IOException {
      if (y - needed < MARGIN + 30) {
        newPage();
      }
    }

    private void title(String text) throws IOException {
      ensure(24);
      text(MARGIN, y, bold, 14, text, new Color(36, 39, 35));
      y -= 18;
    }

    private void section(String text) throws IOException {
      ensure(40);
      y -= 10;
      cs.setNonStrokingColor(new Color(240, 236, 226));
      cs.addRect(MARGIN, y - 4, PAGE.getWidth() - 2 * MARGIN, 16);
      cs.fill();
      text(MARGIN + 6, y, bold, 10, text, new Color(36, 39, 35));
      y -= 20;
    }

    private void keyValue(String key, String value) throws IOException {
      float width = PAGE.getWidth() - 2 * MARGIN - 130;
      List<String> lines = wrap(clean(value), regular, BODY, width);
      ensure(lines.size() * 13f);
      text(MARGIN, y, bold, BODY, key + ":", Color.BLACK);
      for (String line : lines) {
        text(MARGIN + 130, y, regular, BODY, line, Color.BLACK);
        y -= 13;
      }
    }

    private void paragraph(String value, PDType1Font font, float size, Color color) throws IOException {
      for (String line : wrap(clean(value), font, size, PAGE.getWidth() - 2 * MARGIN)) {
        ensure(size * 1.5f);
        text(MARGIN, y, font, size, line, color);
        y -= size * 1.45f;
      }
      y -= 4;
    }

    private void table() throws IOException {
      float[] widths = {22, 230, 110, 45, 85};
      String[] head = {"#", "Documento", "Participante", "Versión", "Recibido"};
      row(head, widths, bold, true);
      int i = 1;
      for (ReceivedItem item : items) {
        row(
            new String[] {
              String.valueOf(i++),
              item.name() + (item.current() ? "" : " (versión anterior)"),
              item.participant() == null ? "—" : item.participant(),
              "V" + item.versionNumber(),
              item.receivedAt() == null ? "—" : DATE_TIME.format(item.receivedAt().atZone(MX))
            },
            widths,
            regular,
            false);
      }
      y -= 6;
    }

    private void row(String[] cells, float[] widths, PDType1Font font, boolean header) throws IOException {
      float size = 8.5f;
      List<List<String>> wrapped = new ArrayList<>();
      int lines = 1;
      for (int c = 0; c < cells.length; c++) {
        List<String> w = wrap(clean(cells[c]), font, size, widths[c] - 6);
        wrapped.add(w);
        lines = Math.max(lines, w.size());
      }
      float height = lines * 11f + 6;
      ensure(height);
      float x = MARGIN;
      if (header) {
        cs.setNonStrokingColor(new Color(36, 39, 35));
        cs.addRect(MARGIN, y - height + 9, sum(widths), height);
        cs.fill();
      }
      cs.setStrokingColor(new Color(200, 200, 200));
      cs.setLineWidth(0.5f);
      cs.addRect(MARGIN, y - height + 9, sum(widths), height);
      cs.stroke();
      for (int c = 0; c < cells.length; c++) {
        float ly = y;
        for (String line : wrapped.get(c)) {
          text(x + 3, ly, font, size, line, header ? Color.WHITE : Color.BLACK);
          ly -= 11;
        }
        x += widths[c];
      }
      y -= height;
    }

    private void signatureBlock() throws IOException {
      ensure(150);
      y -= 30;
      float lineX = MARGIN + 110;
      float lineW = 260;
      if (signature != null && signature.png() != null) {
        PDImageXObject image = PDImageXObject.createFromByteArray(pdf, signature.png(), "firma");
        float w = 180;
        float hgt = Math.min(70, w * image.getHeight() / image.getWidth());
        cs.drawImage(image, lineX + (lineW - w) / 2, y - 6, w, hgt);
        y -= 6;
      } else {
        y -= 50;
      }
      cs.setStrokingColor(Color.BLACK);
      cs.setLineWidth(0.7f);
      cs.moveTo(lineX, y);
      cs.lineTo(lineX + lineW, y);
      cs.stroke();
      y -= 12;
      String name = signature != null ? signature.name() : (h.representativeName() != null ? h.representativeName() : "Nombre del representante legal");
      String role = signature != null && signature.title() != null ? signature.title() : (h.representativeTitle() != null ? h.representativeTitle() : "Representante legal");
      center(clean(name), bold, BODY, lineX, lineW);
      y -= 12;
      center(clean(role + " · " + h.responsibleEntity()), regular, 8.5f, lineX, lineW);
      y -= 16;
      if (signature != null) {
        paragraph(
            "Firmado electrónicamente en el sistema por "
                + signature.name()
                + " el "
                + DATE_TIME.format(signature.at().atZone(MX))
                + " (confirmó su identidad con su contraseña). Huella SHA-256 del borrador que se firmó: "
                + (signature.draftSha256() == null ? "—" : signature.draftSha256())
                + ".",
            regular,
            7.5f,
            Color.DARK_GRAY);
      }
    }

    /** Marcas de agua, leyenda legal y pie con número de página. */
    private void decorate() throws IOException {
      cs.close();
      int total = pages.size();
      for (int i = 0; i < total; i++) {
        try (PDPageContentStream c = new PDPageContentStream(pdf, pages.get(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
          if (signature == null) {
            PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
            gs.setNonStrokingAlphaConstant(0.12f);
            c.setGraphicsStateParameters(gs);
            c.beginText();
            c.setFont(bold, 54);
            c.setNonStrokingColor(new Color(180, 30, 30));
            c.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(40), 120, 220));
            c.showText("BORRADOR - SIN FIRMA");
            c.endText();
            PDExtendedGraphicsState opaque = new PDExtendedGraphicsState();
            opaque.setNonStrokingAlphaConstant(1f);
            c.setGraphicsStateParameters(opaque);
          }
          if (!h.textsApproved()) {
            c.beginText();
            c.setFont(bold, 8);
            c.setNonStrokingColor(new Color(180, 30, 30));
            c.newLineAtOffset(MARGIN, PAGE.getHeight() - 28);
            c.showText("TEXTO PENDIENTE DE APROBACIÓN LEGAL - NO ENTREGAR AL CLIENTE");
            c.endText();
          }
          c.beginText();
          c.setFont(regular, 7.5f);
          c.setNonStrokingColor(Color.GRAY);
          c.newLineAtOffset(MARGIN, 30);
          c.showText(clean("Acuse " + number + " · " + h.responsibleEntity() + " · Página " + (i + 1) + " de " + total));
          c.endText();
        }
      }
    }

    private void center(String value, PDType1Font font, float size, float x, float width) throws IOException {
      float w = font.getStringWidth(value) / 1000 * size;
      text(x + Math.max(0, (width - w) / 2), y, font, size, value, Color.BLACK);
    }

    private void text(float x, float yy, PDType1Font font, float size, String value, Color color) throws IOException {
      cs.beginText();
      cs.setFont(font, size);
      cs.setNonStrokingColor(color);
      cs.newLineAtOffset(x, yy);
      cs.showText(value);
      cs.endText();
    }

    private static float sum(float[] values) {
      float s = 0;
      for (float v : values) {
        s += v;
      }
      return s;
    }

    private List<String> wrap(String value, PDType1Font font, float size, float width) throws IOException {
      List<String> lines = new ArrayList<>();
      for (String paragraph : value.split("\n")) {
        StringBuilder line = new StringBuilder();
        for (String word : paragraph.split(" ")) {
          String candidate = line.isEmpty() ? word : line + " " + word;
          if (font.getStringWidth(candidate) / 1000 * size > width && !line.isEmpty()) {
            lines.add(line.toString());
            line = new StringBuilder(word);
          } else {
            line = new StringBuilder(candidate);
          }
        }
        lines.add(line.toString());
      }
      return lines;
    }

    /** Solo caracteres que las fuentes estándar del PDF pueden dibujar (acentos y ñ sí). */
    private String clean(String value) {
      if (value == null) {
        return "";
      }
      String normalized = value.replace('‘', '\'').replace('’', '\'').replace('“', '"').replace('”', '"').replace('—', '-').replace('–', '-').replace('\t', ' ').replace('\r', ' ');
      StringBuilder out = new StringBuilder();
      for (char ch : normalized.toCharArray()) {
        if (ch == '\n') {
          out.append(ch);
          continue;
        }
        try {
          regular.encode(String.valueOf(ch));
          out.append(ch);
        } catch (IllegalArgumentException | IOException e) {
          out.append('?');
        }
      }
      return out.toString();
    }
  }
}
