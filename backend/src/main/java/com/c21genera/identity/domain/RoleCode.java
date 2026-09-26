package com.c21genera.identity.domain;

/** Roles base (ver AGENTS §16). Los permisos reales por rol viven en la tabla role_permission. */
public enum RoleCode {
  ADMINISTRATOR,
  DIRECTOR,
  ADVISOR,
  DOCUMENT_REVIEWER
}
