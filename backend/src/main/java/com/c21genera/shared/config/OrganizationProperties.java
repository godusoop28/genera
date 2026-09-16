package com.c21genera.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Perfil de la organización que opera esta instancia del sistema. No es
 * multi-tenant: es una sola inmobiliaria por despliegue (ver AGENTS §123),
 * pero se evita hardcodear el nombre por todo el código para poder
 * reutilizar el sistema con otra inmobiliaria en el futuro.
 */
@ConfigurationProperties(prefix = "app.organization")
public record OrganizationProperties(String legalName, String commercialName, String profecoRegistration) {}
