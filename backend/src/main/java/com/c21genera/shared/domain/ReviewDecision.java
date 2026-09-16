package com.c21genera.shared.domain;

/**
 * Decisión de revisión documental. Vive en shared (no en documents) porque
 * expedientes también la referencia al reaccionar al evento
 * DocumentReviewed; mantenerla en un módulo concreto crearía una
 * dependencia cíclica entre documents y expedientes (ver AGENTS §7).
 */
public enum ReviewDecision {
  ACCEPTED,
  RETURNED,
  REJECTED
}
