package com.c21genera.documentprocessing.domain;

/**
 * Normaliza una imagen cargada por el cliente (orientación EXIF, formato) a
 * un JPEG consistente antes de análisis de calidad y ensamblado de PDF (ver
 * AGENTS §35-36). Determinístico: no usa IA.
 */
public interface ImageNormalizer {

  NormalizedImage normalize(byte[] original, String sourceMimeType);

  record NormalizedImage(byte[] content, String mimeType, int widthPx, int heightPx) {}

  class UnreadableImageException extends RuntimeException {
    public UnreadableImageException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
