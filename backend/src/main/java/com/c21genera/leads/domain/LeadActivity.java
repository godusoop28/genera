package com.c21genera.leads.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Línea de tiempo del lead: qué pasó, cuándo y quién (actor null = el sistema o el bot). */
@Entity
@Table(name = "lead_activity")
public class LeadActivity {

  public enum Kind {
    CREATED,
    ASSIGNED,
    REASSIGNED,
    STAGE,
    NOTE,
    BOT,
    NOTICE,
    FOLLOW_UP,
    /** Control de Cambios #30: actividad programada, realizada o cancelada. */
    TASK,
    PROPERTY_SHARED,
    EXPEDIENTE,
    /** Cambio de datos de la ficha (incluida la fuente, CC#29). */
    EDIT
  }

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID leadId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Kind kind;

  @Column(nullable = false)
  private String text;

  private UUID actorUserId;

  private String actorName;

  @Column(nullable = false)
  private Instant createdAt;

  protected LeadActivity() {}

  public LeadActivity(UUID leadId, Kind kind, String text, UUID actorUserId, String actorName, Instant createdAt) {
    this.id = UUID.randomUUID();
    this.leadId = leadId;
    this.kind = kind;
    this.text = text.length() > 4000 ? text.substring(0, 4000) : text;
    this.actorUserId = actorUserId;
    this.actorName = actorName;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getLeadId() {
    return leadId;
  }

  public Kind getKind() {
    return kind;
  }

  public String getText() {
    return text;
  }

  public UUID getActorUserId() {
    return actorUserId;
  }

  public String getActorName() {
    return actorName;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
