package com.c21genera.expedientes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** No se limita a un máximo por arquitectura, aunque el frontend hoy ofrezca hasta 3 (ver AGENTS §12). */
@Entity
@Table(name = "expediente_participant")
public class ExpedienteParticipant {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID expedienteId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private ParticipantRole role;

  @Column(nullable = false)
  private String fullName;

  @Column(nullable = false)
  private int ordinal;

  protected ExpedienteParticipant() {}

  public ExpedienteParticipant(UUID expedienteId, ParticipantRole role, String fullName, int ordinal) {
    this.id = UUID.randomUUID();
    this.expedienteId = expedienteId;
    this.role = role;
    this.fullName = fullName;
    this.ordinal = ordinal;
  }

  public UUID getId() {
    return id;
  }

  public UUID getExpedienteId() {
    return expedienteId;
  }

  public ParticipantRole getRole() {
    return role;
  }

  public String getFullName() {
    return fullName;
  }

  public int getOrdinal() {
    return ordinal;
  }
}
