package com.c21genera.privacy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Control de Cambios #44: liga para que el cliente firme solo el aviso de privacidad de su
 * expediente. El token real nunca se persiste: solo su hash.
 */
@Entity
@Table(name = "privacy_sign_link")
public class PrivacySignLink {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID expedienteId;

  @Column(nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(nullable = false)
  private Instant createdAt;

  private UUID createdByUserId;

  private String createdByName;

  @Column(nullable = false)
  private Instant expiresAt;

  private Instant revokedAt;

  private Instant usedAt;

  private Instant lastOpenedAt;

  protected PrivacySignLink() {}

  public PrivacySignLink(UUID expedienteId, String tokenHash, Instant createdAt, Instant expiresAt, UUID createdByUserId, String createdByName) {
    this.id = UUID.randomUUID();
    this.expedienteId = expedienteId;
    this.tokenHash = tokenHash;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
    this.createdByUserId = createdByUserId;
    this.createdByName = createdByName;
  }

  public boolean isUsable(Instant now) {
    return revokedAt == null && usedAt == null && now.isBefore(expiresAt);
  }

  public boolean isOpen() {
    return revokedAt == null && usedAt == null;
  }

  public void revoke(Instant when) {
    if (isOpen()) {
      this.revokedAt = when;
    }
  }

  public void markUsed(Instant when) {
    this.usedAt = when;
  }

  public void touch(Instant when) {
    this.lastOpenedAt = when;
  }

  public UUID getId() {
    return id;
  }

  public UUID getExpedienteId() {
    return expedienteId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getCreatedByName() {
    return createdByName;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public Instant getUsedAt() {
    return usedAt;
  }

  public Instant getLastOpenedAt() {
    return lastOpenedAt;
  }
}
