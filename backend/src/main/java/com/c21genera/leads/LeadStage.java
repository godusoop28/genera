package com.c21genera.leads;

/** Embudo de seguimiento de un lead, en orden. WON y LOST son etapas cerradas. */
public enum LeadStage {
  NEW,
  CONTACTED,
  APPOINTMENT,
  VISITED,
  NEGOTIATION,
  WON,
  LOST;

  public boolean isOpen() {
    return this != WON && this != LOST;
  }
}
