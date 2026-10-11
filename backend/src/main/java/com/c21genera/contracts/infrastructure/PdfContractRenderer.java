package com.c21genera.contracts.infrastructure;

import com.c21genera.contracts.domain.ContractReferenceData;
import com.c21genera.contracts.domain.document.ContractDocument;
import com.c21genera.contracts.domain.document.ContractDocument.Align;
import com.c21genera.contracts.domain.document.ContractDocument.Block;
import com.c21genera.contracts.domain.document.ContractDocument.Cell;
import com.c21genera.contracts.domain.document.ContractDocument.Image;
import com.c21genera.contracts.domain.document.ContractDocument.PageBreak;
import com.c21genera.contracts.domain.document.ContractDocument.Paragraph;
import com.c21genera.contracts.domain.document.ContractDocument.Row;
import com.c21genera.contracts.domain.document.ContractDocument.SectionHeading;
import com.c21genera.contracts.domain.document.ContractDocument.SignatureLines;
import com.c21genera.contracts.domain.document.ContractDocument.Span;
import com.c21genera.contracts.domain.document.ContractDocument.Table;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 * Genera el PDF del contrato con PDFBox (sin depender de LibreOffice): es el
 * archivo que leen y firman las partes, y cuya huella SHA-256 queda en la
 * evidencia de cada firma. Mismo contenido que el DOCX; el formato imita al
 * modelo registrado (logo arriba a la derecha, Arial/Helvetica 9 pt con
 * interlineado de 1.5, márgenes de 3 cm, barras de sección sombreadas,
 * tablas con bordes en letra de 8 pt, recuadros de firma y pie con el número
 * de expediente PROFECO y el número de página).
 *
 * <p>Un borrador incompleto lleva una marca de agua en cada página.
 */
@Component
public class PdfContractRenderer {

  private static final PDRectangle PAGE = PDRectangle.LETTER;
  /** 1701 twips (3 cm), como el modelo registrado. */
  private static final float MARGIN_X = 85;
  private static final float MARGIN_TOP = 118;
  private static final float MARGIN_BOTTOM = 66;
  private static final float FONT_SIZE = 9f;
  /** Interlineado 1.5 del modelo (Arial 9 pt). */
  private static final float LINE_SPACING = 1.65f;
  private static final float PARAGRAPH_GAP = 7f;
  /** Las tablas del modelo van en 8 pt e interlineado sencillo. */
  private static final float TABLE_FONT_SIZE = 8f;
  private static final float TABLE_LINE_SPACING = 1.2f;
  private static final float CELL_PADDING = 3f;
  private static final float INDENT = 20f;
  private static final float SIGNATURE_BOX_HEIGHT = 44f;
  private static final Color SHADE = new Color(0xDE, 0xEA, 0xF6);
  private static final Color PENDING_BG = new Color(0xFF, 0xF2, 0x00);
  private static final Color PENDING_FG = new Color(0xC0, 0x00, 0x00);

  public byte[] render(ContractDocument document) {
    try (PDDocument pdf = new PDDocument()) {
      Layout layout = new Layout(pdf, document);
      if (document.draft()) {
        layout.write(Paragraph.centered(new Span("BORRADOR INCOMPLETO — NO VÁLIDO PARA FIRMA", true, true)));
        if (!document.missingItems().isEmpty()) {
          layout.write(
              Paragraph.of(Span.b("Datos pendientes antes de poder enviarlo a firma: "), Span.t(String.join("; ", document.missingItems()) + "."))
                  .withFontSize(TABLE_FONT_SIZE));
        }
      }
      List<Block> blocks = document.blocks();
      for (int i = 0; i < blocks.size(); i++) {
        // El párrafo que presenta una tabla ("Cuarta. ... son los siguientes:") no se queda solo al pie de la página.
        if (blocks.get(i) instanceof Paragraph p && i + 1 < blocks.size() && blocks.get(i + 1) instanceof Table next) {
          layout.keepWithTable(p, next);
        }
        layout.write(blocks.get(i));
      }
      layout.finish();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      pdf.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo generar el PDF del contrato", e);
    }
  }

