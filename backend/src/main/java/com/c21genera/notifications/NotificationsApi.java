package com.c21genera.notifications;

import java.util.UUID;

/**
 * API pública del módulo notifications: encola un correo (outbox, nunca
 * síncrono) y deja registro de su entrega o del error en notification_log.
 * Si no hay destinatario, el aviso queda registrado como omitido (así el
 * staff ve que el cliente no tiene correo registrado).
 */
public interface NotificationsApi {

  /** kind: nombre legible del aviso (p. ej. "Documento devuelto"), se muestra en el historial. */
  void notify(UUID expedienteId, String kind, String recipientEmail, String subject, String body);
}
