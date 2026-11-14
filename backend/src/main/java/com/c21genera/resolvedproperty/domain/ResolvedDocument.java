package com.c21genera.resolvedproperty.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Control de Cambios #42: archivo documental de un proyecto de Propiedad Resuelta (separado de los expedientes C21). */
@Entity
@Table(name = "resolved_document")
public class ResolvedDocument {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID projectId;

  @Column(nullable = false)
  private String name;

  private String category;

  @Column(nullable = false)
  private String storageKey;

  @Column(nullable = false)
  private String contentType;

  @Column(nullable = false)
  private long sizeBytes;

  private String sha256;
  private String originalFilename;
  private UUID uploadedByUserId;
  private String uploadedByName;

  @Column(nullable = false)
  private Instant uploadedAt;

  private Instant removedAt;
  private String removedByName;

  protected ResolvedDocument() {}

  public ResolvedDocument(
      UUID id,
      UUID projectId,
      String name,
      String category,
      String storageKey,
      String contentType,
      long sizeBytes,
      String sha256,
      String originalFilename,
      UUID uploadedByUserId,
      String uploadedByName,
      Instant uploadedAt) {
    this.id = id;
    this.projectId = projectId;
    this.name = name;
    this.category = category;
    this.storageKey = storageKey;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.sha256 = sha256;
    this.originalFilename = originalFilename;
    this.uploadedByUserId = uploadedByUserId;
    this.uploadedByName = uploadedByName;
    this.uploadedAt = uploadedAt;
  }

  public void remove(String byName, Instant when) {
    this.removedAt = when;
    this.removedByName = byName;
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getName() {
    return name;
  }

  public String getCategory() {
    return category;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public String getContentType() {
    return contentType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public String getSha256() {
    return sha256;
  }

  public String getOriginalFilename() {
    return originalFilename;
  }

  public String getUploadedByName() {
    return uploadedByName;
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }

  public Instant getRemovedAt() {
    return removedAt;
  }
}