  private static final class Layout {

    private final PDDocument pdf;
    private final ContractDocument document;
    private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final Map<Character, Boolean> encodable = new HashMap<>();
    private final PDImageXObject logo;
    private final float contentWidth = PAGE.getWidth() - 2 * MARGIN_X;
    private PDPageContentStream stream;
    private float y;

    Layout(PDDocument pdf, ContractDocument document) throws IOException {
      this.pdf = pdf;
      this.document = document;
      this.logo = loadLogo(pdf);
      newPage();
    }

    private static PDImageXObject loadLogo(PDDocument pdf) {
      try (InputStream in = new ClassPathResource("contract/logo.png").getInputStream()) {
        return PDImageXObject.createFromByteArray(pdf, in.readAllBytes(), "logo");
      } catch (IOException e) {
        return null;
      }
    }

    void write(Block block) throws IOException {
      switch (block) {
        case Paragraph p -> paragraph(p.spans(), p.align(), MARGIN_X + p.indent() * INDENT, contentWidth - p.indent() * INDENT, sizeOf(p));
        case SectionHeading h -> heading(h.text());
        case Table t -> table(t);
        case PageBreak ignored -> newPage();
        case SignatureLines s -> signatures(s.labels());
        case Image image -> image(image);
      }
    }

    // --- Párrafos -------------------------------------------------------

    private record Word(String text, PDType1Font font, float size, boolean pending, float width, boolean lineBreakAfter) {}

    private static float sizeOf(Paragraph p) {
      return p.fontSize() > 0 ? p.fontSize() : FONT_SIZE;
    }

    private static float leadingOf(float size) {
      return size * (size >= FONT_SIZE ? LINE_SPACING : TABLE_LINE_SPACING);
    }

    private record Line(List<Word> words, boolean last) {}

    private List<Line> lines(List<Span> spans, float width, float size) throws IOException {
      List<Word> words = new ArrayList<>();
      for (Span span : spans) {
        PDType1Font font = span.bold() ? bold : regular;
        String[] hardLines = sanitize(span.text()).split("\n", -1);
        for (int h = 0; h < hardLines.length; h++) {
          String[] parts = hardLines[h].split(" ", -1);
          for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            boolean lastPart = i == parts.length - 1;
            if (!part.isEmpty()) {
              words.add(new Word(part, font, size, span.pending(), textWidth(font, part, size), false));
            }
            if (!lastPart) {
              words.add(new Word(" ", font, size, span.pending(), textWidth(font, " ", size), false));
            }
          }
          if (h < hardLines.length - 1) {
            words.add(new Word("", font, size, false, 0, true));
          }
        }
      }

      List<Line> lines = new ArrayList<>();
      List<Word> current = new ArrayList<>();
      float currentWidth = 0;
      for (Word word : words) {
        if (word.lineBreakAfter()) {
          lines.add(new Line(trim(current), true));
          current = new ArrayList<>();
          currentWidth = 0;
          continue;
        }
        if (word.text().equals(" ") && current.isEmpty()) {
          continue;
        }
        if (currentWidth + word.width() > width && !current.isEmpty() && !word.text().equals(" ")) {
          // Las palabras pegadas sin espacio (p. ej. un nombre en negritas seguido de ",") pasan
          // juntas al siguiente renglón: nunca se empieza un renglón con la coma suelta.
          int lastSpace = -1;
          for (int i = current.size() - 1; i >= 0; i--) {
            if (current.get(i).text().equals(" ")) {
              lastSpace = i;
              break;
            }
          }
          List<Word> carried = new ArrayList<>();
          if (lastSpace >= 0 && lastSpace < current.size() - 1) {
            carried.addAll(current.subList(lastSpace + 1, current.size()));
            current = new ArrayList<>(current.subList(0, lastSpace));
          }
          lines.add(new Line(trim(current), false));
          current = carried;
          currentWidth = 0;
          for (Word w : carried) {
            currentWidth += w.width();
          }
        }
        if (word.width() > width) {
          // Palabra más larga que la línea (p. ej. una URL): se corta por caracteres.
          for (Word piece : splitLongWord(word, width)) {
            if (!current.isEmpty()) {
              lines.add(new Line(trim(current), false));
              current = new ArrayList<>();
            }
            current.add(piece);
            currentWidth = piece.width();
          }
          continue;
        }
        current.add(word);
        currentWidth += word.width();
      }
      if (!current.isEmpty()) {
        lines.add(new Line(trim(current), true));
      }
      return lines;
    }

