package com.c21genera.shared.events;

import java.util.UUID;

/** Eventos de integración publicados por el módulo leads. */
public final class LeadEvents {

  private LeadEvents() {}

  /**
   * Un lead quedó asignado (nuevo, reasignado o por canalización del bot).
   * advisorWhatsapp viene del perfil de ruteo del asesor (puede ser null);
   * notice es el texto del aviso ya armado (sin enlaces al panel).
   */
  public record LeadAssigned(UUID leadId, UUID advisorUserId, String advisorName, String advisorWhatsapp, String notice) {}
}
