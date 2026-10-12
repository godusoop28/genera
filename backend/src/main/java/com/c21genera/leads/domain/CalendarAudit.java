package com.c21genera.leads.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Quién creó, modificó o eliminó un turno, una ausencia o la política de asignación. */
@Entity
@Table(name = "crm_calendar_audit")
public class CalendarAudit {

  @Id
  private UUID id;

  @Column(nullable = false)
  private String entity;

  private UUID entityId;

  private UUID advisorUserId;

  @Column(nullable = false)
  private String action;

  @Column(nullable = false)
  private String detail;

  private UUID actorUserId;

  private String actorName;

  @Column(nullable = false)
  private Instant createdAt;

  protected CalendarAudit() {}

  public CalendarAudit(String entity, UUID entityId, UUID advisorUserId, String action, String detail, UUID actorUserId, String actorName, Instant createdAt) {
    this.id = UUID.randomUUID();
    this.entity = entity;
    this.entityId = entityId;
    this.advisorUserId = advisorUserId;
    this.action = action;
    this.detail = detail.length() > 2000 ? detail.substring(0, 2000) : detail;
    this.actorUserId = actorUserId;
    this.actorName = actorName;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getEntity() {
    return entity;
  }

  public UUID getEntityId() {
    return entityId;
  }

  public UUID getAdvisorUserId() {
    return advisorUserId;
  }

  public String getAction() {
    return action;
  }

  public String getDetail() {
    return detail;
  }

  public String getActorName() {
    return actorName;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
