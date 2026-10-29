package com.c21genera.shared.pdf;

import java.awt.image.BufferedImage;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

/**
 * Apertura de PDFs para documentos inmobiliarios que pueden pesar decenas de
 * MB (escrituras escaneadas de 80+ páginas): los flujos internos de PDFBox se
 * guardan en un archivo temporal en vez de en memoria, para no agotar el heap
 * del servidor. Quien llama cierra el documento.
 */
public final class PdfFiles {

  private PdfFiles() {}

  /** El PDF está dañado, protegido con contraseña o no es un PDF. */
  public static class UnreadablePdfException extends RuntimeException {
    public UnreadablePdfException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static PDDocument open(byte[] content) {
    try {
      return withoutResourceCache(Loader.loadPDF(new RandomAccessReadBuffer(content), "", null, null, IOUtils.createTempFileOnlyStreamCache()));
    } catch (InvalidPasswordException e) {
      throw new UnreadablePdfException("El PDF está protegido con contraseña; súbelo sin contraseña o como fotos de las páginas", e);
    } catch (IOException e) {
      throw new UnreadablePdfException("El PDF está dañado o no se puede abrir; vuelve a generarlo o sube fotos de las páginas", e);
    }
  }

  /**
   * Abre el PDF directamente desde un archivo: PDFBox lo lee del disco conforme lo
   * necesita, sin cargarlo entero en memoria (un PDF de 40 MB ocupaba 40 MB de
   * heap, más una copia transitoria al descargarlo).
   */
  public static PDDocument open(java.nio.file.Path file) {
    try {
      return withoutResourceCache(Loader.loadPDF(file.toFile(), "", null, null, IOUtils.createTempFileOnlyStreamCache()));
    } catch (InvalidPasswordException e) {
      throw new UnreadablePdfException("El PDF está protegido con contraseña; súbelo sin contraseña o como fotos de las páginas", e);
    } catch (IOException e) {
      throw new UnreadablePdfException("El PDF está dañado o no se puede abrir; vuelve a generarlo o sube fotos de las páginas", e);
    }
  }

  /** Número de páginas leyendo el archivo desde disco. */
  public static int pageCount(java.nio.file.Path file) {
    try (PDDocument document = open(file)) {
      return document.getNumberOfPages();
    } catch (IOException e) {
      throw new UnreadablePdfException("El PDF está dañado o no se puede abrir", e);
    }
  }

  /** Copia un flujo a un archivo temporal (que quien llama debe borrar) para trabajar desde el disco. */
  public static java.nio.file.Path toTempFile(java.io.InputStream content, String suffix) throws IOException {
    java.nio.file.Path file = java.nio.file.Files.createTempFile("c21-", suffix);
    try (content) {
      java.nio.file.Files.copy(content, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      return file;
    } catch (IOException | RuntimeException e) {
      java.nio.file.Files.deleteIfExists(file);
      throw e;
    }
  }

  /**
   * Sin caché de recursos: PDFBox guardaba cada imagen decodificada de cada
   * página en referencias "suaves", que solo se liberan cuando la memoria está a
   * punto de agotarse, así que el heap siempre llegaba a su tope (E2E 02/10:
   * 196/199 MB al analizar un PDF escaneado de 38.9 MB). Aquí cada página se
   * renderiza una sola vez, así que la caché no ahorra nada.
   */
  private static PDDocument withoutResourceCache(PDDocument document) {
    document.setResourceCache(null);
    return document;
  }

  /**
   * Renderizador que decodifica cada imagen ya reducida al tamaño en que se dibuja. Sin esto, un
   * PDF con fotos de celular de 12 MP se decodificaba completo y no cabía en el heap ni con 160 MB
   * (28/10: la INE de dos fotos 4032×3024 nunca terminaba y el visor daba "Ocurrió un error
   * inesperado"); así basta con unos 60 MB a 220 DPI. Las imágenes grandes en Flate (capturas de
   * pantalla) se decodifican con {@link LeanPdfRenderer}.
   */
  public static PDFRenderer renderer(PDDocument document) {
    PDFRenderer renderer = new LeanPdfRenderer(document);
    renderer.setSubsamplingAllowed(true);
    return renderer;
  }

  /**
   * Tope de píxeles de una página renderizada (~24 MB en RGB). Una captura de pantalla larga del
   * iPhone (página de 1265×4327 pt, 29/10) a 220 DPI pedía 3865×13224 px, unos 204 MB, y agotaba
   * el heap: ni se leía con IA ni se mostraba en el visor. Con 12 MP tampoco cabía en producción,
   * donde el heap ya trae ~95 MB ocupados y PDFBox hace copias del escaneo para convertir el color.
   * 6 MP alcanzan para una hoja tamaño oficio a 220 DPI y para esa captura a su resolución original.
   */
  public static final long MAX_RENDER_PIXELS = 6_000_000L;

  /** Renderiza la página (0 = la primera) en RGB, bajando los DPI si excede {@link #MAX_RENDER_PIXELS}. */
  public static BufferedImage renderPage(PDFRenderer renderer, PDDocument document, int pageIndex, float dpi) throws IOException {
    return renderer.renderImageWithDPI(pageIndex, cappedDpi(document.getPage(pageIndex).getCropBox(), dpi), ImageType.RGB);
  }

  static float cappedDpi(PDRectangle box, float dpi) {
    double pixels = (box.getWidth() / 72.0 * dpi) * (box.getHeight() / 72.0 * dpi);
    if (pixels <= MAX_RENDER_PIXELS) {
      return dpi;
    }
    return (float) (dpi * Math.sqrt(MAX_RENDER_PIXELS / pixels));
  }

  /** Documento nuevo cuyo contenido intermedio también va a disco. */
  public static PDDocument create() {
    return new PDDocument(IOUtils.createTempFileOnlyStreamCache());
  }

  /** Número de páginas, o lanza {@link UnreadablePdfException} si no se puede abrir. */
  public static int pageCount(byte[] content) {
    try (PDDocument document = open(content)) {
      return document.getNumberOfPages();
    } catch (IOException e) {
      throw new UnreadablePdfException("El PDF está dañado o no se puede abrir", e);
    }
  }
}
