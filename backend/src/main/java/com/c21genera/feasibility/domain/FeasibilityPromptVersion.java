package com.c21genera.feasibility.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Una versión guardada de uno de los dos prompts maestros. Inmutable: editar crea la versión
 * siguiente, así siempre se puede volver a una anterior y saber con qué texto se hizo cada
 * factibilidad. La versión 0 es el texto original del cliente y no se guarda en la base.
 */
@Entity
@Table(name = "feasibility_prompt_version")
public class FeasibilityPromptVersion {

  public enum PromptKey {
    /** "Analista de Factibilidad 3.0": reporte de 27 secciones (Word). */
    ANALYST,
    /** "Presentación de factibilidad": las 12 láminas para el propietario (PowerPoint). */
    PRESENTATION,
    /** Chat de consultas adicionales sobre una factibilidad ya hecha. */
    CHAT
  }

  @Id
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "prompt_key", nullable = false, length = 32)
  private PromptKey key;

  @Column(nullable = false)
  private int versionNumber;

  @Column(nullable = false)
  private String content;

  @Column(length = 200)
  private String note;

  @Column(nullable = false)
  private Instant createdAt;

  private UUID createdByUserId;

  @Column(length = 160)
  private String createdByName;

  protected FeasibilityPromptVersion() {}

  public FeasibilityPromptVersion(PromptKey key, int versionNumber, String content, String note, Instant now, UUID byUserId, String byName) {
    this.id = UUID.randomUUID();
    this.key = key;
    this.versionNumber = versionNumber;
    this.content = content;
    this.note = note;
    this.createdAt = now;
    this.createdByUserId = byUserId;
    this.createdByName = byName;
  }

  public UUID getId() {
    return id;
  }

  public PromptKey getKey() {
    return key;
  }

  public int getVersionNumber() {
    return versionNumber;
  }

  public String getContent() {
    return content;
  }

  public String getNote() {
    return note;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public UUID getCreatedByUserId() {
    return createdByUserId;
  }

  public String getCreatedByName() {
    return createdByName;
  }
}
