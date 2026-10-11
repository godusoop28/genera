package com.c21genera.leads.domain;

/** Estado de una acción externa ligada al lead (registro en 21Online, aviso al asesor). */
public enum SyncStatus {
  NOT_REQUIRED,
  PENDING,
  DONE,
  FAILED
}
