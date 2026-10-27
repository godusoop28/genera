package com.c21genera.receipts;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.receipts.domain.DocumentReceipt.Header;
import com.c21genera.receipts.domain.ReceivedItem;
import com.c21genera.receipts.infrastructure.ReceiptPdfRenderer;
import com.c21genera.receipts.infrastructure.ReceiptPdfRenderer.Signature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** Control de Cambios #31: el acuse se genera con acentos, varias páginas y la firma solo cuando existe. */
class ReceiptPdfRendererTest {

  private final ReceiptPdfRenderer renderer = new ReceiptPdfRenderer();
  private static final Instant NOW = Instant.parse("2026-10-26T18:00:00Z");

  private static Header header(boolean approved) {
    return new Header(
        UUID.randomUUID(),
        "EXP-2026-0012",
        "CVEA03",
        "José Peña Núñez",
        "jose@example.com",
        "Grupo WILGEN y Asociados S. de R.L. de C.V.",
        "Representante Legal",
        "Representante legal",
        "Texto de responsabilidad “aprobado” — con acentos: áéíóú ñ.",
        "Aviso de privacidad aplicable.",
        approved,
        3,
        "a".repeat(64),
        null,
        NOW);
  }

  private static List<ReceivedItem> items(int n) {
    List<ReceivedItem> items = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      items.add(new ReceivedItem("Identificación oficial " + i, "José Peña", 2, NOW, true, "ACCEPTED"));
      items.add(new ReceivedItem("Identificación oficial " + i, "José Peña", 1, NOW.minusSeconds(86400), false, "ACCEPTED"));
    }
    return items;
  }

  @Test
  void draftCarriesWatermarkAndPendingLegalNotice() throws Exception {
    byte[] pdf = renderer.render(header(false), "EXP-2026-0012-A1", items(3), NOW, null);
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      String text = new PDFTextStripper().getText(doc);
      assertThat(text).contains("ACUSE DE RECEPCIÓN DE DOCUMENTOS").contains("José Peña Núñez").contains("BORRADOR").contains("PENDIENTE DE APROBACIÓN LEGAL");
      assertThat(text).contains("(versión anterior)");
    }
  }

  @Test
  void manyDocumentsSpanPagesAndSignedVersionHasNoWatermark() throws Exception {
    byte[] png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");
    byte[] pdf = renderer.render(header(true), "EXP-2026-0012-A2", items(40), NOW, new Signature("Representante Legal", "Representante legal", png, NOW, "b".repeat(64)));
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      assertThat(doc.getNumberOfPages()).isGreaterThan(1);
      String text = new PDFTextStripper().getText(doc);
      assertThat(text).doesNotContain("BORRADOR").doesNotContain("PENDIENTE DE APROBACIÓN").contains("Firmado electrónicamente");
    }
  }
}
