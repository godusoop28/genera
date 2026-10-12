package com.c21genera.leads;

/** Estado de la asignación del lead, separado de su etapa comercial. */
public enum AssignmentStatus {
  /** Tiene asesor que lo atiende. */
  ASSIGNED,
  /** Reservado para el asesor de la propiedad: espera su próximo turno. */
  PENDING_OWNER,
  /** "Pendiente por horario": no había asesores disponibles; se asigna solo cuando empiece un turno elegible. */
  PENDING_SCHEDULE,
  /** Sin asesor y sin proceso automático (alta manual sin asignar o registros anteriores al calendario). */
  UNASSIGNED;

  public boolean isPending() {
    return this == PENDING_OWNER || this == PENDING_SCHEDULE;
  }
}
