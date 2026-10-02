package com.c21genera.documents.domain;

import com.c21genera.shared.domain.DomainException;
import org.springframework.http.HttpStatus;

public class UnsupportedFileException extends DomainException {

  public UnsupportedFileException(String detectedMimeType) {
    super("UNSUPPORTED_FILE_TYPE", HttpStatus.UNSUPPORTED_MEDIA_TYPE, messageFor(detectedMimeType));
  }

  private static String messageFor(String detectedMimeType) {
    if (detectedMimeType != null && (detectedMimeType.contains("heic") || detectedMimeType.contains("heif"))) {
      return "Las fotos HEIC del iPhone se convierten solas al subirlas desde el navegador; si ves este mensaje, vuelve a intentarlo o"
          + " envía la foto como JPG.";
    }
    return "Formato de archivo no permitido (" + detectedMimeType + "). Sube una foto (JPG, PNG o WEBP) o un PDF.";
  }
}
