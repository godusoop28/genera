package com.c21genera.expedientes;

import java.util.List;
import java.util.UUID;

/** Eventos de dominio publicados por expedientes (ver AGENTS §84). */
public final class ExpedienteEvents {

  private ExpedienteEvents() {}

  public record ExpedienteCreated(UUID expedienteId, String folio, UUID createdByUserId) {}

  /** Documents escucha esto para materializar sus propios Document a partir de la política calculada. */
  public record ExpedienteRequirementsChanged(UUID expedienteId, List<RequiredDocumentSpec> requirements) {}

  public record PropertyAccepted(UUID expedienteId, UUID decidedByUserId) {}

  public record PropertyRejected(UUID expedienteId, UUID decidedByUserId, String reason) {}
}