    private List<Word> splitLongWord(Word word, float width) throws IOException {
      List<Word> pieces = new ArrayList<>();
      StringBuilder piece = new StringBuilder();
      for (char c : word.text().toCharArray()) {
        if (textWidth(word.font(), piece.toString() + c, word.size()) > width && piece.length() > 0) {
          pieces.add(new Word(piece.toString(), word.font(), word.size(), word.pending(), textWidth(word.font(), piece.toString(), word.size()), false));
          piece.setLength(0);
        }
        piece.append(c);
      }
      if (piece.length() > 0) {
        pieces.add(new Word(piece.toString(), word.font(), word.size(), word.pending(), textWidth(word.font(), piece.toString(), word.size()), false));
      }
      return pieces;
    }

    private static List<Word> trim(List<Word> words) {
      List<Word> trimmed = new ArrayList<>(words);
      while (!trimmed.isEmpty() && trimmed.getLast().text().equals(" ")) {
        trimmed.removeLast();
      }
      return trimmed;
    }

    private float paragraphHeight(List<Span> spans, float width, float size) throws IOException {
      return lines(spans, width, size).size() * leadingOf(size);
    }

    private void paragraph(List<Span> spans, Align align, float x, float width, float size) throws IOException {
      float leading = leadingOf(size);
      for (Line line : lines(spans, width, size)) {
        ensureSpace(leading);
        drawLine(line, align, x, width, y - size - (leading - size) / 2);
        y -= leading;
      }
      y -= PARAGRAPH_GAP;
    }

    /**
     * Si el párrafo y el inicio de la tabla que le sigue no caben juntos en lo que
     * resta de la página, ambos pasan a la siguiente (evita el párrafo huérfano y
     * la página casi vacía que dejaba la tabla de la cláusula cuarta).
     */
    void keepWithTable(Paragraph p, Table table) throws IOException {
      float size = sizeOf(p);
      float needed = paragraphHeight(p.spans(), contentWidth - p.indent() * INDENT, size) + PARAGRAPH_GAP + tableLeadHeight(table);
      if (y - needed < MARGIN_BOTTOM && needed < PAGE.getHeight() - MARGIN_TOP - MARGIN_BOTTOM) {
        newPage();
      }
    }

    private void drawLine(Line line, Align align, float x, float width, float baseline) throws IOException {
      float lineWidth = 0;
      int spaces = 0;
      for (Word w : line.words()) {
        lineWidth += w.width();
        if (w.text().equals(" ")) {
          spaces++;
        }
      }
      float extraPerSpace = 0;
      float cursor = x;
      if (align == Align.CENTER) {
        cursor = x + (width - lineWidth) / 2;
      } else if (align == Align.JUSTIFY && !line.last() && spaces > 0) {
        extraPerSpace = (width - lineWidth) / spaces;
      }
      for (Word w : line.words()) {
        float advance = w.width() + (w.text().equals(" ") ? extraPerSpace : 0);
        if (w.pending()) {
          stream.setNonStrokingColor(PENDING_BG);
          stream.addRect(cursor, baseline - 2, advance, w.size() + 2);
          stream.fill();
        }
        if (!w.text().isBlank()) {
          stream.setNonStrokingColor(w.pending() ? PENDING_FG : Color.BLACK);
          stream.beginText();
          stream.setFont(w.font(), w.size());
          stream.newLineAtOffset(cursor, baseline);
          stream.showText(w.text());
          stream.endText();
        }
        cursor += advance;
      }
      stream.setNonStrokingColor(Color.BLACK);
    }

