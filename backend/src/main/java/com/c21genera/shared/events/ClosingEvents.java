package com.c21genera.shared.events;

import java.util.UUID;

/** Eventos de integración publicados por closing. */
public final class ClosingEvents {

  private ClosingEvents() {}

  /** El seguimiento de cierre quedó concluido (cierre oficial de la transacción). surveys lo usa para la encuesta NPS (CC#28). */
  public record ClosingCompleted(UUID expedienteId) {}
}
