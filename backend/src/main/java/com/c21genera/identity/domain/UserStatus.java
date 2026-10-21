package com.c21genera.identity.domain;

/**
 * Control de Cambios #25: ciclo de vida de una cuenta.
 *
 * <ul>
 *   <li>PENDING: recién creada; la persona aún no define su contraseña con su enlace.
 *   <li>ACTIVE: puede entrar.
 *   <li>INACTIVE: desactivada temporalmente (reversible).
 *   <li>OFFBOARDED: dada de baja (definitivo). Nunca se borra: conserva su historial.
 * </ul>
 */
public enum UserStatus {
  PENDING,
  ACTIVE,
  INACTIVE,
  OFFBOARDED
}
