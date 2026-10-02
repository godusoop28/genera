package com.c21genera.shared.pdf;

import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

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
      return Loader.loadPDF(new RandomAccessReadBuffer(content), "", null, null, IOUtils.createTempFileOnlyStreamCache());
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
      return Loader.loadPDF(file.toFile(), "", null, null, IOUtils.createTempFileOnlyStreamCache());
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
