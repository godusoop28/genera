package com.c21genera.assistant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Bitácora de cada turno de la IA (modelo, tokens, herramientas usadas). Sin textos del cliente. */
@Entity
@Table(name = "wa_turn")
public class ConversationTurn {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID conversationId;

  @Column(nullable = false)
  private long fromSeq;

  @Column(nullable = false)
  private long toSeq;

  @Column(nullable = false)
  private String status;

  private String model;

  private Integer inputTokens;

  private Integer outputTokens;

  private String toolTrace;

  private String error;

  private Integer durationMs;

  @Column(nullable = false)
  private Instant createdAt;

  protected ConversationTurn() {}

  public ConversationTurn(
      UUID conversationId, long fromSeq, long toSeq, String status, String model, Integer inputTokens, Integer outputTokens, String toolTrace, String error, Integer durationMs, Instant createdAt) {
    this.id = UUID.randomUUID();
    this.conversationId = conversationId;
    this.fromSeq = fromSeq;
    this.toSeq = toSeq;
    this.status = status;
    this.model = model;
    this.inputTokens = inputTokens;
    this.outputTokens = outputTokens;
    this.toolTrace = toolTrace;
    this.error = error == null ? null : (error.length() > 1000 ? error.substring(0, 1000) : error);
    this.durationMs = durationMs;
    this.createdAt = createdAt;
  }

  public String getStatus() {
    return status;
  }

  public String getToolTrace() {
    return toolTrace;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
