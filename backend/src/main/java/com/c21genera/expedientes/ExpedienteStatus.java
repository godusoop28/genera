package com.c21genera.expedientes;

/** API pública del módulo expedientes (ver AGENTS §9). Sin strings libres. El orden importa: refleja el avance. */
public enum ExpedienteStatus {
  DRAFT,
  WAITING_PRIVACY,
  WAITING_DOCUMENTS,
  DOCUMENTS_RECEIVED,
  UNDER_REVIEW,
  CORRECTIONS_REQUESTED,
  DOCUMENTS_APPROVED,
  RECEPTION_SIGNED,
  CONTRACT_PREPARATION,
  READY_FOR_SIGNATURE,
  CONTRACT_SIGNED,
  PROPERTY_ACCEPTED,
  PROPERTY_REJECTED,
  CLOSED
}
