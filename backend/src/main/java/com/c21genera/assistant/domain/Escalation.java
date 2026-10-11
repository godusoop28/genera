package com.c21genera.assistant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Pendiente para el equipo: pidió una persona, asunto de gerencia, seguimiento o falla del bot. */
@Entity
@Table(name = "wa_escalation")
public class Escalation {

  public enum Type {
    HUMAN,
    MANAGEMENT,
    FOLLOW_UP,
    NO_ADVISOR,
    PROCESSING_ERROR
  }

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID conversationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Type type;

  @Column(nullable = false)
  private String reason;

  @Column(nullable = false)
  private String status = "OPEN";

  @Column(nullable = false)
  private Instant createdAt;

  private Instant resolvedAt;

  protected Escalation() {}

  public Escalation(UUID conversationId, Type type, String reason, Instant createdAt) {
    this.id = UUID.randomUUID();
    this.conversationId = conversationId;
    this.type = type;
    this.reason = reason.length() > 1000 ? reason.substring(0, 1000) : reason;
    this.createdAt = createdAt;
  }

  public void resolve(Instant at) {
    this.status = "RESOLVED";
    this.resolvedAt = at;
  }

  public UUID getId() {
    return id;
  }

  public UUID getConversationId() {
    return conversationId;
  }

  public Type getType() {
    return type;
  }

  public String getReason() {
    return reason;
  }

  public String getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
