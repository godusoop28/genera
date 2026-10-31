package com.c21genera.operations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** CC#32: historial de la operación (estados, revisiones, observaciones, autorizaciones, reaperturas). */
@Entity
@Table(name = "closing_operation_event")
public class OperationEvent {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID operationId;

  private UUID requirementId;

  @Column(nullable = false, length = 40)
  private String type;

  private String detail;
  private UUID actorUserId;
  private String actorName;

  @Column(nullable = false)
  private Instant occurredAt;

  protected OperationEvent() {}

  public OperationEvent(UUID operationId, UUID requirementId, String type, String detail, UUID actorUserId, String actorName, Instant at) {
    this.id = UUID.randomUUID();
    this.operationId = operationId;
    this.requirementId = requirementId;
    this.type = type;
    this.detail = detail;
    this.actorUserId = actorUserId;
    this.actorName = actorName;
    this.occurredAt = at;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOperationId() {
    return operationId;
  }

  public UUID getRequirementId() {
    return requirementId;
  }

  public String getType() {
    return type;
  }

  public String getDetail() {
    return detail;
  }

  public UUID getActorUserId() {
    return actorUserId;
  }

  public String getActorName() {
    return actorName;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }
}
