package com.c21genera.shared.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Orígenes permitidos por CORS. Nunca "*" en producción (ver AGENTS §119). */
@ConfigurationProperties(prefix = "app.security.cors")
public record CorsProperties(List<String> allowedOrigins) {}
