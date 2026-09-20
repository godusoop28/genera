package com.c21genera.shared.events;

import java.util.UUID;

/** Eventos de integración publicados por publicaccess (ver AGENTS §7/§84). */
public final class PublicAccessEvents {

  private PublicAccessEvents() {}

  public record PublicLinkGenerated(UUID expedienteId) {}
}
