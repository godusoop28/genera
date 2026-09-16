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
  PUBLIC_LINK_GENERATE,
  DOCUMENT_REVIEW,
  DOCUMENT_ACCEPT,
  DOCUMENT_RETURN,
  DOCUMENT_REJECT,
  EXTRACTED_DATA_EDIT,
  CONTRACT_PREPARE,
  CONTRACT_GENERATE,
  DOCUMENT_EMAIL_SEND,
  RECEPTION_SIGN,
  PROPERTY_DECIDE,
  USER_MANAGE,
  ROLE_MANAGE
}
