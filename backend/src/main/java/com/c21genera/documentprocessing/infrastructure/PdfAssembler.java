package com.c21genera.documentprocessing.infrastructure;

import com.c21genera.shared.pdf.PdfFiles;
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

  /**
   * storageKey: de dónde se puede volver a leer el contenido (null si solo está en memoria).
   * file: el PDF ya descargado a disco; si está, se usa en vez de {@code content}.
   */
  public record PageContent(byte[] content, String mimeType, String storageKey, java.nio.file.Path file) {

    public PageContent(byte[] content, String mimeType, String storageKey) {
      this(content, mimeType, storageKey, null);
    }

    public PageContent(byte[] content, String mimeType) {
      this(content, mimeType, null, null);
    }
  }

  public byte[] assemble(List<PageContent> pages) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    assemble(pages, out);
    return out.toByteArray();
  }

  /**
   * Escribe el PDF en {@code out}. Los PDF se abren desde disco cuando vienen con {@code file}: unir
   * dos PDF escaneados de 30 MB en memoria no cabía en el servidor (04/11). Los PDF de origen
   * quedan abiertos hasta guardar: PDFBox copia sus objetos al guardar, no al unir.
   */
  public void assemble(List<PageContent> pages, java.io.OutputStream out) {
    List<PDDocument> sources = new java.util.ArrayList<>();
    try (PDDocument document = PdfFiles.create()) {
      PDFMergerUtility merger = new PDFMergerUtility();
      for (PageContent page : pages) {
        if ("application/pdf".equals(page.mimeType())) {
          PDDocument source = page.file() != null ? PdfFiles.open(page.file()) : PdfFiles.open(page.content());
          sources.add(source);
          merger.appendDocument(document, source);
        } else {
          appendImagePage(document, page.content());
        }
      }
      document.save(out);
    } catch (IOException e) {
      throw new IllegalStateException("No se pudo ensamblar el PDF del documento", e);
    } finally {
      for (PDDocument source : sources) {
        try {
          source.close();
        } catch (IOException ignored) {
          // Solo se leía.
        }
      }
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