    // --- Encabezados, tablas, firmas ------------------------------------

    private void heading(String text) throws IOException {
      float height = FONT_SIZE + 6;
      ensureSpace(height + 3 * leadingOf(FONT_SIZE)); // nunca dejar un encabezado huérfano al pie de página
      stream.setNonStrokingColor(SHADE);
      stream.addRect(MARGIN_X, y - height, contentWidth, height);
      stream.fill();
      stream.setStrokingColor(Color.BLACK);
      stream.setLineWidth(0.5f);
      stream.addRect(MARGIN_X, y - height, contentWidth, height);
      stream.stroke();
      drawLine(lines(List.of(Span.b(text)), contentWidth, FONT_SIZE).getFirst(), Align.CENTER, MARGIN_X, contentWidth, y - height + 4);
      y -= height + PARAGRAPH_GAP + 4;
    }

    private record TableGeometry(List<List<float[]>> cells, List<Float> heights) {}

    private TableGeometry geometry(Table table) throws IOException {
      int columns = table.columnWeights().size();
      int totalWeight = table.columnWeights().stream().mapToInt(Integer::intValue).sum();
      float[] widths = new float[columns];
      for (int i = 0; i < columns; i++) {
        widths[i] = contentWidth * table.columnWeights().get(i) / totalWeight;
      }
      List<List<float[]>> geometry = new ArrayList<>();
      List<Float> heights = new ArrayList<>();
      for (Row row : table.rows()) {
        float rowHeight = 0;
        List<float[]> cellGeometry = new ArrayList<>();
        int column = 0;
        for (Cell cell : row.cells()) {
          int span = Math.max(1, cell.colSpan());
          float x = MARGIN_X;
          for (int k = 0; k < column; k++) {
            x += widths[k];
          }
          float width = 0;
          for (int k = column; k < Math.min(columns, column + span); k++) {
            width += widths[k];
          }
          float textHeight = 0;
          for (List<Span> paragraph : cell.paragraphs()) {
            textHeight += paragraphHeight(paragraph, width - 2 * CELL_PADDING, TABLE_FONT_SIZE) + 2;
          }
          rowHeight = Math.max(rowHeight, textHeight + 2 * CELL_PADDING);
          cellGeometry.add(new float[] {x, width});
          column += span;
        }
        geometry.add(cellGeometry);
        heights.add(rowHeight);
      }
      return new TableGeometry(geometry, heights);
    }

    /** Alto de los renglones de títulos más el primer renglón de datos. */
    private float tableLeadHeight(Table table) throws IOException {
      List<Float> heights = geometry(table).heights();
      int leading = 0;
      while (leading < table.rows().size() - 1 && isHeaderRow(table.rows().get(leading))) {
        leading++;
      }
      float height = 0;
      for (int r = 0; r <= Math.min(leading, heights.size() - 1); r++) {
        height += heights.get(r);
      }
      return height;
    }

