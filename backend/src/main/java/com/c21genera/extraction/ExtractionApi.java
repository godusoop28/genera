package com.c21genera.extraction;

import java.util.List;
import java.util.UUID;

/** API pública del módulo extraction, usada por compliance y contracts (ver AGENTS §7/§42). */
public interface ExtractionApi {

  /**
   * ¿Hay alguna inconsistencia CRÍTICA sin resolver (evidencia fuerte de otra
   * persona u otro inmueble)? Solo esas bloquean el envío del contrato a firma;
   * las de severidad INFO o WARNING son ayuda para el revisor.
   */
  boolean hasUnresolvedConflicts(UUID expedienteId);

  /** Descripción legible de cada inconsistencia CRÍTICA sin resolver. */
  List<String> unresolvedConflictDescriptions(UUID expedienteId);

  /** Todas las posibles inconsistencias sin resolver, con su severidad (INFO, WARNING, CRITICAL). */
  List<ConflictView> unresolvedConflicts(UUID expedienteId);

  record ConflictView(String severity, String description) {}
}
