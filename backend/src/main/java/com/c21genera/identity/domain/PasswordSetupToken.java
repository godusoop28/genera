package com.c21genera.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Enlace de un solo uso para definir contraseña (alta o restablecimiento). Igual
 * que el refresh token, el valor real nunca se persiste: solo su hash SHA-256.
 */
@Entity
@Table(name = "password_setup_token")
public class PasswordSetupToken {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false, unique = true)
  private String tokenHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private PasswordSetupPurpose purpose;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant expiresAt;

  private Instant usedAt;

  private Instant revokedAt;

  private UUID createdByUserId;

  protected PasswordSetupToken() {}

  public PasswordSetupToken(UUID userId, String tokenHash, PasswordSetupPurpose purpose, Instant createdAt, Instant expiresAt, UUID createdByUserId) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.purpose = purpose;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
    this.createdByUserId = createdByUserId;
  }

  public boolean isUsable(Instant now) {
    return usedAt == null && revokedAt == null && now.isBefore(expiresAt);
  }

  public void markUsed(Instant when) {
    this.usedAt = when;
  }

  public void revoke(Instant when) {
    if (usedAt == null && revokedAt == null) {
      this.revokedAt = when;
    }
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public PasswordSetupPurpose getPurpose() {
    return purpose;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
