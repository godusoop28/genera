package com.c21genera.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Rastro de auditoría TÉCNICO (ver AGENTS §48-49): un registro por cada
 * evento de integración relevante, pensado para investigación/soporte, no
 * para mostrarse al usuario final (para eso existe {@link Activity}). Nunca
 * debe contener PII: solo IDs, tipos y nombres de evento.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

  @Id
  private UUID id;

  @Column(nullable = false)
  private Instant occurredAt;

  @Column(nullable = false, length = 96)
  private String eventType;

  @Column(nullable = false, length = 32)
  private String aggregateType;

  @Column(nullable = false)
  private UUID aggregateId;

  private UUID actorUserId;

  @Column(nullable = false, length = 512)
  private String summary;

  protected AuditEvent() {}

  public AuditEvent(
      Instant occurredAt, String eventType, String aggregateType, UUID aggregateId, UUID actorUserId, String summary) {
    this.id = UUID.randomUUID();
    this.occurredAt = occurredAt;
    this.eventType = eventType;
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.actorUserId = actorUserId;
    this.summary = summary;
  }

  public UUID getId() {
    return id;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public String getEventType() {
    return eventType;
  }

  public String getAggregateType() {
    return aggregateType;
  }

  public UUID getAggregateId() {
    return aggregateId;
  }

  public UUID getActorUserId() {
    return actorUserId;
  }

  public String getSummary() {
    return summary;
  }
}
