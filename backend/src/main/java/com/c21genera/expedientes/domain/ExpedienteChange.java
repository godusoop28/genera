package com.c21genera.expedientes.domain;

import com.c21genera.shared.events.Actor;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Un renglón del historial de correcciones: quién cambió qué campo, de qué
 * valor a qué valor, cuándo y por qué. Inmutable: nunca se edita ni se
 * borra (a diferencia de audit_event, sí guarda los valores, porque su
 * propósito es poder reconstruir qué datos tenía el expediente antes).
 */
@Entity
@Table(name = "expediente_change")
public class ExpedienteChange {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID expedienteId;

  @Column(nullable = false)
  private Instant changedAt;

  @Column(nullable = false, length = 16)
  private String actorType;

  private UUID actorUserId;
  private String actorName;

  @Column(length = 32)
  private String actorRole;

  @Column(nullable = false, length = 48)
  private String section;

  @Column(nullable = false, length = 96)
  private String field;

  private String oldValue;
  private String newValue;
  private String reason;

  protected ExpedienteChange() {}

  public ExpedienteChange(
      UUID expedienteId, Instant changedAt, Actor actor, String section, String field, String oldValue, String newValue, String reason) {
    this.id = UUID.randomUUID();
    this.expedienteId = expedienteId;
    this.changedAt = changedAt;
    this.actorType = actor.type().name();
    this.actorUserId = actor.userId();
    this.actorName = actor.name();
    this.actorRole = actor.role();
    this.section = section;
    this.field = truncate(field, 96);
    this.oldValue = oldValue;
    this.newValue = newValue;
    this.reason = reason;
  }

  private static String truncate(String value, int max) {
    return value.length() <= max ? value : value.substring(0, max);
  }

  public UUID getId() {
    return id;
  }

  public UUID getExpedienteId() {
    return expedienteId;
  }

  public Instant getChangedAt() {
    return changedAt;
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

  public String getSection() {
    return section;
  }

  public String getField() {
    return field;
  }

  public String getOldValue() {
    return oldValue;
  }

  public String getNewValue() {
    return newValue;
  }

  public String getReason() {
    return reason;
  }
}
