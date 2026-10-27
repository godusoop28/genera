package com.c21genera.identity.domain;

/**
 * Catálogo de permisos del sistema (ver AGENTS §16). Estos nombres deben
 * coincidir literalmente con las cadenas usadas en las anotaciones
 * {@code @PreAuthorize("hasAuthority('...')")} de los controladores: las
 * anotaciones necesitan constantes de compilación, así que no pueden
 * referenciar directamente a este enum. Este catálogo es la fuente de
 * verdad para el seeding y para el endpoint GET /permissions.
 */
public enum PermissionCode {
  EXPEDIENT_CREATE,
  EXPEDIENT_VIEW_OWN,
  EXPEDIENT_VIEW_ALL,
  EXPEDIENT_EDIT,
  EXPEDIENT_DUPLICATE_AUTHORIZE,
  EXPEDIENT_ADVISOR_ASSIGN,
  EXPEDIENT_ARCHIVE,
  EXPEDIENT_KEY_ASSIGN,
  PUBLIC_LINK_GENERATE,
  DOCUMENT_REVIEW,
  DOCUMENT_UPLOAD,
  DOCUMENT_ACCEPT,
  DOCUMENT_RETURN,
  DOCUMENT_REJECT,
  DOCUMENT_QUALITY_OVERRIDE,
  DOCUMENT_MARK_NOT_APPLICABLE,
  EXTRACTED_DATA_EDIT,
  CONTRACT_PREPARE,
  CONTRACT_GENERATE,
  CONTRACT_SIGN,
  DOCUMENT_EMAIL_SEND,
  RECEPTION_SIGN,
  PROPERTY_DECIDE,
  USER_MANAGE,
  ROLE_MANAGE,
  AUDIT_VIEW,
  LEAD_VIEW_ALL,
  LEAD_VIEW_OWN,
  LEAD_MANAGE,
  LEAD_ASSIGN,
  ADVISOR_ROUTING_MANAGE,
  CONVERSATION_VIEW,
  CONVERSATION_MANAGE,
  PROPERTY_VIEW,
  PROPERTY_MANAGE,
  PROPERTY_CATALOG_MANAGE,
  LEAD_SOURCE_MANAGE,
  NPS_VIEW,
  NPS_MANAGE,
  RECEIPT_GENERATE,
  RECEIPT_SIGN,
  RECEIPT_MANAGE
}
