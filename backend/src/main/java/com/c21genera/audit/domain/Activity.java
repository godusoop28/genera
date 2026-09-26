package com.c21genera.audit.domain;

import com.c21genera.shared.events.Actor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Línea de tiempo AMIGABLE de un expediente, para mostrarse en la UI interna
 * (ver AGENTS §48-49). Deliberadamente distinta de {@link AuditEvent}: aquí
 * el mensaje ya está redactado para un humano, no para depuración técnica.
 * Cada renglón dice quién (cliente, usuario interno con su rol, o sistema),
 * qué acción, sobre qué documento y cuándo.
 */
@Entity
@Table(name = "activity")
public class Activity {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID expedienteId;

  @Column(nullable = false)
  private Instant occurredAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private ActivityCategory category;

  @Column(nullable = false, length = 2000)
  private String message;

  @Column(length = 16)
  private String actorType;

  private UUID actorUserId;
  private String actorName;

  @Column(length = 32)
  private String actorRole;

  @Column(length = 64)
  private String action;

  private UUID documentId;
  private String documentLabel;

  protected Activity() {}

  public Activity(
      UUID expedienteId,
      Instant occurredAt,
      ActivityCategory category,
      String action,
      Actor actor,
      UUID documentId,
      String documentLabel,
      String message) {
    this.id = UUID.randomUUID();
    this.expedienteId = expedienteId;
    this.occurredAt = occurredAt;
    this.category = category;
    this.action = action;
    Actor effective = actor != null ? actor : Actor.system();
    this.actorType = effective.type().name();
    this.actorUserId = effective.userId();
    this.actorName = effective.name();
    this.actorRole = effective.role();
    this.documentId = documentId;
    this.documentLabel = documentLabel;
    this.message = message.length() > 2000 ? message.substring(0, 2000) : message;
  }

  public UUID getId() {
    return id;
  }

  public UUID getExpedienteId() {
    return expedienteId;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public ActivityCategory getCategory() {
    return category;
  }

  public String getMessage() {
    return message;
  }

  public String getActorType() {
    return actorType;
  }

  public UUID getActorUserId() {
    return actorUserId;
  }

  public String getActorName() {
    return actorName;
  }

  public String getActorRole() {
    return actorRole;
  }

  public String getAction() {
    return action;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public String getDocumentLabel() {
    return documentLabel;
  }
}
