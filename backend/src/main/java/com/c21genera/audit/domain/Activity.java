package com.c21genera.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Línea de tiempo AMIGABLE de un expediente, para mostrarse en la UI interna
 * (ver AGENTS §48-49). Deliberadamente distinta de {@link AuditEvent}: aquí
 * el mensaje ya está redactado para un humano, no para depuración técnica.
 */
@Entity
@Table(name = "activity")
public class Activity {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID expedienteId;

  @Column(nullable = false)
  private Instant occurredAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private ActivityCategory category;

  @Column(nullable = false, length = 512)
  private String message;

  protected Activity() {}

  public Activity(UUID expedienteId, Instant occurredAt, ActivityCategory category, String message) {
    this.id = UUID.randomUUID();
    this.expedienteId = expedienteId;
    this.occurredAt = occurredAt;
    this.category = category;
    this.message = message;
  }

  public UUID getId() {
    return id;
  }

  public UUID getExpedienteId() {
    return expedienteId;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public ActivityCategory getCategory() {
    return category;
  }

  public String getMessage() {
    return message;
  }
}
