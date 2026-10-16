package com.c21genera.extraction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  /** Página del documento donde se leyó el dato (1 = primera); null si no se sabe. */
  private Integer sourcePage;

  /**
   * Control de Cambios #19: otros valores posibles del dato (JSON), p. ej. las fechas o notarios
   * de los antecedentes de una escritura, para que una persona elija el del instrumento actual.
   */
  private String alternativesJson;

  private UUID confirmedByUserId;

  @Column(length = 160)
  private String confirmedByName;

  private Instant confirmedAt;

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final int MAX_ALTERNATIVES = 6;

  protected ExtractedFieldObservation() {}

  public ExtractedFieldObservation(
      UUID expedienteId,
      UUID documentId,
      UUID documentVersionId,
      String fieldName,
      String detectedValue,
      FieldOrigin origin,
      Double confidence,
      Instant now,
      Integer sourcePage) {
    this(expedienteId, documentId, documentVersionId, fieldName, detectedValue, origin, confidence, now);
    this.sourcePage = sourcePage;
  }

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
    confirm(confirmedValue, now, null, null);
  }

  /** Queda quién lo confirmó y cuándo ("Confirmado por ..."). */
  public void confirm(String confirmedValue, Instant now, UUID byUserId, String byName) {
    this.confirmedValue = confirmedValue;
    this.origin = FieldOrigin.STAFF_EDITED;
    this.updatedAt = now;
    this.confirmedByUserId = byUserId;
    this.confirmedByName = byName;
    this.confirmedAt = now;
  }

  /**
   * Guarda las opciones solo si de verdad hay más de un valor distinto; el valor elegido por la IA
   * va primero si no venía en la lista.
   */
  public void recordAlternatives(String chosen, List<StructuredExtractionProvider.Alternative> alternatives) {
    if (alternatives == null || alternatives.isEmpty()) {
      this.alternativesJson = null;
      return;
    }
    Map<String, StructuredExtractionProvider.Alternative> distinct = new LinkedHashMap<>();
    boolean chosenListed = alternatives.stream().anyMatch(a -> same(a.value(), chosen));
    if (!chosenListed && chosen != null) {
      distinct.put(key(chosen), new StructuredExtractionProvider.Alternative(chosen, "Valor propuesto por la IA", sourcePage));
    }
    for (StructuredExtractionProvider.Alternative a : alternatives) {
      if (a.value() != null && !a.value().isBlank() && distinct.size() < MAX_ALTERNATIVES) {
        distinct.putIfAbsent(key(a.value()), a);
      }
    }
    if (distinct.size() < 2) {
      this.alternativesJson = null;
      return;
    }
    try {
      this.alternativesJson = JSON.writeValueAsString(new ArrayList<>(distinct.values()));
    } catch (Exception e) {
      this.alternativesJson = null;
    }
  }

  public List<StructuredExtractionProvider.Alternative> getAlternatives() {
    if (alternativesJson == null || alternativesJson.isBlank()) {
      return List.of();
    }
    try {
      return List.of(JSON.readValue(alternativesJson, StructuredExtractionProvider.Alternative[].class));
    } catch (Exception e) {
      return List.of();
    }
  }

  private static boolean same(String a, String b) {
    return a != null && b != null && key(a).equals(key(b));
  }

  private static String key(String value) {
    return value.strip().toUpperCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
  }

  public UUID getConfirmedByUserId() {
    return confirmedByUserId;
  }

  public String getConfirmedByName() {
    return confirmedByName;
  }

  public Instant getConfirmedAt() {
    return confirmedAt;
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

  public Integer getSourcePage() {
    return sourcePage;
  }
}
