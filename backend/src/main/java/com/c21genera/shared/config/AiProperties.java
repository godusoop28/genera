package com.c21genera.shared.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del proveedor de inteligencia artificial documental. Cuando
 * {@code enabled=false} (por defecto) ningún documento se envía a un
 * proveedor externo: se usa {@code StubDocumentIntelligenceProvider}.
 *
 * <p>maxPages: tope de páginas que se revisan de un documento largo (acota
 * costo y tiempo; la revisión se detiene antes si ya encontró todo).
 * pagesPerBatch: páginas que se mandan juntas en cada petición.
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
    boolean enabled,
    String provider,
    String apiKey,
    String model,
    String baseUrl,
    Duration timeout,
    int maxRetries,
    int maxPages,
    int pagesPerBatch) {

  public AiProperties {
    if (maxPages <= 0) {
      maxPages = 96;
    }
    if (pagesPerBatch <= 0) {
      pagesPerBatch = 8;
    }
  }

  // Un solo constructor a propósito: con dos, Spring Boot no sabe con cuál enlazar
  // la configuración y el arranque falla ("No default constructor found").
}
