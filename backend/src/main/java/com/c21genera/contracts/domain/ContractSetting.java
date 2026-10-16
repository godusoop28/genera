package com.c21genera.contracts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Textos estándar del contrato que C21 Genera puede actualizar sin un cambio de código
 * (Control de Cambios #21: instrucciones para visitas al inmueble).
 */
@Entity
@Table(name = "contract_setting")
public class ContractSetting {

  /** Instrucciones para visitas con las que se prellena cada expediente. */
  public static final String VISIT_INSTRUCTIONS = "VISIT_INSTRUCTIONS";

  @Id
  @Column(name = "setting_key", length = 64)
  private String key;

  @Column(name = "setting_value", nullable = false)
  private String value;

  @Column(nullable = false)
  private Instant updatedAt;

  private UUID updatedByUserId;

  @Column(length = 160)
  private String updatedByName;

  protected ContractSetting() {}

  public ContractSetting(String key, String value, Instant now, UUID byUserId, String byName) {
    this.key = key;
    change(value, now, byUserId, byName);
  }

  public void change(String value, Instant now, UUID byUserId, String byName) {
    this.value = value;
    this.updatedAt = now;
    this.updatedByUserId = byUserId;
    this.updatedByName = byName;
  }

  public String getKey() {
    return key;
  }

  public String getValue() {
    return value;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public UUID getUpdatedByUserId() {
    return updatedByUserId;
  }

  public String getUpdatedByName() {
    return updatedByName;
  }
}
