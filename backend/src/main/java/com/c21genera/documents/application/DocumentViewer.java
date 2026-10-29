package com.c21genera.documents.application;

import com.c21genera.documents.DocumentsApi.PageView;
import com.c21genera.documents.domain.DocumentVersion;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.pdf.PdfFiles;
import com.c21genera.shared.storage.FileStorage;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

/**
 * Visor de documentos sin descarga (Control de Cambios #6): el servidor convierte cada página
 * en una imagen con marca de agua (quién consulta y cuándo), así que quien no tiene permiso de
 * descarga nunca recibe el archivo original. No impide una captura de pantalla, pero la marca de
 * agua deja rastro de quién la tomó.
 */
@Service
public class DocumentViewer {

  /** Suficiente para leer una escritura en pantalla sin acercarse demasiado. */
  private static final int PDF_DPI = 110;

  private static final int MAX_IMAGE_WIDTH = 1600;
  private static final Duration CACHE_TTL = Duration.ofMinutes(10);
  private static final int CACHE_SIZE = 2;
  private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Mexico_City"));

  /** El servidor tiene 512 MB: las páginas se renderizan de una en una. */
  private final Semaphore rendering = new Semaphore(1);

  private final DocumentService documentService;
  private final FileStorage fileStorage;
  private final Clock clock;

  /** PDFs recién consultados, en disco: hojear una escritura no la vuelve a descargar por página. */
  private final Map<String, CachedFile> cache = new LinkedHashMap<>();

  private record CachedFile(Path path, Instant openedAt) {}

  public record ViewerInfo(int pageCount) {}

  public DocumentViewer(DocumentService documentService, FileStorage fileStorage, Clock clock) {
    this.documentService = documentService;
    this.fileStorage = fileStorage;
    this.clock = clock;
  }

  public ViewerInfo info(UUID versionId) {
    DocumentVersion version = documentService.getVersion(versionId);
    if (version.getStorageKeyPdf() != null) {
      return new ViewerInfo(PdfFiles.pageCount(cachedPdf(version.getStorageKeyPdf())));
    }
    List<PageView> pages = documentService.pagesOf(versionId);
    if (pages.isEmpty()) {
      throw new NotFoundException("El documento todavía no tiene un archivo para consultar.");
    }
    // Sin PDF armado (p. ej. no pasó la calidad): se muestran las imágenes originales; un PDF
    // original que nunca se procesó cuenta sus propias páginas.
    if (pages.size() == 1 && "application/pdf".equals(pages.getFirst().mimeType())) {
      return new ViewerInfo(PdfFiles.pageCount(cachedPdf(pages.getFirst().storageKeyOriginal())));
    }
    return new ViewerInfo(pages.size());
  }

  /** JPEG de la página (1 = la primera) con la marca de agua de quien consulta. */
  public byte[] page(UUID versionId, int pageNumber, String viewerName) {
    DocumentVersion version = documentService.getVersion(versionId);
    try {
      rendering.acquire();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Se interrumpió la consulta del documento", e);
    }
    try {
      BufferedImage image = render(version, versionId, pageNumber);
      watermark(image, viewerName);
      return jpeg(image);
    } finally {
      rendering.release();
    }
  }

  private BufferedImage render(DocumentVersion version, UUID versionId, int pageNumber) {
    String pdfKey = version.getStorageKeyPdf();
    List<PageView> pages = pdfKey == null ? documentService.pagesOf(versionId) : List.of();
    if (pdfKey == null && pages.size() == 1 && "application/pdf".equals(pages.getFirst().mimeType())) {
      pdfKey = pages.getFirst().storageKeyOriginal();
    }
    if (pdfKey != null) {
      try (PDDocument document = PdfFiles.open(cachedPdf(pdfKey))) {
        if (pageNumber < 1 || pageNumber > document.getNumberOfPages()) {
          throw new NotFoundException("La página " + pageNumber + " no existe.");
        }
        return PdfFiles.renderPage(PdfFiles.renderer(document), document, pageNumber - 1, PDF_DPI);
      } catch (IOException e) {
        throw new UncheckedIOException("No se pudo mostrar la página", e);
      }
    }
    PageView page =
        pages.stream().filter(p -> p.pageNumber() == pageNumber).findFirst().orElseThrow(() -> new NotFoundException("La página no existe."));
    try (InputStream in = fileStorage.get(page.storageKeyOriginal())) {
      BufferedImage original = ImageIO.read(in);
      if (original == null) {
        throw new NotFoundException("Esta página no se puede mostrar en el visor.");
      }
      return toRgb(original);
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo mostrar la página", e);
    }
  }

