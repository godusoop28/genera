package com.c21genera.leads.domain;

import com.c21genera.shared.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Vacaciones, permiso, descanso o indisponibilidad. Prevalece sobre cualquier turno. */
@Entity
@Table(name = "crm_absence")
public class Absence extends AuditableEntity {

  public enum Kind {
    VACATION,
    PERMISSION,
    REST,
    UNAVAILABLE
  }

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID advisorUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Kind kind;

  @Column(nullable = false)
  private Instant startsAt;

  @Column(nullable = false)
  private Instant endsAt;

  private String notes;

  private String createdByName;

  private String updatedByName;

  private Instant deletedAt;

  private String deletedByName;

  protected Absence() {}

  public Absence(UUID advisorUserId, String createdByName) {
    this.id = UUID.randomUUID();
    this.advisorUserId = advisorUserId;
    this.createdByName = createdByName;
  }

  public void define(UUID advisorUserId, Kind kind, Instant startsAt, Instant endsAt, String notes) {
    this.advisorUserId = advisorUserId;
    this.kind = kind;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.notes = notes == null || notes.isBlank() ? null : notes.trim();
  }

  public void updatedBy(String name) {
    this.updatedByName = name;
  }

  public void delete(Instant at, String byName) {
    this.deletedAt = at;
    this.deletedByName = byName;
  }

  public Schedule.Span toSpan() {
    return new Schedule.Span(advisorUserId, startsAt, endsAt);
  }

  public UUID getId() {
    return id;
  }

  public UUID getAdvisorUserId() {
    return advisorUserId;
  }

  public Kind getKind() {
    return kind;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public String getNotes() {
    return notes;
  }

  public String getCreatedByName() {
    return createdByName;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }
}
