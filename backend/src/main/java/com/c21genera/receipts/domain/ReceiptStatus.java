package com.c21genera.receipts.domain;

/** Control de Cambios #31: un borrador nunca se presenta como firmado. */
public enum ReceiptStatus {
  DRAFT,
  PENDING_SIGNATURE,
  SIGNED,
  SENT
}
