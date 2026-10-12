package com.c21genera.leads.domain;

import com.c21genera.leads.AssignmentMethod;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Por qué se asignó (o no) un lead: fecha, asesor, motivo, regla, turno y espera. */
@Entity
@Table(name = "lead_assignment_log")
public class AssignmentLog {

  public enum Outcome {
    ASSIGNED,
    PENDING,
    KEPT,
    CONFLICT,
    REASSIGNED
  }

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID leadId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Outcome outcome;

  private UUID advisorUserId;

  @Enumerated(EnumType.STRING)
  private AssignmentMethod reason;

  @Column(nullable = false)
  private String rule;

  private UUID shiftId;

  private String roundMode;

  private String detail;

  private UUID actorUserId;

  private String actorName;

  @Column(nullable = false)
  private Instant createdAt;

  protected AssignmentLog() {}

  public AssignmentLog(
      UUID leadId, Outcome outcome, UUID advisorUserId, AssignmentMethod reason, String rule, UUID shiftId, String roundMode, String detail, UUID actorUserId, String actorName, Instant createdAt) {
    this.id = UUID.randomUUID();
    this.leadId = leadId;
    this.outcome = outcome;
    this.advisorUserId = advisorUserId;
    this.reason = reason;
    this.rule = rule;
    this.shiftId = shiftId;
    this.roundMode = roundMode;
    this.detail = detail;
    this.actorUserId = actorUserId;
    this.actorName = actorName;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public Outcome getOutcome() {
    return outcome;
  }

  public UUID getAdvisorUserId() {
    return advisorUserId;
  }

  public AssignmentMethod getReason() {
    return reason;
  }

  public String getRule() {
    return rule;
  }

  public UUID getShiftId() {
    return shiftId;
  }

  public String getRoundMode() {
    return roundMode;
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
