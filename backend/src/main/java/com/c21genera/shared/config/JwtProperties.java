package com.c21genera.shared.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(String issuer, Duration accessTtl, Duration refreshTtl, String secret) {}
