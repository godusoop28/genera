package com.c21genera.notifications;

import java.util.Set;
import java.util.UUID;

/**
 * API pública para avisarle al personal dentro del sistema (campana del
 * encabezado) y, si se pide, también por correo: recordatorios de
 * actividades de leads (CC#30) y alertas de detractores NPS (CC#28).
 */
public interface StaffAlerts {

  /**
   * @param link       ruta del panel a la que lleva el aviso (p. ej. "/leads/…"); puede ser null
   * @param dedupeKey  si ya existe un aviso con esta llave para el usuario, no se repite (null = siempre)
   * @return false si no se creó porque ya existía
   */
  boolean notifyUser(UUID userId, String kind, String title, String body, String link, String dedupeKey, boolean alsoEmail);

  /** A todo el personal activo con alguno de esos roles (p. ej. ADMINISTRATOR y DIRECTOR). */
  void notifyRoles(Set<String> roleCodes, String kind, String title, String body, String link, String dedupeKey, boolean alsoEmail);
}
