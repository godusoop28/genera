package com.c21genera.compliance.domain;

/**
 * Catálogo de verificaciones del "Control documental de cumplimiento" (ver
 * AGENTS §42-44). Deliberadamente NO se llama "Legal compliance guaranteed"
 * ni frases similares: es un control documental interno, no una garantía
 * legal frente al cliente.
 */
public enum ComplianceCheckCode {
  PRIVACY_NOTICE_PRESENTED,
  CONTRACT_TEMPLATE_CORRECT,
  MAIN_CONSENT_RECORDED,
  REQUIRED_DOCUMENTS_ACCEPTED,
  RECEPTION_SIGNED,
  NO_UNRESOLVED_DATA_CONFLICTS
}