  private synchronized Path cachedPdf(String storageKey) {
    Instant now = clock.instant();
    for (Iterator<Map.Entry<String, CachedFile>> it = cache.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<String, CachedFile> entry = it.next();
      if (entry.getValue().openedAt().plus(CACHE_TTL).isBefore(now) || !Files.exists(entry.getValue().path())) {
        deleteQuietly(entry.getValue().path());
        it.remove();
      }
    }
    CachedFile hit = cache.remove(storageKey);
    if (hit != null) {
      cache.put(storageKey, new CachedFile(hit.path(), now));
      return hit.path();
    }
    try {
      Path file = PdfFiles.toTempFile(fileStorage.get(storageKey), ".pdf");
      cache.put(storageKey, new CachedFile(file, now));
      while (cache.size() > CACHE_SIZE) {
        Map.Entry<String, CachedFile> oldest = cache.entrySet().iterator().next();
        deleteQuietly(oldest.getValue().path());
        cache.remove(oldest.getKey());
      }
      return file;
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo leer el documento", e);
    }
  }

  private static BufferedImage toRgb(BufferedImage source) {
    double scale = source.getWidth() > MAX_IMAGE_WIDTH ? (double) MAX_IMAGE_WIDTH / source.getWidth() : 1.0;
    int width = (int) Math.round(source.getWidth() * scale);
    int height = (int) Math.round(source.getHeight() * scale);
    BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = rgb.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, width, height);
    g.drawImage(source, 0, 0, width, height, null);
    g.dispose();
    return rgb;
  }

  /** Texto diagonal repetido más una franja inferior: quién consultó, cuándo y que es solo consulta. */
  private void watermark(BufferedImage image, String viewerName) {
    String text = (viewerName == null || viewerName.isBlank() ? "Personal C21 Genera" : viewerName) + " · " + WHEN.format(clock.instant());
    Graphics2D g = image.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    int w = image.getWidth();
    int h = image.getHeight();
    int size = Math.max(14, w / 38);
    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.13f));
    g.setColor(new Color(40, 40, 40));
    AffineTransform original = g.getTransform();
    g.rotate(-Math.PI / 6, w / 2.0, h / 2.0);
    String line = "SOLO CONSULTA · " + text;
    int step = size * 6;
    for (int y = -h; y < h * 2; y += step) {
      for (int x = -w; x < w * 2; x += g.getFontMetrics().stringWidth(line) + size * 3) {
        g.drawString(line, x, y);
      }
    }
    g.setTransform(original);
    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.8f));
    int band = size + size / 2;
    g.setColor(new Color(255, 255, 255));
    g.fillRect(0, h - band, w, band);
    g.setColor(new Color(60, 60, 60));
    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(11, size * 2 / 3)));
    g.drawString("Consultado por " + text + " · Prohibida su copia o descarga", size / 2, h - band / 3);
    g.dispose();
  }

  private static byte[] jpeg(BufferedImage image) {
    ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    try (ByteArrayOutputStream out = new ByteArrayOutputStream(); MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(out)) {
      ImageWriteParam params = writer.getDefaultWriteParam();
      params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      params.setCompressionQuality(0.8f);
      writer.setOutput(stream);
      writer.write(null, new IIOImage(image, null, null), params);
      stream.flush();
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo preparar la página", e);
    } finally {
      writer.dispose();
    }
  }

  private static void deleteQuietly(Path file) {
    try {
      Files.deleteIfExists(file);
    } catch (IOException ignored) {
      // Es un temporal: si no se borra ahora lo limpia el sistema.
    }
  }
}
