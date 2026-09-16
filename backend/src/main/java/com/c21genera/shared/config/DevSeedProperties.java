package com.c21genera.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Solo activo en el perfil {@code local}. Nunca debe existir en producción. */
@ConfigurationProperties(prefix = "app.dev-seed")
public record DevSeedProperties(
    boolean enabled, String adminName, String adminEmail, String adminPassword, boolean seedDemoExpediente) {}
