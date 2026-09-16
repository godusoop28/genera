package com.c21genera.extraction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Observación de un campo extraído de un documento (ver AGENTS §38-40). El
 * valor "detectado" (propuesto por IA u otro origen) nunca sobrescribe
 * directamente el dato canónico del expediente: un humano debe confirmarlo
 * explícitamente vía {@link #confirm}.
 */
@Entity
@Table(name = "extracted_field_observation")
public class ExtractedFieldObservation {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID expedienteId;

  @Column(nullable = false)
  private UUID documentId;

  @Column(nullable = false)
  private UUID documentVersionId;

  @Column(nullable = false, length = 64)
  private String fieldName;

  private String detectedValue;

  private String confirmedValue;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private FieldOrigin origin;

  private Double confidence;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  protected ExtractedFieldObservation() {}

  public ExtractedFieldObservation(
      UUID expedienteId,
      UUID documentId,
      UUID documentVersionId,
      String fieldName,
      String detectedValue,
      FieldOrigin origin,
      Double confidence,
      Instant now) {
    this.id = UUID.randomUUID();
    this.expedienteId = expedienteId;
    this.documentId = documentId;
    this.documentVersionId = documentVersionId;
    this.fieldName = fieldName;
    this.detectedValue = detectedValue;
    this.origin = origin;
    this.confidence = confidence;
    this.createdAt = now;
    this.updatedAt = now;
  }

  /** Un humano (staff) confirma o corrige el valor detectado (ver AGENTS §40). */
  public void confirm(String confirmedValue, Instant now) {
    this.confirmedValue = confirmedValue;
    this.origin = FieldOrigin.STAFF_EDITED;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getExpedienteId() {
    return expedienteId;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public UUID getDocumentVersionId() {
    return documentVersionId;
  }

  public String getFieldName() {
    return fieldName;
  }

  public String getDetectedValue() {
    return detectedValue;
  }

  public String getConfirmedValue() {
    return confirmedValue;
  }

  public FieldOrigin getOrigin() {
    return origin;
  }

  public Double getConfidence() {
    return confidence;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
