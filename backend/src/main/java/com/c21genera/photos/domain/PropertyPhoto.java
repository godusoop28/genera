package com.c21genera.photos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Control de Cambios #33: fotografía del inmueble. Se guarda quién la subió (cliente o asesor) y
 * cuándo; la selección para el portal (si se usa, si es la principal y su orden) la decide el asesor.
 * Retirarla no borra el archivo: queda marcada como retirada y deja de usarse.
 */
@Entity
@Table(name = "property_photo")
public class PropertyPhoto {

  public enum UploadedBy {
    CLIENT,
    STAFF
  }

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID expedienteId;

  @Column(nullable = false)
  private String storageKey;

  @Column(nullable = false, length = 40)
  private String contentType;

  private long sizeBytes;

  private String sha256;

  private String originalFilename;

  @Column(nullable = false, length = 16)
  @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
  private UploadedBy uploadedByKind;

  private UUID uploadedByUserId;

  private String uploadedByName;

  @Column(nullable = false)
  private Instant uploadedAt;

  private boolean selectedForPortal;

  private boolean mainPhoto;

  private Integer portalOrder;

  private String selectionUpdatedBy;

  private Instant selectionUpdatedAt;

  private Instant removedAt;

  private String removedByName;

  protected PropertyPhoto() {}

  public PropertyPhoto(
      UUID id,
      UUID expedienteId,
      String storageKey,
      String contentType,
      long sizeBytes,
      String sha256,
      String originalFilename,
      UploadedBy uploadedByKind,
      UUID uploadedByUserId,
      String uploadedByName,
      Instant uploadedAt) {
    this.id = id;
    this.expedienteId = expedienteId;
    this.storageKey = storageKey;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.sha256 = sha256;
    this.originalFilename = originalFilename;
    this.uploadedByKind = uploadedByKind;
    this.uploadedByUserId = uploadedByUserId;
    this.uploadedByName = uploadedByName;
    this.uploadedAt = uploadedAt;
  }

  public void select(boolean selected, boolean main, Integer order, String byName, Instant now) {
    this.selectedForPortal = selected;
    this.mainPhoto = selected && main;
    this.portalOrder = selected ? order : null;
    this.selectionUpdatedBy = byName;
    this.selectionUpdatedAt = now;
  }

  public void remove(String byName, Instant now) {
    this.removedAt = now;
    this.removedByName = byName;
    this.selectedForPortal = false;
    this.mainPhoto = false;
    this.portalOrder = null;
  }

  public boolean isRemoved() {
    return removedAt != null;
  }

  public UUID getId() {
    return id;
  }

  public UUID getExpedienteId() {
    return expedienteId;
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

  public String getOriginalFilename() {
    return originalFilename;
  }

  public UploadedBy getUploadedByKind() {
    return uploadedByKind;
  }

  public UUID getUploadedByUserId() {
    return uploadedByUserId;
  }

  public String getUploadedByName() {
    return uploadedByName;
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }

  public boolean isSelectedForPortal() {
    return selectedForPortal;
  }

  public boolean isMainPhoto() {
    return mainPhoto;
  }

  public Integer getPortalOrder() {
    return portalOrder;
  }

  public String getSelectionUpdatedBy() {
    return selectionUpdatedBy;
  }

  public Instant getSelectionUpdatedAt() {
    return selectionUpdatedAt;
  }
}
