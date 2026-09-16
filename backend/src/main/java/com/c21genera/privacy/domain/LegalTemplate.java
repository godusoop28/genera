package com.c21genera.privacy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Versión concreta de un documento legal (aviso, contrato, carta de
 * derechos). Necesitamos saber exactamente qué versión aceptó el cliente
 * (ver AGENTS §64).
 */
@Entity
@Table(name = "legal_template")
public class LegalTemplate {

  @Id
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private LegalTemplateType type;

  @Column(nullable = false)
  private int version;

  @Column(nullable = false)
  private Instant effectiveFrom;

  private Instant effectiveTo;

  @Column(nullable = false, length = 64)
  private String sha256;

  @Column(nullable = false)
  private String storageKey;

  @Column(nullable = false)
  private boolean active;

  protected LegalTemplate() {}

  public LegalTemplate(LegalTemplateType type, int version, Instant effectiveFrom, String sha256, String storageKey) {
    this.id = UUID.randomUUID();
    this.type = type;
    this.version = version;
    this.effectiveFrom = effectiveFrom;
    this.sha256 = sha256;
    this.storageKey = storageKey;
    this.active = true;
  }

  public UUID getId() {
    return id;
  }

  public LegalTemplateType getType() {
    return type;
  }

  public int getVersion() {
    return version;
  }

  public Instant getEffectiveFrom() {
    return effectiveFrom;
  }

  public Instant getEffectiveTo() {
    return effectiveTo;
  }

  public String getSha256() {
    return sha256;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public boolean isActive() {
    return active;
  }

  public void deactivate(java.time.Instant effectiveTo) {
    this.active = false;
    this.effectiveTo = effectiveTo;
  }
}
