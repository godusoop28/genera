package com.c21genera.shared.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * signingBaseUrl: base de la liga personal de firma (el token se agrega al
 * final). Si está vacía se deriva de la liga pública del cliente (ver
 * {@link #signingBaseUrlOr(String)}).
 */
@ConfigurationProperties(prefix = "app.contracts")
public record ContractsProperties(String signingBaseUrl, Duration signingLinkTtl) {

  public ContractsProperties {
    if (signingLinkTtl == null || signingLinkTtl.isNegative() || signingLinkTtl.isZero()) {
      signingLinkTtl = Duration.ofDays(15);
    }
  }

  /** "https://app.com/carga" -> "https://app.com/firma". */
  public String signingBaseUrlOr(String publicLinkBaseUrl) {
    if (signingBaseUrl != null && !signingBaseUrl.isBlank()) {
      return signingBaseUrl;
    }
    String base = publicLinkBaseUrl == null ? "http://localhost:3000/carga" : publicLinkBaseUrl.replaceAll("/+$", "");
    int slash = base.lastIndexOf('/');
    return (slash > "https://".length() ? base.substring(0, slash) : base) + "/firma";
  }
}
