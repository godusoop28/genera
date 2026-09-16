package com.c21genera.extraction;

import java.util.UUID;

/** API pública del módulo extraction, usada por compliance (ver AGENTS §7/§42). */
public interface ExtractionApi {

  boolean hasUnresolvedConflicts(UUID expedienteId);
}
