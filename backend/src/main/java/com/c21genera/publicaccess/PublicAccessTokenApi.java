package com.c21genera.publicaccess;

import com.c21genera.shared.events.Actor;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** API pública del módulo publicaccess (ver AGENTS §14/§15/§161-162). */
public interface PublicAccessTokenApi {

  /** Genera una liga nueva con vencimiento; cualquier liga anterior del expediente deja de funcionar. */
  IssuedToken generate(UUID expedienteId, Actor actor);

  void revoke(UUID expedienteId, Actor actor, String reason);

  /**
   * Resuelve un token crudo (tal como llega en la URL) al expediente al que
   * pertenece, solo si está activo, no expirado y no revocado. Este es el
   * único punto de entrada seguro para la API pública: nunca se debe
   * confiar en un expedienteId recibido directamente del cliente.
   */
  Optional<UUID> resolve(String rawToken);

  /** Estado de la liga más reciente del expediente (sin el token: solo se guarda su hash). */
  Optional<LinkStatus> statusOf(UUID expedienteId);

  record IssuedToken(String rawToken, Instant expiresAt) {}

  record LinkStatus(Instant createdAt, Instant expiresAt, Instant revokedAt, Instant lastUsedAt, boolean usable) {}
}
