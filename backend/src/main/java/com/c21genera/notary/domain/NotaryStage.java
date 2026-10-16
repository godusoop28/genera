package com.c21genera.notary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Control de Cambios #23: etapa del expediente frente a la notaría. Registrar la oferta aceptada
 * activa la etapa de cierre (documentos actualizados y envío de cierre).
 */
@Entity
@Table(name = "notary_stage")
public class NotaryStage {

  @Id
  private UUID expedienteId;

  private Instant offerAcceptedAt;

  @Column(length = 160)
  private String offerAcceptedByName;

  private String offerNote;

  protected NotaryStage() {}

  public NotaryStage(UUID expedienteId) {
    this.expedienteId = expedienteId;
  }

  public void registerOfferAccepted(Instant when, String byName, String note) {
    this.offerAcceptedAt = when;
    this.offerAcceptedByName = byName;
    this.offerNote = note;
  }

  public void clearOffer() {
    this.offerAcceptedAt = null;
    this.offerAcceptedByName = null;
    this.offerNote = null;
  }

  public boolean isClosingActive() {
    return offerAcceptedAt != null;
  }

  public UUID getExpedienteId() {
    return expedienteId;
  }

  public Instant getOfferAcceptedAt() {
    return offerAcceptedAt;
  }

  public String getOfferAcceptedByName() {
    return offerAcceptedByName;
  }

  public String getOfferNote() {
    return offerNote;
  }
}
