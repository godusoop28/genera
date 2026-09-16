package com.c21genera.contracts.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

class DocxTemplateEngineTest {

  @Test
  void replacesAPlaceholderSplitAcrossMultipleRuns() throws Exception {
    byte[] template = buildDocxWithSplitPlaceholder();

    byte[] rendered =
        new DocxTemplateEngine().render(template, Map.of("CLIENT_FULL_NAME", "Juan Pérez López"));

    try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(rendered))) {
      String text = doc.getParagraphs().get(0).getText();
      assertThat(text).isEqualTo("Cliente: Juan Pérez López.");
    }
  }

  /** Simula lo que Word hace a menudo: parte "{{CLIENT_FULL_NAME}}" en 3 runs distintos. */
  private static byte[] buildDocxWithSplitPlaceholder() throws Exception {
    try (XWPFDocument doc = new XWPFDocument()) {
      XWPFParagraph paragraph = doc.createParagraph();
      XWPFRun run1 = paragraph.createRun();
      run1.setText("Cliente: {{CLIENT_");
      XWPFRun run2 = paragraph.createRun();
      run2.setText("FULL_NAME");
      XWPFRun run3 = paragraph.createRun();
      run3.setText("}}.");

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      doc.write(out);
      return out.toByteArray();
    }
  }
}
