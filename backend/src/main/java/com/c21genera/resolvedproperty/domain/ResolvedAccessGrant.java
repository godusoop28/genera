package com.c21genera.resolvedproperty.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Control de Cambios #42: acceso expreso de un usuario a un proyecto de Propiedad Resuelta. Ver el
 * proyecto no implica descargar ni subir archivos. Retirarlo (revokedAt) corta todo acceso posterior.
 */
@Entity
@Table(name = "resolved_access_grant")
public class ResolvedAccessGrant {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID projectId;

  @Column(nullable = false)
  private UUID userId;

  private String userName;

  @Column(nullable = false)
  private boolean canDownload;

  @Column(nullable = false)
  private boolean canUpload;

  private String grantedByName;

  @Column(nullable = false)
  private Instant grantedAt;

  private String revokedByName;
  private Instant revokedAt;

  protected ResolvedAccessGrant() {}

  public ResolvedAccessGrant(UUID projectId, UUID userId, String userName, boolean canDownload, boolean canUpload, String grantedByName, Instant now) {
    this.id = UUID.randomUUID();
    this.projectId = projectId;
    this.userId = userId;
    this.userName = userName;
    this.canDownload = canDownload;
    this.canUpload = canUpload;
    this.grantedByName = grantedByName;
    this.grantedAt = now;
  }

  public void change(boolean canDownload, boolean canUpload, String byName, Instant now) {
    this.canDownload = canDownload;
    this.canUpload = canUpload;
    this.grantedByName = byName;
    this.grantedAt = now;
  }

  public void revoke(String byName, Instant now) {
    this.revokedByName = byName;
    this.revokedAt = now;
  }

  public boolean isActive() {
    return revokedAt == null;
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getUserName() {
    return userName;
  }

  public boolean isCanDownload() {
    return canDownload;
  }

  public boolean isCanUpload() {
    return canUpload;
  }

  public String getGrantedByName() {
    return grantedByName;
  }

  public Instant getGrantedAt() {
    return grantedAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public String getRevokedByName() {
    return revokedByName;
  }
}
