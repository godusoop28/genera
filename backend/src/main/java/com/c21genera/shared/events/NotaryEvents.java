package com.c21genera.shared.events;

import java.util.UUID;

/** Eventos del módulo notary (Control de Cambios #23); audit los registra en la bitácora. */
public final class NotaryEvents {

  private NotaryEvents() {}

  /** accepted=false: se quitó el registro de la oferta aceptada. */
  public record OfferAcceptanceChanged(UUID expedienteId, boolean accepted, String note, Actor actor) {}

  /** kind: INITIAL o CLOSING; summary: documentos y versión enviados ("Predial V2, INE V1"). */
  public record NotaryPackageSent(UUID expedienteId, UUID submissionId, String kind, String summary, String recipientEmail, Actor actor) {}

  public record ClosingDocumentChecked(UUID expedienteId, UUID documentId, String documentName, boolean valid, String note, Actor actor) {}
}
