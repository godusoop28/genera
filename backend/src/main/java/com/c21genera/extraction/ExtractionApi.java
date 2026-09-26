package com.c21genera.extraction;

import java.util.List;
import java.util.UUID;

/** API pública del módulo extraction, usada por compliance y contracts (ver AGENTS §7/§42). */
public interface ExtractionApi {

  boolean hasUnresolvedConflicts(UUID expedienteId);

  /** Descripción legible de cada diferencia entre documentos sin resolver. */
  List<String> unresolvedConflictDescriptions(UUID expedienteId);
}
