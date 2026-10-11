package com.c21genera.assistant.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Un mensaje de la conversación. Los salientes pasan por QUEUED → SENDING → SENT | FAILED. */
@Entity
@Table(name = "wa_message")
public class ConversationMessage {

  public enum Role {
    CLIENT,
    BOT,
    TEAM,
    SYSTEM
  }

  public enum Status {
    RECEIVED,
    QUEUED,
    SENDING,
    SENT,
    FAILED,
    /** Del simulador o con WhatsApp sin configurar: se guardó pero no se envió. */
    NOT_SENT
  }

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID conversationId;

  @Column(nullable = false)
  private long seq;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role;

  @Column(nullable = false)
  private String text;

  private String externalId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Status status;

  private String error;

  @Column(nullable = false)
  private int attempts;

  @Column(nullable = false)
  private Instant createdAt;

  private Instant sentAt;

  protected ConversationMessage() {}

  public ConversationMessage(UUID conversationId, long seq, Role role, String text, String externalId, Status status, Instant createdAt) {
    this.id = UUID.randomUUID();
    this.conversationId = conversationId;
    this.seq = seq;
    this.role = role;
    this.text = text.length() > 4096 ? text.substring(0, 4096) : text;
    this.externalId = externalId;
    this.status = status;
    this.createdAt = createdAt;
  }

  public void sending() {
    this.status = Status.SENDING;
    this.attempts++;
  }

  public void sent(String externalId, Instant at) {
    this.status = Status.SENT;
    this.externalId = externalId;
    this.sentAt = at;
    this.error = null;
  }

  public void failed(String error, boolean finalFailure) {
    this.status = finalFailure ? Status.FAILED : Status.QUEUED;
    this.error = error == null ? null : (error.length() > 500 ? error.substring(0, 500) : error);
  }

  public void notSent(String reason) {
    this.status = Status.NOT_SENT;
    this.error = reason;
  }

  public UUID getId() {
    return id;
  }

  public UUID getConversationId() {
    return conversationId;
  }

  public long getSeq() {
    return seq;
  }

  public Role getRole() {
    return role;
  }

  public String getText() {
    return text;
  }

  public String getExternalId() {
    return externalId;
  }

  public Status getStatus() {
    return status;
  }

  public String getError() {
    return error;
  }

  public int getAttempts() {
    return attempts;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getSentAt() {
    return sentAt;
  }
}
