package com.c21genera.contracts.infrastructure;

import com.c21genera.contracts.domain.document.ContractDocument;
import com.c21genera.contracts.domain.document.ContractDocument.Align;
import com.c21genera.contracts.domain.document.ContractDocument.Block;
import com.c21genera.contracts.domain.document.ContractDocument.Cell;
import com.c21genera.contracts.domain.document.ContractDocument.PageBreak;
import com.c21genera.contracts.domain.document.ContractDocument.Paragraph;
import com.c21genera.contracts.domain.document.ContractDocument.Row;
import com.c21genera.contracts.domain.document.ContractDocument.SectionHeading;
import com.c21genera.contracts.domain.document.ContractDocument.SignatureLines;
import com.c21genera.contracts.domain.document.ContractDocument.Span;
import com.c21genera.contracts.domain.document.ContractDocument.Table;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.List;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblWidth;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STShd;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Genera el DOCX sobre el propio modelo registrado (resources
 * contract/base-template.docx, copia de /reference/contrato): se vacía su
 * cuerpo y se escribe el contenido, así se conservan el logo, el encabezado,
 * el pie con el número de expediente PROFECO, márgenes y estilos (Arial 9).
 */
@Component
public class DocxContractRenderer {

  private static final String TEMPLATE = "contract/base-template.docx";
  private static final int FONT_HALF_POINTS = 18;
  private static final int TABLE_WIDTH_TWIPS = 8828;
  private static final String SHADE = "DEEAF6";

