package com.c21genera.shared.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ttl: cuánto tiempo funciona una liga de cliente desde que se genera
 * (después hay que generar una nueva). Por defecto 30 días.
 */
@ConfigurationProperties(prefix = "app.security.public-link")
public record PublicLinkProperties(String baseUrl, Duration ttl) {

  public PublicLinkProperties {
    if (ttl == null || ttl.isNegative() || ttl.isZero()) {
      ttl = Duration.ofDays(30);
    }
  }
}
