package com.c21genera.documents.domain;

import com.c21genera.shared.domain.DomainException;
import org.springframework.http.HttpStatus;

public class UnsupportedFileException extends DomainException {

  public UnsupportedFileException(String detectedMimeType) {
    super(
        "UNSUPPORTED_FILE_TYPE",
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "Formato de archivo no permitido: " + detectedMimeType);
  }
}
