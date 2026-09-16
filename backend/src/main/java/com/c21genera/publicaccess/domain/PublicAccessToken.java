package com.c21genera.publicaccess.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** El token real nunca se persiste: solo su hash (ver AGENTS §14). */
@Entity
@Table(name = "public_access_token")
public class PublicAccessToken {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID expedienteId;

  @Column(nullable = false, unique = true)
  private String tokenHash;

  @Column(nullable = false)
  private Instant createdAt;

  private Instant expiresAt;

  private Instant revokedAt;

  private Instant lastUsedAt;

  @Column(nullable = false)
  private boolean active;

  protected PublicAccessToken() {}

  public PublicAccessToken(UUID expedienteId, String tokenHash, Instant createdAt, Instant expiresAt) {
    this.id = UUID.randomUUID();
    this.expedienteId = expedienteId;
    this.tokenHash = tokenHash;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
    this.active = true;
  }

  public boolean isUsable(Instant now) {
    return active && revokedAt == null && (expiresAt == null || now.isBefore(expiresAt));
  }

  public void revoke(Instant when) {
    this.active = false;
    this.revokedAt = when;
  }

  public void touchUsage(Instant when) {
    this.lastUsedAt = when;
  }

  public UUID getId() {
    return id;
  }

  public UUID getExpedienteId() {
    return expedienteId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public Instant getLastUsedAt() {
    return lastUsedAt;
  }

  public boolean isActive() {
    return active;
  }
}
