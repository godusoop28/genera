package com.c21genera.documents;

/**
 * Estado humano del documento (ver AGENTS §26). Separado a propósito del
 * estado de procesamiento técnico ({@link ProcessingStatus}): que un PDF se
 * haya generado no significa que esté aceptado.
 */
public enum DocumentStatus {
  PENDING,
  UPLOADED,
  READY_FOR_REVIEW,
  ACCEPTED,
  RETURNED,
  REJECTED,
  REPLACED
}
