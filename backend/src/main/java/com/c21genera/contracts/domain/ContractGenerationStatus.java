package com.c21genera.contracts.domain;

/**
 * DRAFT_INCOMPLETE: borrador con datos pendientes, nunca se puede firmar.
 * GENERATED: completo y enviado a firma (ninguna parte ha firmado).
 * PARTIALLY_SIGNED: al menos una parte firmó.
 * SIGNED: firmado por todas las partes (con evidencia de cada firma).
 * DELIVERED: se registró la entrega de un tanto firmado al cliente.
 * SUPERSEDED: sin efecto (se corrigieron datos o se generó otra versión).
 */
public enum ContractGenerationStatus {
  DRAFT_INCOMPLETE,
  GENERATED,
  PARTIALLY_SIGNED,
  SIGNED,
  DELIVERED,
  SUPERSEDED
}
