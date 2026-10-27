package com.c21genera.leads;

/**
 * Pipeline de seguimiento de un lead, en orden (Control de Cambios #30). WON
 * ("Cierre") y LOST son etapas cerradas.
 */
public enum LeadStage {
  NEW,
  CONTACTED,
  QUALIFIED,
  APPOINTMENT,
  VISITED,
  PROPOSAL,
  NEGOTIATION,
  OFFER_ACCEPTED,
  WON,
  LOST;

  public boolean isOpen() {
    return this != WON && this != LOST;
  }

  /** Negociación en adelante: cuentan para el valor del pipeline y las alertas de leads "por enfriarse". */
  public boolean isAdvanced() {
    return this == NEGOTIATION || this == OFFER_ACCEPTED;
  }
}
