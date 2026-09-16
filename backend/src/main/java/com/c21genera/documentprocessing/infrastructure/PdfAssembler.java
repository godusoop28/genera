package com.c21genera.documentprocessing.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

/**
 * Ensambla las páginas ordenadas de un {@code DocumentVersion} en un único
 * PDF (ver AGENTS §37). Cada página puede ser una imagen normalizada (foto
 * del cliente) o un PDF ya existente (carga interna de staff), en cuyo caso
 * sus páginas se copian tal cual.
 */
@Component
public class PdfAssembler {

  public record PageContent(byte[] content, String mimeType) {}

  public byte[] assemble(List<PageContent> pages) {
    try (PDDocument document = new PDDocument()) {
      PDFMergerUtility merger = new PDFMergerUtility();
      for (PageContent page : pages) {
        if ("application/pdf".equals(page.mimeType())) {
          try (PDDocument source = org.apache.pdfbox.Loader.loadPDF(page.content())) {
            merger.appendDocument(document, source);
          }
        } else {
          appendImagePage(document, page.content());
        }
      }
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo ensamblar el PDF del documento", e);
    }
  }

  private void appendImagePage(PDDocument document, byte[] jpegBytes) throws IOException {
    PDImageXObject image = PDImageXObject.createFromByteArray(document, jpegBytes, "page");
    float width = image.getWidth();
    float height = image.getHeight();

    PDRectangle pageSize =
        width >= height
            ? new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth())
            : PDRectangle.A4;
    PDPage page = new PDPage(pageSize);
    document.addPage(page);

    float scale = Math.min(pageSize.getWidth() / width, pageSize.getHeight() / height);
    float drawWidth = width * scale;
    float drawHeight = height * scale;
    float x = (pageSize.getWidth() - drawWidth) / 2;
    float y = (pageSize.getHeight() - drawHeight) / 2;

    try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
      contentStream.drawImage(image, x, y, drawWidth, drawHeight);
    }
  }
}
