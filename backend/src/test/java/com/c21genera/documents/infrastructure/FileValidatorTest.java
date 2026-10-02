package com.c21genera.documents.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c21genera.documents.domain.UnsupportedFileException;
import com.c21genera.shared.config.UploadProperties;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** Mismos valores que application.yml: 40 MB por archivo; fotos JPG/PNG/WEBP y PDF desde la liga del cliente. */
class FileValidatorTest {

  private static final long FORTY_MB = 40L * 1024 * 1024;

  private final FileValidator validator =
      new FileValidator(
          new UploadProperties(
              FORTY_MB,
              30,
              List.of("image/jpeg", "image/png", "image/webp", "application/pdf"),
              List.of("image/jpeg", "image/png", "image/webp", "application/pdf")));

  /** PDF real con relleno al final para alcanzar el tamaño pedido (Tika lo sigue detectando como PDF). */
  private static byte[] pdfOfSize(int bytes) throws Exception {
    try (PDDocument document = new PDDocument()) {
      document.addPage(new PDPage());
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      document.save(out);
      byte[] pdf = out.toByteArray();
      byte[] padded = new byte[Math.max(bytes, pdf.length)];
      System.arraycopy(pdf, 0, padded, 0, pdf.length);
      return padded;
    }
  }

  @Test
  void aPdfOfMoreThan10MbIsAcceptedWithinTheNewLimit() throws Exception {
    var result = validator.validatePublic(pdfOfSize(18 * 1024 * 1024));

    assertThat(result.detectedMimeType()).isEqualTo("application/pdf");
  }

  @Test
  void aFileOverTheLimitIsRejectedAsTooLarge() throws Exception {
    byte[] huge = pdfOfSize((int) FORTY_MB + 1);

    assertThatThrownBy(() -> validator.validatePublic(huge)).isInstanceOf(MaxUploadSizeExceededException.class);
  }

  @Test
  void webpPhotosAreAcceptedAndTheRealTypeIsDetectedNotTheExtension() {
    // Cabecera RIFF/WEBP mínima: Tika la reconoce por contenido.
    byte[] webp = new byte[] {'R', 'I', 'F', 'F', 0x1A, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' ', 0x0E, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

    assertThat(validator.validatePublic(webp).detectedMimeType()).isEqualTo("image/webp");
  }

  @Test
  void anythingElseIsRejectedWithAFriendlyMessage() {
    assertThatThrownBy(() -> validator.validatePublic("hola, no soy un documento".getBytes()))
        .isInstanceOf(UnsupportedFileException.class)
        .hasMessageContaining("PDF");
  }

  @Test
  void aLargeFileIsValidatedFromItsSizeAndFirstBytesWithoutLoadingIt() throws Exception {
    // E2E 02/10: la carga de un PDF de 38.9 MB ya no lo lee entero a memoria; basta la cabecera.
    byte[] head = java.util.Arrays.copyOf(pdfOfSize(1), FileValidator.HEAD_BYTES);

    assertThat(validator.validatePublic(39L * 1024 * 1024, head)).isEqualTo("application/pdf");
    assertThatThrownBy(() -> validator.validatePublic(FORTY_MB + 1, head)).isInstanceOf(MaxUploadSizeExceededException.class);
  }
}
