package com.c21genera.identity.domain;

/** Para qué se generó un enlace de contraseña (Control de Cambios #25). */
public enum PasswordSetupPurpose {
  /** Alta: la persona define su primera contraseña. */
  INVITE,
  /** Restablecimiento iniciado por un administrador (la persona olvidó su contraseña). */
  RESET
}