    private void table(Table table) throws IOException {
      TableGeometry tableGeometry = geometry(table);
      List<List<float[]>> geometry = tableGeometry.cells();
      List<Float> heights = tableGeometry.heights();

      // Los renglones de encabezado (sombreados o de títulos) nunca quedan
      // solos al pie de la página: se mantienen con el primer renglón de datos.
      int leading = 0;
      while (leading < table.rows().size() - 1 && isHeaderRow(table.rows().get(leading))) {
        leading++;
      }
      float keepTogether = 0;
      for (int r = 0; r <= Math.min(leading, table.rows().size() - 1); r++) {
        keepTogether += heights.get(r);
      }
      if (keepTogether < PAGE.getHeight() - MARGIN_TOP - MARGIN_BOTTOM) {
        ensureSpace(keepTogether);
      }

      for (int r = 0; r < table.rows().size(); r++) {
        Row row = table.rows().get(r);
        float rowHeight = heights.get(r);
        ensureSpace(rowHeight);
        for (int c = 0; c < row.cells().size(); c++) {
          Cell cell = row.cells().get(c);
          float x = geometry.get(r).get(c)[0];
          float width = geometry.get(r).get(c)[1];
          if (cell.shaded()) {
            stream.setNonStrokingColor(SHADE);
            stream.addRect(x, y - rowHeight, width, rowHeight);
            stream.fill();
          }
          stream.setStrokingColor(Color.BLACK);
          stream.setLineWidth(0.5f);
          stream.addRect(x, y - rowHeight, width, rowHeight);
          stream.stroke();
          float cursor = y - CELL_PADDING;
          float lineLeading = leadingOf(TABLE_FONT_SIZE);
          for (List<Span> paragraph : cell.paragraphs()) {
            for (Line line : lines(paragraph, width - 2 * CELL_PADDING, TABLE_FONT_SIZE)) {
              drawLine(line, cell.align(), x + CELL_PADDING, width - 2 * CELL_PADDING, cursor - TABLE_FONT_SIZE);
              cursor -= lineLeading;
            }
            cursor -= 2;
          }
        }
        y -= rowHeight;
      }
      y -= PARAGRAPH_GAP + 2;
    }

    /** Un renglón de títulos: todas sus celdas sombreadas o centradas con texto corto. */
    private static boolean isHeaderRow(Row row) {
      return row.cells().stream()
          .allMatch(
              c ->
                  c.shaded()
                      || (c.align() == Align.CENTER
                          && c.paragraphs().stream().flatMap(List::stream).mapToInt(s -> s.text().length()).sum() < 120));
    }

    /**
     * Recuadros de firma del modelo registrado: un rectángulo por firmante con
     * "Firma de ..." en negritas debajo y, en su caso, nombre y carácter. Dos por
     * renglón; si queda uno solo en el renglón, va centrado.
     */
    private void signatures(List<String> labels) throws IOException {
      float columnWidth = contentWidth / 2;
      float boxWidth = columnWidth - 30;
      float leading = leadingOf(TABLE_FONT_SIZE) + 1;
      for (int i = 0; i < labels.size(); i += 2) {
        int inRow = Math.min(2, labels.size() - i);
        int maxLines = 1;
        for (int k = i; k < i + inRow; k++) {
          maxLines = Math.max(maxLines, labels.get(k).split("\n").length);
        }
        float blockHeight = 14 + SIGNATURE_BOX_HEIGHT + 4 + maxLines * leading + 14;
        ensureSpace(blockHeight);
        float top = y - 14;
        for (int k = i; k < i + inRow; k++) {
          float x = inRow == 1 ? MARGIN_X + (contentWidth - boxWidth) / 2 : k == i ? MARGIN_X : MARGIN_X + contentWidth - boxWidth;
          stream.setStrokingColor(Color.BLACK);
          stream.setLineWidth(0.6f);
          stream.addRect(x, top - SIGNATURE_BOX_HEIGHT, boxWidth, SIGNATURE_BOX_HEIGHT);
          stream.stroke();
          float cursor = top - SIGNATURE_BOX_HEIGHT - 4;
          String[] parts = labels.get(k).split("\n");
          for (int p = 0; p < parts.length; p++) {
            float size = p == 0 ? FONT_SIZE : TABLE_FONT_SIZE;
            List<Span> spans = List.of(p == 0 ? Span.b(parts[p]) : Span.t(parts[p]));
            for (Line line : lines(spans, boxWidth, size)) {
              drawLine(line, Align.CENTER, x, boxWidth, cursor - size);
              cursor -= leading;
            }
          }
        }
        y -= blockHeight;
      }
    }

