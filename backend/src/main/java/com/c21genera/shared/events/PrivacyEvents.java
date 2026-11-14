package com.c21genera.shared.events;

import java.util.UUID;

/** Eventos de integración publicados por privacy (ver AGENTS §7/§84). */
public final class PrivacyEvents {

  private PrivacyEvents() {}

  /** physical: se firmó en papel y el staff (actor) cargó el escaneo; si no, lo firmó el cliente en su liga. */
  public record PrivacyAccepted(
      UUID expedienteId, boolean mainPurposesAccepted, boolean secondaryPurposesAccepted, boolean physical, Actor actor) {

    public PrivacyAccepted(UUID expedienteId, boolean mainPurposesAccepted, boolean secondaryPurposesAccepted) {
      this(expedienteId, mainPurposesAccepted, secondaryPurposesAccepted, false, Actor.client());
    }
  }

  /** Control de Cambios #44: el staff generó (o reenvió) la liga exclusiva para firmar el aviso. */
  public record PrivacySignLinkIssued(UUID expedienteId, java.time.Instant expiresAt, Actor actor) {}

  /** Control de Cambios #44: el staff desactivó la liga de firma del aviso. */
  public record PrivacySignLinkRevoked(UUID expedienteId, Actor actor) {}
}
