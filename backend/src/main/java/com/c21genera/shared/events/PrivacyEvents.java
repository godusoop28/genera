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
}
