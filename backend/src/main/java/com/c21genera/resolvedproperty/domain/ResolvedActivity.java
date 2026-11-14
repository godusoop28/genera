package com.c21genera.resolvedproperty.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Control de Cambios #42: bitácora del apartado (accesos, aprobación, incorporación y archivos). */
@Entity
@Table(name = "resolved_activity")
public class ResolvedActivity {

  @Id
  private UUID id;

  private UUID projectId;

  @Column(nullable = false)
  private String action;

  private String detail;
  private UUID actorUserId;
  private String actorName;

  @Column(nullable = false)
  private Instant occurredAt;

  protected ResolvedActivity() {}

  public ResolvedActivity(UUID projectId, String action, String detail, UUID actorUserId, String actorName, Instant occurredAt) {
    this.id = UUID.randomUUID();
    this.projectId = projectId;
    this.action = action;
    this.detail = detail;
    this.actorUserId = actorUserId;
    this.actorName = actorName;
    this.occurredAt = occurredAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
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

  public Instant getOccurredAt() {
    return occurredAt;
  }
}
