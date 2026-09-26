package com.c21genera.shared.events;

import java.util.UUID;

/** Eventos de integración publicados por notifications, para la bitácora del expediente. */
public final class NotificationEvents {

  private NotificationEvents() {}

  /** recipient ya viene enmascarado (p. ej. "ju***@gmail.com"): la bitácora nunca guarda el correo completo. */
  public record NotificationDelivered(UUID expedienteId, UUID notificationId, String kind, String maskedRecipient) {}

  public record NotificationDeliveryFailed(UUID expedienteId, UUID notificationId, String kind, String maskedRecipient, String error) {}
}
