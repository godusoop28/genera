package com.c21genera.shared.web;

import com.c21genera.shared.config.UploadProperties;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Límites de carga vigentes, para que el navegador avise ANTES de subir un
 * archivo que el servidor rechazaría (frontend y backend con el mismo número).
 */
@RestController
public class UploadLimitsController {

  private final UploadProperties properties;

  public UploadLimitsController(UploadProperties properties) {
    this.properties = properties;
  }

  public record UploadLimitsResponse(long maxFileSizeBytes, int maxFilesPerRequest, List<String> allowedMimeTypes) {}

  @GetMapping("/api/v1/public/upload-limits")
  public UploadLimitsResponse limits() {
    return new UploadLimitsResponse(properties.maxFileSizeBytes(), properties.maxFilesPerRequest(), properties.allowedPublicMimeTypes());
  }
}
