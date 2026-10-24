package com.c21genera.feasibility.infrastructure;

import com.c21genera.feasibility.infrastructure.OpenAiBackgroundClient.Citation;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageSz;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.stereotype.Component;

/**
 * Word editable del reporte "Analista de Factibilidad 3.0". La IA lo entrega en Markdown
 * (títulos, negritas, viñetas, tablas con "|" y ligas); aquí se traduce a párrafos y tablas
 * reales de Word con la paleta de CENTURY 21 (negro #252527, dorado #BEAF86, gris #E4E8E9).
 */
@Component
public class FeasibilityDocxRenderer {

  static final String INK = "252527";
  static final String GOLD = "BEAF86";
  static final String LIGHT = "E4E8E9";
  static final String MUTED = "6E6A5F";
  private static final String FONT = "Arial";

  /** rent: factibilidad para renta (la portada lo dice). */
  public record Cover(String propertyTitle, String folio, String address, String advisor, String dateText, boolean rent) {}

  private static final Pattern INLINE = Pattern.compile("\\*\\*(.+?)\\*\\*|\\[([^\\]]+)\\]\\((https?://[^)\\s]+)\\)|(https?://[^\\s)\\]|>]+)|\\*(?!\\s)([^*]+?)\\*|`([^`]+)`");
  private static final Pattern NUMBERED = Pattern.compile("^(\\d+)[.)]\\s+(.*)$");

  public byte[] render(Cover cover, String markdown, List<Citation> sources) {
    try (XWPFDocument doc = new XWPFDocument()) {
      pageSetup(doc);
      footer(doc);
      coverPage(doc, cover);
      body(doc, markdown == null ? "" : markdown);
      if (sources != null && !sources.isEmpty()) {
        heading(doc, "ANEXO. FUENTES CITADAS POR LA BÚSQUEDA WEB", 1);
        paragraph(doc, "Publicaciones y páginas que la IA consultó para este análisis (" + cover.dateText() + "). Los precios publicados son precios de oferta, no de cierre.", false);
        for (Citation c : sources) {
          XWPFParagraph p = bulletParagraph(doc, "•");
          inline(p, (c.title() == null || c.title().isBlank() ? c.url() : c.title()) + " — ", 10, false, INK);
          link(p, c.url(), c.url(), 10);
        }
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo generar el Word de la factibilidad", e);
    }
  }

  // ------------------------------------------------------------------ estructura

  private static void pageSetup(XWPFDocument doc) {
    CTSectPr sect = doc.getDocument().getBody().isSetSectPr() ? doc.getDocument().getBody().getSectPr() : doc.getDocument().getBody().addNewSectPr();
    CTPageSz size = sect.isSetPgSz() ? sect.getPgSz() : sect.addNewPgSz();
    size.setW(BigInteger.valueOf(12240));
    size.setH(BigInteger.valueOf(15840));
    CTPageMar margin = sect.isSetPgMar() ? sect.getPgMar() : sect.addNewPgMar();
    margin.setTop(BigInteger.valueOf(1134));
    margin.setBottom(BigInteger.valueOf(1134));
    margin.setLeft(BigInteger.valueOf(1134));
    margin.setRight(BigInteger.valueOf(1134));
    margin.setFooter(BigInteger.valueOf(500));
  }

  private static void footer(XWPFDocument doc) {
    XWPFFooter footer = doc.createFooter(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
    XWPFParagraph p = footer.createParagraph();
    p.setAlignment(ParagraphAlignment.CENTER);
    XWPFRun r = p.createRun();
    r.setText("CENTURY 21 Genera · Factibilidad comercial · Documento de trabajo. Cada oficina es de propiedad y operación independiente.");
    r.setFontFamily(FONT);
    r.setFontSize(7.5);
    r.setColor(MUTED);
  }

  private static void coverPage(XWPFDocument doc, Cover cover) {
    XWPFParagraph logo = doc.createParagraph();
    logo.setAlignment(ParagraphAlignment.LEFT);
    try (InputStream in = FeasibilityDocxRenderer.class.getResourceAsStream("/feasibility/logo-vertical-sobre-claro.jpg")) {
      if (in != null) {
        logo.createRun().addPicture(in, XWPFDocument.PICTURE_TYPE_JPEG, "logo.jpg", Units.toEMU(190), Units.toEMU(110));
      }
    } catch (Exception e) {
      // Sin logo el documento sigue siendo útil.
    }
    spacer(doc, 60);
    XWPFParagraph kicker = doc.createParagraph();
    run(kicker, cover.rent() ? "ANÁLISIS DE FACTIBILIDAD COMERCIAL · RENTA" : "ANÁLISIS DE FACTIBILIDAD COMERCIAL · VENTA", 12, true, GOLD);
    XWPFParagraph title = doc.createParagraph();
    title.setSpacingAfter(120);
    run(title, nonBlank(cover.propertyTitle(), "Inmueble en análisis"), 26, true, INK);
    goldRule(doc);
    keyValue(doc, "Ubicación", nonBlank(cover.address(), "Dato pendiente por confirmar"));
    keyValue(doc, "Expediente", cover.folio());
    keyValue(doc, "Asesor", nonBlank(cover.advisor(), "Sin asignar"));
    keyValue(doc, "Fecha del análisis", cover.dateText());
    spacer(doc, 240);
    paragraph(
        doc,
        "Reporte elaborado con apoyo de inteligencia artificial (Analista de Factibilidad 3.0) a partir de los documentos del expediente, los datos capturados por el asesor y la investigación de mercado en fuentes públicas. Es una estimación comercial orientativa: no sustituye un avalúo practicado por perito valuador. Los precios de publicaciones son precios de oferta, no de cierre."
            + (cover.rent() ? " Los montos son rentas mensuales." : ""),
        false,
        9,
        MUTED);
    doc.createParagraph().setPageBreak(true);
  }

  private void body(XWPFDocument doc, String markdown) {
    String[] lines = markdown.replace("\r", "").split("\n");
    List<String> table = new ArrayList<>();
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      String trimmed = line.strip();
      if (trimmed.startsWith("|")) {
        table.add(trimmed);
        continue;
      }
      if (!table.isEmpty()) {
        table(doc, table);
        table.clear();
      }
      if (trimmed.isEmpty() || trimmed.matches("^(-{3,}|\\*{3,}|_{3,})$")) {
        continue;
      }
      if (trimmed.startsWith("#")) {
        int level = 0;
        while (level < trimmed.length() && trimmed.charAt(level) == '#') {
          level++;
        }
        heading(doc, stripMarks(trimmed.substring(level).strip()), Math.min(level, 4));
        continue;
      }
      if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ")) {
        int indent = (line.length() - line.stripLeading().length()) >= 2 ? 2 : 1;
        XWPFParagraph p = bulletParagraph(doc, indent == 1 ? "•" : "–");
        p.setIndentationLeft(360 * indent + 120);
        inline(p, trimmed.substring(2).strip(), 10, false, INK);
        continue;
      }
      Matcher numbered = NUMBERED.matcher(trimmed);
      if (numbered.matches()) {
        XWPFParagraph p = bulletParagraph(doc, numbered.group(1) + ".");
        inline(p, numbered.group(2), 10, false, INK);
        continue;
      }
      if (trimmed.startsWith(">")) {
        XWPFParagraph p = doc.createParagraph();
        p.setIndentationLeft(360);
        p.setSpacingAfter(100);
        inline(p, trimmed.substring(1).strip(), 10, false, MUTED);
        continue;
      }
      XWPFParagraph p = doc.createParagraph();
      p.setSpacingAfter(110);
      p.setSpacingBetween(1.1);
      inline(p, trimmed, 10, false, INK);
    }
    if (!table.isEmpty()) {
      table(doc, table);
    }
  }

