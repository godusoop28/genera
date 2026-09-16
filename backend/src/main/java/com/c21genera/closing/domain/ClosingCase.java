package com.c21genera.closing.domain;

import com.c21genera.shared.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Seguimiento de cierre de un expediente ya con inmueble aceptado (ver
 * AGENTS §50): estado/notas/entrega de contrato genéricos, sin inventar
 * documentos legalmente requeridos que no estén especificados en el Módulo 1.
 */
@Entity
@Table(name = "closing_case")
public class ClosingCase extends AuditableEntity {

  @Id
  private UUID id;

  @Column(nullable = false, unique = true)
  private UUID expedienteId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ClosingStatus status;

  private boolean contractDelivered;

  private Instant contractDeliveredAt;

  protected ClosingCase() {}

  public ClosingCase(UUID expedienteId) {
    this.id = UUID.randomUUID();
    this.expedienteId = expedienteId;
    this.status = ClosingStatus.OPEN;
    this.contractDelivered = false;
  }

  public void changeStatus(ClosingStatus status) {
    this.status = status;
  }

  public void markContractDelivered(Instant when) {
    this.contractDelivered = true;
    this.contractDeliveredAt = when;
  }

  public UUID getId() {
    return id;
  }

  public UUID getExpedienteId() {
    return expedienteId;
  }

  public ClosingStatus getStatus() {
    return status;
  }

  public boolean isContractDelivered() {
    return contractDelivered;
  }

  public Instant getContractDeliveredAt() {
    return contractDeliveredAt;
  }
}
