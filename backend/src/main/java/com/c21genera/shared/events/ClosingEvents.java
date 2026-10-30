package com.c21genera.shared.events;

import java.time.LocalDate;
import java.util.UUID;

/** Eventos de integración publicados por closing. */
public final class ClosingEvents {

  private ClosingEvents() {}

  /**
   * El seguimiento de cierre quedó concluido (cierre oficial de la transacción). surveys lo usa para la
   * encuesta NPS (CC#28); expedientes, para marcar la operación como "Cerrada" (CC#32). closedOn: fecha de
   * cierre (null en publicaciones anteriores al CC#32).
   */
  public record ClosingCompleted(UUID expedienteId, LocalDate closedOn) {}

  /** El cierre ya completado se volvió a abrir (CC#32: el expediente deja de mostrarse como "Cerrada"). */
  public record ClosingReopened(UUID expedienteId) {}
}
