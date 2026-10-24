package com.c21genera.feasibility.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Un mensaje del chat de consultas sobre una factibilidad (pregunta del asesor o respuesta de la IA). */
@Entity
@Table(name = "feasibility_chat_message")
public class FeasibilityChatMessage {

  public enum Role {
    USER,
    ASSISTANT
  }

  public enum Status {
    PENDING,
    DONE,
    FAILED
  }

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID reportId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Role role;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status;

  private String content;

  private String sourcesJson;

  @Column(length = 120)
  private String aiResponseId;

  private String error;

  private UUID authorUserId;

  @Column(length = 160)
  private String authorName;

  @Column(nullable = false)
  private Instant createdAt;

  private Instant completedAt;

  protected FeasibilityChatMessage() {}

  public static FeasibilityChatMessage question(UUID reportId, String text, UUID userId, String userName, Instant now) {
    FeasibilityChatMessage m = new FeasibilityChatMessage(reportId, Role.USER, now);
    m.status = Status.DONE;
    m.content = text;
    m.authorUserId = userId;
    m.authorName = userName;
    m.completedAt = now;
    return m;
  }

  public static FeasibilityChatMessage pendingAnswer(UUID reportId, String responseId, Instant now) {
    FeasibilityChatMessage m = new FeasibilityChatMessage(reportId, Role.ASSISTANT, now);
    m.status = Status.PENDING;
    m.aiResponseId = responseId;
    return m;
  }

  private FeasibilityChatMessage(UUID reportId, Role role, Instant now) {
    this.id = UUID.randomUUID();
    this.reportId = reportId;
    this.role = role;
    this.createdAt = now;
  }

  public void answered(String text, String sourcesJson, Instant now) {
    this.status = Status.DONE;
    this.content = text;
    this.sourcesJson = sourcesJson;
    this.completedAt = now;
  }

  public void failed(String reason, Instant now) {
    this.status = Status.FAILED;
    this.error = reason != null && reason.length() > 1000 ? reason.substring(0, 1000) : reason;
    this.completedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getReportId() {
    return reportId;
  }

  public Role getRole() {
    return role;
  }

  public Status getStatus() {
    return status;
  }

  public String getContent() {
    return content;
  }

  public String getSourcesJson() {
    return sourcesJson;
  }

  public String getAiResponseId() {
    return aiResponseId;
  }

  public String getError() {
    return error;
  }

  public String getAuthorName() {
    return authorName;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
