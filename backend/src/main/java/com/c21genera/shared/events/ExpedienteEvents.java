package com.c21genera.shared.events;

import com.c21genera.shared.domain.RequiredDocumentSpec;
import java.util.List;
import java.util.UUID;

/**
 * Eventos de integración publicados por expedientes (ver AGENTS §84). Viven
 * en shared (módulo abierto) y no en expedientes: varios módulos
 * (documents, privacy más adelante audit/notifications) necesitan
 * escucharlos, y expedientes a su vez escucha eventos de esos módulos;
 * mantenerlos dentro de expedientes crearía una dependencia cíclica entre
 * módulos (ver AGENTS §7).
 */
public final class ExpedienteEvents {

  private ExpedienteEvents() {}

  public record ExpedienteCreated(UUID expedienteId, String folio, UUID createdByUserId) {}

  /** Documents escucha esto para materializar sus propios Document a partir de la política calculada. */
  public record ExpedienteRequirementsChanged(UUID expedienteId, List<RequiredDocumentSpec> requirements) {}

  public record PropertyAccepted(UUID expedienteId, UUID decidedByUserId) {}

  public record PropertyRejected(UUID expedienteId, UUID decidedByUserId, String reason) {}
}
