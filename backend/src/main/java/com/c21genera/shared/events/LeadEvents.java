package com.c21genera.shared.events;

import java.util.UUID;

/** Eventos de integración publicados por el módulo leads. */
public final class LeadEvents {

  private LeadEvents() {}

  /**
   * Hay que mandarle al asesor la ficha del lead: quedó asignado (nuevo,
   * reasignado, manual o al salir de pendiente), se reenvió desde el panel o
   * su cliente volvió a escribir con algo nuevo. advisorWhatsapp viene del
   * perfil de ruteo (puede ser null). notice: ficha completa para texto libre;
   * compactNotice: versión corta que cabe en el parámetro de una plantilla.
   */
  public record LeadAssigned(UUID leadId, UUID advisorUserId, String advisorName, String advisorWhatsapp, String notice, String compactNotice) {}
}
