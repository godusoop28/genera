package com.c21genera.audit.domain;

import com.c21genera.shared.events.Actor;
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
 * debe contener datos del cliente: solo IDs, tipos, nombres de evento y el
 * nombre/rol del usuario interno que actuó.
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

  @Column(length = 16)
  private String actorType;

  private String actorName;

  @Column(length = 32)
  private String actorRole;

  @Column(nullable = false, length = 2000)
  private String summary;

  protected AuditEvent() {}

  public AuditEvent(Instant occurredAt, String eventType, String aggregateType, UUID aggregateId, Actor actor, String summary) {
    this.id = UUID.randomUUID();
    this.occurredAt = occurredAt;
    this.eventType = eventType;
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    Actor effective = actor != null ? actor : Actor.system();
    this.actorType = effective.type().name();
    this.actorUserId = effective.userId();
    this.actorName = effective.name();
    this.actorRole = effective.role();
    this.summary = summary.length() > 2000 ? summary.substring(0, 2000) : summary;
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

  public String getActorType() {
    return actorType;
  }

  public String getActorName() {
    return actorName;
  }

  public String getActorRole() {
    return actorRole;
  }

  public String getSummary() {
    return summary;
  }
}
