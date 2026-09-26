package com.c21genera.shared.events;

import java.util.UUID;

/** Eventos de integración publicados por contracts. */
public final class ContractEvents {

  private ContractEvents() {}

  /** draft=true: borrador INCOMPLETO, bloqueado para firma. */
  public record ContractGenerated(UUID expedienteId, UUID contractId, int versionNumber, boolean draft, Actor actor) {}

  /** La versión dejó de ser válida (se corrigieron datos o se generó una versión nueva); sus firmas pendientes se anulan. */
  public record ContractSuperseded(UUID expedienteId, UUID contractId, int versionNumber, String reason, Actor actor) {}

  /** Una de las partes firmó. method: ELECTRONIC_SIMPLE o AUTOGRAPH_SCAN. */
  public record ContractSignatureRecorded(
      UUID expedienteId, UUID contractId, int versionNumber, String signerName, String signerCapacity, String method, Actor actor) {}

  /** Todas las partes firmaron: expedientes avanza a CONTRACT_SIGNED y closing abre el seguimiento posterior a la firma. */
  public record ContractFullySigned(UUID expedienteId, UUID contractId, int versionNumber, String documentSha256) {}

  public record ContractDelivered(UUID expedienteId, UUID contractId, int versionNumber, String method, Actor actor) {}
}
