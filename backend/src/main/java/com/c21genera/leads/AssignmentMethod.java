package com.c21genera.leads;

/** Cómo llegó el lead a su asesor. */
public enum AssignmentMethod {
  /** Es el asesor que tiene la propiedad en 21Online. */
  PROPERTY_OWNER,
  /** Ruleta ponderada entre los asesores que reciben leads. */
  ROULETTE,
  /** Lo asignó una persona desde el panel. */
  MANUAL
}
