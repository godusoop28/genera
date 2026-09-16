package com.c21genera.contracts.infrastructure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Convierte DOCX a PDF invocando LibreOffice en modo headless
 * ({@code soffice --headless --convert-to pdf}), tal como sugiere AGENTS
 * §79. Requiere que el binario esté disponible en el contenedor/servidor
 * (ver backend/Dockerfile); si no lo está, falla con un mensaje claro en
 * lugar de generar un PDF distinto al contrato oficial.
 *
 * <p>Deshabilitado por defecto ({@code app.contracts.pdf-conversion-enabled=false})
 * porque este entorno de desarrollo/sandbox no tiene LibreOffice instalado.
 */
@Component
public class LibreOfficePdfConverter implements PdfConverter {

  @Value("${app.contracts.pdf-conversion-enabled:false}")
  private boolean enabled;

  @Value("${app.contracts.soffice-path:soffice}")
  private String sofficeBinary;

  @Override
  public byte[] toPdf(byte[] docxBytes) throws IOException {
    if (!enabled) {
      throw new IllegalStateException(
          "La conversión a PDF está deshabilitada (app.contracts.pdf-conversion-enabled=false). "
              + "Requiere LibreOffice instalado; ver backend/README.md.");
    }
    Path tempDir = Files.createTempDirectory("contract-pdf");
    Path docxPath = tempDir.resolve("contract.docx");
    Files.write(docxPath, docxBytes);

    try {
      Process process =
          new ProcessBuilder(sofficeBinary, "--headless", "--convert-to", "pdf", "--outdir", tempDir.toString(), docxPath.toString())
              .redirectErrorStream(true)
              .start();
      boolean finished = waitFor(process, Duration.ofSeconds(30));
      if (!finished || process.exitValue() != 0) {
        throw new IOException("La conversión con LibreOffice falló o excedió el tiempo de espera.");
      }
      Path pdfPath = tempDir.resolve("contract.pdf");
      return Files.readAllBytes(pdfPath);
    } finally {
      deleteQuietly(tempDir);
    }
  }

  private static boolean waitFor(Process process, Duration timeout) {
    try {
      return process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private static void deleteQuietly(Path dir) {
    try (var stream = Files.walk(dir)) {
      stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
    } catch (IOException ignored) {
      // limpieza best-effort de archivos temporales
    }
  }
}
