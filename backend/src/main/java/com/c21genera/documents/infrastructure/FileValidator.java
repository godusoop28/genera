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

  /** Bytes del inicio del archivo que bastan para detectar su tipo real (firma "mágica"). */
  public static final int HEAD_BYTES = 64 * 1024;

  public ValidatedFile validatePublic(byte[] content) {
    return new ValidatedFile(content, validate(content.length, content, properties.allowedPublicMimeTypes()));
  }

  public ValidatedFile validateInternal(byte[] content) {
    return new ValidatedFile(content, validate(content.length, content, properties.allowedInternalMimeTypes()));
  }

  /** Valida sin tener el archivo en memoria: el tamaño y los primeros {@link #HEAD_BYTES} bytes. Devuelve el MIME real. */
  public String validatePublic(long size, byte[] head) {
    return validate(size, head, properties.allowedPublicMimeTypes());
  }

  public String validateInternal(long size, byte[] head) {
    return validate(size, head, properties.allowedInternalMimeTypes());
  }

  private String validate(long size, byte[] head, List<String> allowedMimeTypes) {
    if (size > properties.maxFileSizeBytes()) {
      throw new MaxUploadSizeExceededException(properties.maxFileSizeBytes());
    }
    String detected = tika.detect(head);
    if (!allowedMimeTypes.contains(detected)) {
      throw new UnsupportedFileException(detected);
    }
    return detected;
  }
}
