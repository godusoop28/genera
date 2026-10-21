package com.c21genera.shared.events;

import java.util.UUID;

/**
 * Eventos de integración del módulo identity (Control de Cambios #25). La
 * bitácora guarda quién hizo la acción, a qué cuenta y cuándo. Nunca viajan
 * contraseñas ni enlaces.
 */
public final class UserAccountEvents {

  private UserAccountEvents() {}

  /**
   * action: CREATED, UPDATED, ROLE_CHANGED, DEACTIVATED, ACTIVATED, OFFBOARDED,
   * INVITE_LINK_ISSUED, RESET_LINK_ISSUED, PASSWORD_SET, PASSWORD_CHANGED, LOCKED, UNLOCKED.
   * summary: descripción legible (sin datos sensibles).
   */
  public record UserAccountChanged(UUID userId, String userName, String action, String summary, Actor actor) {}
}
