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