  public byte[] render(ContractDocument document) {
    try (InputStream template = new ClassPathResource(TEMPLATE).getInputStream();
        XWPFDocument docx = new XWPFDocument(template)) {
      for (int i = docx.getBodyElements().size() - 1; i >= 0; i--) {
        docx.removeBodyElement(i);
      }
      if (document.draft()) {
        writeDraftBanner(docx, document.missingItems());
      }
      for (Block block : document.blocks()) {
        write(docx, block);
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      docx.write(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo generar el DOCX del contrato", e);
    }
  }

  /** Solo para pruebas: permite verificar el texto generado. */
  static String plainText(byte[] docx) throws IOException {
    try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
      StringBuilder text = new StringBuilder();
      document.getParagraphs().forEach(p -> text.append(p.getText()).append('\n'));
      document.getTables().forEach(t -> text.append(t.getText()).append('\n'));
      return text.toString();
    }
  }

  private void writeDraftBanner(XWPFDocument docx, List<String> missing) {
    XWPFParagraph p = docx.createParagraph();
    p.setAlignment(ParagraphAlignment.CENTER);
    XWPFRun run = p.createRun();
    run.setBold(true);
    run.setColor("C00000");
    run.setFontSize(12);
    run.setText("BORRADOR INCOMPLETO — NO VÁLIDO PARA FIRMA");
    XWPFParagraph list = docx.createParagraph();
    XWPFRun intro = list.createRun();
    intro.setFontSize(9);
    intro.setColor("C00000");
    intro.setText("Datos pendientes antes de poder enviarlo a firma: " + String.join("; ", missing) + ".");
  }

  private void write(XWPFDocument docx, Block block) {
    switch (block) {
      case Paragraph p -> writeParagraph(docx.createParagraph(), p.spans(), p.align(), p.indent());
      case SectionHeading h -> writeHeading(docx, h.text());
      case Table t -> writeTable(docx, t);
      case PageBreak ignored -> docx.createParagraph().createRun().addBreak(BreakType.PAGE);
      case SignatureLines s -> writeSignatures(docx, s.labels());
      case ContractDocument.Image image -> writeParagraph(docx.createParagraph(), List.of(Span.t(image.caption())), Align.CENTER, 0);
    }
  }

  private void writeParagraph(XWPFParagraph paragraph, List<Span> spans, Align align, int indent) {
    paragraph.setAlignment(
        switch (align) {
          case LEFT -> ParagraphAlignment.LEFT;
          case CENTER -> ParagraphAlignment.CENTER;
          case JUSTIFY -> ParagraphAlignment.BOTH;
        });
    if (indent > 0) {
      paragraph.setIndentationLeft(360 * indent);
    }
    for (Span span : spans) {
      String[] lines = span.text().split("\n", -1);
      for (int i = 0; i < lines.length; i++) {
        XWPFRun run = paragraph.createRun();
        run.setFontSize(FONT_HALF_POINTS / 2);
        run.setBold(span.bold());
        if (span.pending()) {
          run.setTextHighlightColor("yellow");
          run.setColor("C00000");
        }
        run.setText(lines[i]);
        if (i < lines.length - 1) {
          run.addBreak();
        }
      }
    }
  }

  private void writeHeading(XWPFDocument docx, String text) {
    XWPFTable table = docx.createTable(1, 1);
    setTableWidth(table);
    XWPFTableCell cell = table.getRow(0).getCell(0);
    shade(cell);
    XWPFParagraph p = cell.getParagraphs().getFirst();
    writeParagraph(p, List.of(Span.b(text)), Align.CENTER, 0);
    docx.createParagraph();
  }

  private void writeTable(XWPFDocument docx, Table table) {
    int columns = table.columnWeights().size();
    int totalWeight = table.columnWeights().stream().mapToInt(Integer::intValue).sum();
    XWPFTable xt = docx.createTable(table.rows().size(), columns);
    setTableWidth(xt);
    for (int r = 0; r < table.rows().size(); r++) {
      Row row = table.rows().get(r);
      XWPFTableRow xr = xt.getRow(r);
      int column = 0;
      for (Cell cell : row.cells()) {
        XWPFTableCell xc = xr.getCell(column);
        int span = Math.max(1, cell.colSpan());
        int weight = 0;
        for (int k = column; k < Math.min(columns, column + span); k++) {
          weight += table.columnWeights().get(k);
        }
        CTTcPr pr = xc.getCTTc().isSetTcPr() ? xc.getCTTc().getTcPr() : xc.getCTTc().addNewTcPr();
        CTTblWidth width = pr.isSetTcW() ? pr.getTcW() : pr.addNewTcW();
        width.setType(STTblWidth.DXA);
        width.setW(BigInteger.valueOf((long) TABLE_WIDTH_TWIPS * weight / totalWeight));
        if (span > 1) {
          pr.addNewGridSpan().setVal(BigInteger.valueOf(span));
        }
        if (cell.shaded()) {
          shade(xc);
        }
        boolean first = true;
        for (List<Span> paragraph : cell.paragraphs()) {
          XWPFParagraph xp = first ? xc.getParagraphs().getFirst() : xc.addParagraph();
          writeParagraph(xp, paragraph, cell.align(), 0);
          first = false;
        }
        column += span;
      }
      // Las celdas sobrantes (por gridSpan) se eliminan para que Word no las muestre.
      while (xr.getTableCells().size() > column) {
        xr.removeCell(xr.getTableCells().size() - 1);
      }
    }
    docx.createParagraph();
  }

  private void writeSignatures(XWPFDocument docx, List<String> labels) {
    int rows = (labels.size() + 1) / 2;
    XWPFTable table = docx.createTable(rows, 2);
    setTableWidth(table);
    table.removeBorders();
    for (int i = 0; i < labels.size(); i++) {
      XWPFTableCell cell = table.getRow(i / 2).getCell(i % 2);
      XWPFParagraph space = cell.getParagraphs().getFirst();
      space.setAlignment(ParagraphAlignment.CENTER);
      space.createRun().addBreak();
      XWPFParagraph line = cell.addParagraph();
      writeParagraph(line, List.of(Span.t("______________________________")), Align.CENTER, 0);
      XWPFParagraph label = cell.addParagraph();
      writeParagraph(label, List.of(Span.b(labels.get(i))), Align.CENTER, 0);
    }
    docx.createParagraph();
  }

  private static void setTableWidth(XWPFTable table) {
    CTTblWidth width = table.getCTTbl().getTblPr().isSetTblW() ? table.getCTTbl().getTblPr().getTblW() : table.getCTTbl().getTblPr().addNewTblW();
    width.setType(STTblWidth.DXA);
    width.setW(BigInteger.valueOf(TABLE_WIDTH_TWIPS));
  }

  private static void shade(XWPFTableCell cell) {
    CTTcPr pr = cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
    CTShd shd = pr.isSetShd() ? pr.getShd() : pr.addNewShd();
    shd.setVal(STShd.CLEAR);
    shd.setColor("auto");
    shd.setFill(SHADE);
  }
}
