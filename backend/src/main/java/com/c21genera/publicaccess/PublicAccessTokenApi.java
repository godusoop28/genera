package com.c21genera.publicaccess;

import java.util.Optional;
import java.util.UUID;

/** API pública del módulo publicaccess (ver AGENTS §14/§15/§161-162). */
public interface PublicAccessTokenApi {

  IssuedToken generate(UUID expedienteId);

  IssuedToken regenerate(UUID expedienteId);

  void revoke(UUID expedienteId);

  /**
   * Resuelve un token crudo (tal como llega en la URL) al expediente al que
   * pertenece, solo si está activo, no expirado y no revocado. Este es el
   * único punto de entrada seguro para la API pública: nunca se debe
   * confiar en un expedienteId recibido directamente del cliente.
   */
  Optional<UUID> resolve(String rawToken);

  record IssuedToken(String rawToken, java.time.Instant expiresAt) {}
}