  private static void heading(XWPFDocument doc, String text, int level) {
    XWPFParagraph p = doc.createParagraph();
    if (!p.getCTP().isSetPPr()) {
      p.getCTP().addNewPPr();
    }
    p.setKeepNext(true);
    switch (level) {
      case 1, 2 -> {
        p.setSpacingBefore(320);
        p.setSpacingAfter(120);
        p.setBorderBottom(org.apache.poi.xwpf.usermodel.Borders.SINGLE);
        run(p, text.toUpperCase(java.util.Locale.forLanguageTag("es-MX")), 14, true, INK);
        p.getCTP().getPPr().getPBdr().getBottom().setColor(GOLD);
      }
      case 3 -> {
        p.setSpacingBefore(200);
        p.setSpacingAfter(80);
        run(p, text, 11.5, true, INK);
      }
      default -> {
        p.setSpacingBefore(140);
        p.setSpacingAfter(60);
        run(p, text, 10.5, true, MUTED);
      }
    }
  }

  private void table(XWPFDocument doc, List<String> rows) {
    List<List<String>> cells = new ArrayList<>();
    for (String row : rows) {
      String inner = row.strip();
      if (inner.startsWith("|")) {
        inner = inner.substring(1);
      }
      if (inner.endsWith("|")) {
        inner = inner.substring(0, inner.length() - 1);
      }
      List<String> parts = new ArrayList<>();
      for (String c : inner.split("(?<!\\\\)\\|", -1)) {
        parts.add(c.strip().replace("\\|", "|"));
      }
      if (parts.stream().allMatch(c -> c.matches(":?-{2,}:?") || c.isEmpty())) {
        continue; // fila separadora |---|---|
      }
      cells.add(parts);
    }
    if (cells.isEmpty()) {
      return;
    }
    int columns = cells.stream().mapToInt(List::size).max().orElse(1);
    double fontSize = columns >= 8 ? 7 : columns >= 6 ? 8 : 9;
    XWPFTable t = doc.createTable(cells.size(), columns);
    CTTblWidth width = t.getCTTbl().getTblPr().isSetTblW() ? t.getCTTbl().getTblPr().getTblW() : t.getCTTbl().getTblPr().addNewTblW();
    width.setType(STTblWidth.PCT);
    width.setW(BigInteger.valueOf(5000));
    t.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "C9C9C9");
    t.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "C9C9C9");
    t.setTopBorder(XWPFTable.XWPFBorderType.SINGLE, 8, 0, GOLD);
    t.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE, 8, 0, GOLD);
    t.setLeftBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
    t.setRightBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
    for (int r = 0; r < cells.size(); r++) {
      XWPFTableRow row = t.getRow(r);
      if (r == 0) {
        row.setRepeatHeader(true);
      }
      for (int c = 0; c < columns; c++) {
        XWPFTableCell cell = row.getCell(c);
        String text = c < cells.get(r).size() ? cells.get(r).get(c) : "";
        XWPFParagraph p = cell.getParagraphs().get(0);
        p.setSpacingAfter(0);
        if (r == 0) {
          cell.setColor(INK);
          run(p, stripMarks(text), fontSize, true, "FFFFFF");
        } else {
          if (r % 2 == 0) {
            cell.setColor("F4F5F5");
          }
          String[] pieces = text.split("<br\\s*/?>");
          for (int k = 0; k < pieces.length; k++) {
            XWPFParagraph target = k == 0 ? p : cell.addParagraph();
            target.setSpacingAfter(0);
            inline(target, pieces[k].strip(), fontSize, false, INK);
          }
        }
      }
    }
    spacer(doc, 120);
  }

  // ------------------------------------------------------------------ texto en línea

  /** **negritas**, *cursivas*, `código`, [texto](url) y URLs sueltas (como ligas reales). */
  private void inline(XWPFParagraph p, String text, double size, boolean bold, String color) {
    Matcher m = INLINE.matcher(text);
    int last = 0;
    while (m.find()) {
      if (m.start() > last) {
        styled(p, text.substring(last, m.start()), size, bold, false, color);
      }
      if (m.group(1) != null) {
        styled(p, m.group(1), size, true, false, color);
      } else if (m.group(2) != null) {
        link(p, m.group(2), m.group(3), size);
      } else if (m.group(4) != null) {
        link(p, shortUrl(m.group(4)), m.group(4), size);
      } else if (m.group(5) != null) {
        styled(p, m.group(5), size, bold, true, color);
      } else if (m.group(6) != null) {
        styled(p, m.group(6), size, bold, false, color);
      }
      last = m.end();
    }
    if (last < text.length()) {
      styled(p, text.substring(last), size, bold, false, color);
    }
  }

  private static void styled(XWPFParagraph p, String text, double size, boolean bold, boolean italic, String color) {
    XWPFRun r = run(p, text, size, bold, color);
    r.setItalic(italic);
  }

  private static void link(XWPFParagraph p, String text, String url, double size) {
    XWPFHyperlinkRun r = p.createHyperlinkRun(url);
    r.setText(text);
    r.setFontFamily(FONT);
    r.setFontSize(size);
    r.setColor("1F5FA8");
    r.setUnderline(org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE);
  }

  private static String shortUrl(String url) {
    String s = url.replaceFirst("^https?://(www\\.)?", "");
    return s.length() > 60 ? s.substring(0, 57) + "…" : s;
  }

  private static XWPFRun run(XWPFParagraph p, String text, double size, boolean bold, String color) {
    XWPFRun r = p.createRun();
    r.setText(text);
    r.setFontFamily(FONT);
    r.setFontSize(size);
    r.setBold(bold);
    r.setColor(color);
    return r;
  }

  private static XWPFParagraph bulletParagraph(XWPFDocument doc, String marker) {
    XWPFParagraph p = doc.createParagraph();
    p.setIndentationLeft(480);
    p.setIndentationHanging(300);
    p.setSpacingAfter(60);
    run(p, marker + "\t", 10, true, GOLD);
    return p;
  }

  private static void paragraph(XWPFDocument doc, String text, boolean bold) {
    paragraph(doc, text, bold, 10, INK);
  }

  private static void paragraph(XWPFDocument doc, String text, boolean bold, double size, String color) {
    XWPFParagraph p = doc.createParagraph();
    p.setSpacingAfter(100);
    run(p, text, size, bold, color);
  }

  private static void keyValue(XWPFDocument doc, String key, String value) {
    XWPFParagraph p = doc.createParagraph();
    p.setSpacingAfter(60);
    run(p, key + ": ", 11, true, MUTED);
    run(p, value == null ? "" : value, 11, false, INK);
  }

  private static void goldRule(XWPFDocument doc) {
    XWPFParagraph p = doc.createParagraph();
    p.setBorderBottom(org.apache.poi.xwpf.usermodel.Borders.THICK);
    p.getCTP().getPPr().getPBdr().getBottom().setColor(GOLD);
    p.setSpacingAfter(200);
  }

  private static void spacer(XWPFDocument doc, int twips) {
    XWPFParagraph p = doc.createParagraph();
    p.setSpacingAfter(twips);
  }

  private static String stripMarks(String text) {
    return text.replace("**", "").replace("`", "").replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1");
  }

  private static String nonBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  /** Para pruebas: el Word se puede volver a abrir. */
  static XWPFDocument open(byte[] docx) throws IOException {
    return new XWPFDocument(new ByteArrayInputStream(docx));
  }
}
