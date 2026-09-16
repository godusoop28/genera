package com.c21genera.shared.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del proveedor de inteligencia artificial documental. Cuando
 * {@code enabled=false} (por defecto) ningún documento se envía a un
 * proveedor externo: se usa {@code StubDocumentIntelligenceProvider}.
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
    boolean enabled, String provider, String apiKey, String model, String baseUrl, Duration timeout, int maxRetries) {}
