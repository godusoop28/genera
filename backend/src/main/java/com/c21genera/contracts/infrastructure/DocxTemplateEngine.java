package com.c21genera.contracts.infrastructure;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;

/**
 * Reemplazo de placeholders {@code {{FIELD_CODE}}} en un DOCX, robusto ante
 * el hecho de que Word frecuentemente divide un mismo placeholder en varios
 * "runs" XML (ver AGENTS §73): no hace un reemplazo ingenuo de texto plano
 * que rompería el documento, sino que reconstruye el texto completo del
 * párrafo/celda antes de sustituir y luego reescribe los runs.
 *
 * <p>IMPORTANTE: el documento PROFECO original (ver /reference/contrato) usa
 * líneas en blanco para llenado manual, no placeholders {{...}}. Esta
 * utilidad se usa hoy sobre una plantilla de PROTOTIPO propia (ver
 * ContractService), nunca sobre el DOCX original registrado. Está lista
 * para operar sobre una plantilla oficial con placeholders reales el día
 * que CENTURY 21 Genera la proporcione, sin cambios.
 */
@Component
public class DocxTemplateEngine {

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

  public byte[] render(byte[] templateDocx, Map<String, String> values) throws IOException {
    try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(templateDocx))) {
      for (IBodyElement element : document.getBodyElements()) {
        if (element instanceof XWPFParagraph paragraph) {
          replaceInParagraph(paragraph, values);
        } else if (element instanceof XWPFTable table) {
          replaceInTable(table, values);
        }
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.write(out);
      return out.toByteArray();
    }
  }

  private void replaceInTable(XWPFTable table, Map<String, String> values) {
    for (XWPFTableRow row : table.getRows()) {
      for (XWPFTableCell cell : row.getTableCells()) {
        for (XWPFParagraph paragraph : cell.getParagraphs()) {
          replaceInParagraph(paragraph, values);
        }
      }
    }
  }

  private void replaceInParagraph(XWPFParagraph paragraph, Map<String, String> values) {
    List<XWPFRun> runs = paragraph.getRuns();
    if (runs.isEmpty()) {
      return;
    }

    String fullText = runs.stream().map(r -> r.text() == null ? "" : r.text()).reduce("", String::concat);
    if (!fullText.contains("{{")) {
      return;
    }

    String replaced = substitute(fullText, values);

    // Reescribe todo el texto en el primer run (conserva su formato) y
    // vacía los demás, en vez de intentar re-mapear cada placeholder a su
    // run original (que puede estar partido de forma arbitraria por Word).
    runs.get(0).setText(replaced, 0);
    for (int i = 1; i < runs.size(); i++) {
      runs.get(i).setText("", 0);
    }
  }

  private String substitute(String text, Map<String, String> values) {
    Matcher matcher = PLACEHOLDER.matcher(text);
    StringBuilder result = new StringBuilder();
    int last = 0;
    while (matcher.find()) {
      result.append(text, last, matcher.start());
      String code = matcher.group(1);
      result.append(values.getOrDefault(code, ""));
      last = matcher.end();
    }
    result.append(text.substring(last));
    return result.toString();
  }
}
