package com.c21genera.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * El refresh token real nunca se persiste: solo su hash (mismo patrón que
 * PublicAccessToken, ver AGENTS §14/§17).
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false, unique = true)
  private String tokenHash;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant expiresAt;

  private Instant revokedAt;

  private UUID replacedByTokenId;

  protected RefreshToken() {}

  public RefreshToken(UUID userId, String tokenHash, Instant createdAt, Instant expiresAt) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  public boolean isActive(Instant now) {
    return revokedAt == null && now.isBefore(expiresAt);
  }

  public void revoke(UUID replacedByTokenId, Instant when) {
    this.revokedAt = when;
    this.replacedByTokenId = replacedByTokenId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }
}
