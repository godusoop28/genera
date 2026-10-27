package com.c21genera.leads.domain;

import com.c21genera.leads.LeadStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Control de Cambios #30: cuándo entró y salió el lead de cada etapa (exitedAt null = etapa actual). */
@Entity
@Table(name = "lead_stage_history")
public class LeadStageHistory {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID leadId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private LeadStage stage;

  @Column(nullable = false)
  private Instant enteredAt;

  private Instant exitedAt;

  private String actorName;

  protected LeadStageHistory() {}

  public LeadStageHistory(UUID leadId, LeadStage stage, Instant enteredAt, Instant exitedAt, String actorName) {
    this.id = UUID.randomUUID();
    this.leadId = leadId;
    this.stage = stage;
    this.enteredAt = enteredAt;
    this.exitedAt = exitedAt;
    this.actorName = actorName;
  }

  public void exit(Instant at) {
    this.exitedAt = at;
  }

  public UUID getId() {
    return id;
  }

  public UUID getLeadId() {
    return leadId;
  }

  public LeadStage getStage() {
    return stage;
  }

  public Instant getEnteredAt() {
    return enteredAt;
  }

  public Instant getExitedAt() {
    return exitedAt;
  }

  public String getActorName() {
    return actorName;
  }
}
