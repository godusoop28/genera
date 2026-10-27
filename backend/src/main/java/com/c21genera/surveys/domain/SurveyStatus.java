package com.c21genera.surveys.domain;

public enum SurveyStatus {
  /** Programada: se envía al llegar su hora (retraso configurado). */
  SCHEDULED,
  SENT,
  ANSWERED,
  /** Venció sin respuesta. */
  EXPIRED,
  /** No se envió por la regla anti-saturación o porque el punto de contacto está inactivo. */
  SKIPPED,
  /** No se pudo enviar (sin teléfono ni correo, o el canal falló). */
  FAILED;

  public boolean countsForFrequency() {
    return this == SCHEDULED || this == SENT || this == ANSWERED;
  }
}
