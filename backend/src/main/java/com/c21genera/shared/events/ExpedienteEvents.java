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

  public record ExpedienteCreated(UUID expedienteId, String folio, UUID createdByUserId, Actor actor) {}

  /** Documents escucha esto para materializar sus propios Document a partir de la política calculada. */
  public record ExpedienteRequirementsChanged(UUID expedienteId, List<RequiredDocumentSpec> requirements) {}

  /**
   * Se corrigió información del expediente (datos principales,
   * participantes, datos legales o datos del cliente). contracts escucha
   * esto para invalidar los contratos todavía no firmados: cualquier
   * corrección relevante obliga a generar una nueva versión.
   */
  public record ExpedienteDataCorrected(
      UUID expedienteId, Actor actor, String section, List<String> changedFields, String reason, boolean contractRelevant) {}

  public record PropertyAccepted(UUID expedienteId, UUID decidedByUserId, Actor actor) {}

  public record PropertyRejected(UUID expedienteId, UUID decidedByUserId, String reason, Actor actor) {}
}
