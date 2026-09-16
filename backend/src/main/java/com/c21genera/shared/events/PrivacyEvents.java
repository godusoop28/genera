package com.c21genera.shared.events;

import java.util.UUID;

/** Eventos de integración publicados por privacy (ver AGENTS §7/§84). */
public final class PrivacyEvents {

  private PrivacyEvents() {}

  public record PrivacyAccepted(UUID expedienteId, boolean mainPurposesAccepted, boolean secondaryPurposesAccepted) {}
}
