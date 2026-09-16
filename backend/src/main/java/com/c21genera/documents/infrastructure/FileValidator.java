package com.c21genera.documents.infrastructure;

import com.c21genera.documents.domain.UnsupportedFileException;
import com.c21genera.shared.config.UploadProperties;
import java.util.List;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Nunca confía en el Content-Type que manda el navegador ni en la extensión
 * del archivo: detecta el MIME real con Apache Tika (ver AGENTS §31).
 */
@Component
public class FileValidator {

  private final Tika tika = new Tika();
  private final UploadProperties properties;

  public FileValidator(UploadProperties properties) {
    this.properties = properties;
  }

  public record ValidatedFile(byte[] content, String detectedMimeType) {}

  public ValidatedFile validatePublic(byte[] content) {
    return validate(content, properties.allowedPublicMimeTypes());
  }

  public ValidatedFile validateInternal(byte[] content) {
    return validate(content, properties.allowedInternalMimeTypes());
  }

  private ValidatedFile validate(byte[] content, List<String> allowedMimeTypes) {
    if (content.length > properties.maxFileSizeBytes()) {
      throw new MaxUploadSizeExceededException(properties.maxFileSizeBytes());
    }
    String detected = tika.detect(content);
    if (!allowedMimeTypes.contains(detected)) {
      throw new UnsupportedFileException(detected);
    }
    return new ValidatedFile(content, detected);
  }
}
