package com.c21genera.leads;

/** Motivo por el que un lead quedó con su asesor. */
public enum AssignmentMethod {
  /** Es el asesor responsable de la propiedad en 21Online (vinculado a su usuario del CRM). */
  PROPERTY_OWNER,
  /** El cliente ya tenía un lead activo con ese asesor: se conserva al responsable. */
  CONTINUITY,
  /** Ronda entre los asesores en turno (equitativa o ponderada). */
  ROULETTE,
  /** Canalizado a un asesor de guardia porque el de la propiedad no estaba disponible. */
  GUARD,
  /** Lo asignó una persona desde el panel. */
  MANUAL
}
