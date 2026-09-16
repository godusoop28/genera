package com.c21genera.extraction.domain;

/**
 * Origen de un valor de campo extraído (ver AGENTS §38-40): la IA nunca
 * sobrescribe directamente el dato canónico, solo propone un valor
 * "detectado" separado del "confirmado".
 */
public enum FieldOrigin {
  AI_EXTRACTED,
  CLIENT_DECLARED,
  STAFF_EDITED,
  CALCULATED,
  NOT_APPLICABLE
}
