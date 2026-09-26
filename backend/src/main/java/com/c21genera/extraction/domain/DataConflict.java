package com.c21genera.extraction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Conflicto detectado entre valores del mismo campo declarados/extraídos en
 * documentos distintos del mismo expediente (ver AGENTS §41). Nunca se
 * resuelve automáticamente: solo se registra para revisión humana.
 */
@Entity
@Table(name = "data_conflict")
public class DataConflict {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID expedienteId;

  /** Clave del tipo de hallazgo (ver DocumentConsistencyChecker.Finding#key). */
  @Column(nullable = false, length = 160)
  private String fieldName;

  @Column(nullable = false)
  private String description;

  @Column(nullable = false)
  private Instant detectedAt;

  @Column(nullable = false)
  private boolean resolved;

  private Instant resolvedAt;

  private UUID resolvedByUserId;

  private String resolutionNote;

  @Column(nullable = false)
  private boolean resolvedAutomatically;

  protected DataConflict() {}

  public DataConflict(UUID expedienteId, String fieldName, String description, Instant detectedAt) {
    this.id = UUID.randomUUID();
    this.expedienteId = expedienteId;
    this.fieldName = fieldName;
    this.description = description;
    this.detectedAt = detectedAt;
    this.resolved = false;
  }

  public void resolve(UUID resolvedByUserId, String resolutionNote, Instant now) {
    this.resolved = true;
    this.resolvedByUserId = resolvedByUserId;
    this.resolutionNote = resolutionNote;
    this.resolvedAt = now;
  }

  /** Los datos volvieron a coincidir (tras una corrección o un documento nuevo): se cierra sin intervención. */
  public void closeBecauseDataNowMatches(Instant now) {
    this.resolved = true;
    this.resolvedAutomatically = true;
    this.resolutionNote = "Los datos ya coinciden tras una corrección o la carga de un documento nuevo.";
    this.resolvedAt = now;
  }

  public void refreshDescription(String description) {
    this.description = description;
  }

  public boolean isResolvedAutomatically() {
    return resolvedAutomatically;
  }

  public UUID getId() {
    return id;
  }

  public UUID getExpedienteId() {
    return expedienteId;
  }

  public String getFieldName() {
    return fieldName;
  }

  public String getDescription() {
    return description;
  }

  public Instant getDetectedAt() {
    return detectedAt;
  }

  public boolean isResolved() {
    return resolved;
  }

  public Instant getResolvedAt() {
    return resolvedAt;
  }

  public UUID getResolvedByUserId() {
    return resolvedByUserId;
  }

  public String getResolutionNote() {
    return resolutionNote;
  }
}