    private void image(Image image) throws IOException {
      PDImageXObject xobject = PDImageXObject.createFromByteArray(pdf, image.png(), "firma");
      float width = Math.min(image.widthPoints(), contentWidth);
      float height = width * xobject.getHeight() / xobject.getWidth();
      ensureSpace(height + leadingOf(FONT_SIZE) + 4);
      stream.drawImage(xobject, MARGIN_X, y - height, width, height);
      y -= height + 2;
      paragraph(List.of(Span.t(image.caption())), Align.LEFT, MARGIN_X, contentWidth, FONT_SIZE);
    }

    // --- Páginas --------------------------------------------------------

    private void ensureSpace(float needed) throws IOException {
      if (y - needed < MARGIN_BOTTOM) {
        newPage();
      }
    }

    private void newPage() throws IOException {
      if (stream != null) {
        stream.close();
      }
      PDPage page = new PDPage(PAGE);
      pdf.addPage(page);
      stream = new PDPageContentStream(pdf, page);
      if (logo != null) {
        // Como en el encabezado del modelo: logo alineado a la derecha.
        float width = 72;
        float height = width * logo.getHeight() / logo.getWidth();
        stream.drawImage(logo, PAGE.getWidth() - MARGIN_X - width, PAGE.getHeight() - 36 - height, width, height);
      }
      y = PAGE.getHeight() - MARGIN_TOP;
    }

    void finish() throws IOException {
      stream.close();
      int total = pdf.getNumberOfPages();
      for (int i = 0; i < total; i++) {
        PDPage page = pdf.getPage(i);
        try (PDPageContentStream footer = new PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
          // Pie del modelo: leyenda PROFECO a la izquierda y el número de página a la derecha.
          footer.setNonStrokingColor(Color.BLACK);
          footer.beginText();
          footer.setFont(regular, 8);
          footer.newLineAtOffset(MARGIN_X, 36);
          footer.showText(sanitize("Contrato registrado ante PROFECO Número de expediente: " + ContractReferenceData.PROFECO_FILE_NUMBER));
          footer.endText();
          String pageLabel = String.valueOf(i + 1);
          footer.beginText();
          footer.setFont(regular, 11);
          footer.newLineAtOffset(PAGE.getWidth() - MARGIN_X - textWidth(regular, pageLabel, 11), 34);
          footer.showText(pageLabel);
          footer.endText();
          if (document.draft()) {
            watermark(footer);
          }
        }
      }
    }

    private void watermark(PDPageContentStream content) throws IOException {
      PDExtendedGraphicsState alpha = new PDExtendedGraphicsState();
      alpha.setNonStrokingAlphaConstant(0.18f);
      content.setGraphicsStateParameters(alpha);
      content.setNonStrokingColor(PENDING_FG);
      content.beginText();
      content.setFont(bold, 34);
      content.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(52), 110, 170));
      content.showText(sanitize("BORRADOR INCOMPLETO"));
      content.endText();
      content.beginText();
      content.setFont(bold, 22);
      content.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(52), 150, 130));
      content.showText(sanitize("NO VÁLIDO PARA FIRMA"));
      content.endText();
    }

    private float textWidth(PDType1Font font, String text, float size) throws IOException {
      return font.getStringWidth(text) / 1000 * size;
    }

    /** Helvetica estándar usa WinAnsi: cualquier carácter fuera de ese juego se reemplaza. */
    private String sanitize(String text) {
      StringBuilder out = new StringBuilder(text.length());
      for (char c : text.toCharArray()) {
        if (c == '\n') {
          out.append(c);
          continue;
        }
        char normalized = switch (c) {
          case '\t', ' ' -> ' ';
          case '‐', '‑', '‒' -> '-';
          default -> c;
        };
        boolean ok =
            encodable.computeIfAbsent(
                normalized,
                ch -> {
                  try {
                    regular.encode(String.valueOf(ch));
                    return true;
                  } catch (IllegalArgumentException | IOException e) {
                    return false;
                  }
                });
        out.append(ok ? normalized : '?');
      }
      return out.toString();
    }
  }
}
