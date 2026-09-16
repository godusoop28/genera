package com.c21genera.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.public-link")
public record PublicLinkProperties(String baseUrl) {}
