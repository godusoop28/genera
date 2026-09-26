package com.c21genera.shared.events;

import java.time.Instant;
import java.util.UUID;

/** Eventos de integración publicados por publicaccess (ver AGENTS §7/§84). */
public final class PublicAccessEvents {

  private PublicAccessEvents() {}

  public record PublicLinkGenerated(UUID expedienteId, Actor actor, Instant expiresAt) {}

  /** reason: por qué dejó de funcionar (revocada por el staff, expediente cerrado...). */
  public record PublicLinkRevoked(UUID expedienteId, Actor actor, String reason) {}
}
