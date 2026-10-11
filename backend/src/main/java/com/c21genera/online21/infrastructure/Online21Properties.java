package com.c21genera.online21.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Integración con la API REST de 21Online ("API Genio Inmobiliario",
 * documentación en {@code <base-url>/api/docs.html}).
 *
 * <p>apiKey: llave JWT que cada usuario genera en 21Online (Mi Perfil → API
 * Keys). Sin ella la integración queda apagada y el bot dice que no pudo
 * consultar el inventario. Nunca se registra en logs.
 *
 * <p>publicUrlTemplate: enlace público de la ficha para mandarlo al cliente,
 * con {id} como marcador (p. ej. la página de la propiedad en el sitio web de
 * la oficina). Vacío = el bot no manda enlace.
 */
@ConfigurationProperties(prefix = "app.online21")
public record Online21Properties(
    String baseUrl,
    String apiKey,
    String authPrefix,
    String publicUrlTemplate,
    Duration refreshInterval,
    Duration timeout,
    int maxPages,
    boolean pushProspects) {

  public Online21Properties {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "https://plus.21onlinemx.com";
    }
    if (authPrefix == null) {
      authPrefix = "Bearer ";
    }
    if (refreshInterval == null) {
      refreshInterval = Duration.ofHours(3);
    }
    if (timeout == null) {
      timeout = Duration.ofSeconds(20);
    }
    if (maxPages <= 0) {
      maxPages = 40;
    }
  }

  public boolean configured() {
    return apiKey != null && !apiKey.isBlank();
  }
}
