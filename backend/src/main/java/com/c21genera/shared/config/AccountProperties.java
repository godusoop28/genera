package com.c21genera.shared.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Control de Cambios #25: cuentas del personal interno.
 *
 * <ul>
 *   <li>linkBaseUrl: página del front donde la persona define su contraseña (se le agrega /{token}).
 *   <li>inviteTtl: vigencia del enlace de alta. resetTtl: vigencia del enlace de restablecimiento.
 *   <li>maxFailedLogins / lockDuration: bloqueo temporal tras varios intentos fallidos.
 * </ul>
 */
@ConfigurationProperties(prefix = "app.security.account")
public record AccountProperties(String linkBaseUrl, Duration inviteTtl, Duration resetTtl, int maxFailedLogins, Duration lockDuration) {

  public AccountProperties {
    if (linkBaseUrl == null || linkBaseUrl.isBlank()) {
      linkBaseUrl = "http://localhost:3000/acceso";
    }
    linkBaseUrl = linkBaseUrl.replaceAll("/+$", "");
    if (inviteTtl == null || inviteTtl.isNegative() || inviteTtl.isZero()) {
      inviteTtl = Duration.ofHours(72);
    }
    if (resetTtl == null || resetTtl.isNegative() || resetTtl.isZero()) {
      resetTtl = Duration.ofHours(24);
    }
    if (maxFailedLogins <= 0) {
      maxFailedLogins = 5;
    }
    if (lockDuration == null || lockDuration.isNegative() || lockDuration.isZero()) {
      lockDuration = Duration.ofMinutes(15);
    }
  }
}
