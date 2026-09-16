package com.c21genera.contracts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Snapshot INMUTABLE de un contrato generado (ver AGENTS §75). Si el
 * expediente cambia después, este registro NO cambia: se genera una nueva
 * versión.
 */
@Entity
@Table(name = "contract_generation")
public class ContractGeneration {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID expedienteId;

  @Column(nullable = false)
  private int versionNumber;

  @Lob
  @Column(nullable = false)
  private String snapshotJson;

  @Column(nullable = false)
  private String docxStorageKey;

  private String pdfStorageKey;

  @Column(nullable = false)
  private Instant generatedAt;

  @Column(nullable = false)
  private UUID generatedBy;

  @Column(nullable = false, length = 64)
  private String sha256;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ContractGenerationStatus status;

  private Instant signedAt;
  private Instant deliveredAt;
  private String deliveryMethod;

  protected ContractGeneration() {}

  public ContractGeneration(
      UUID expedienteId,
      int versionNumber,
      String snapshotJson,
      String docxStorageKey,
      Instant generatedAt,
      UUID generatedBy,
      String sha256) {
    this.id = UUID.randomUUID();
    this.expedienteId = expedienteId;
    this.versionNumber = versionNumber;
    this.snapshotJson = snapshotJson;
    this.docxStorageKey = docxStorageKey;
    this.generatedAt = generatedAt;
    this.generatedBy = generatedBy;
    this.sha256 = sha256;
    this.status = ContractGenerationStatus.GENERATED;
  }

  public void attachPdf(String pdfStorageKey) {
    this.pdfStorageKey = pdfStorageKey;
  }

  public void markSigned(Instant when) {
    this.status = ContractGenerationStatus.SIGNED;
    this.signedAt = when;
  }

  public void markDelivered(Instant when, String method) {
    this.status = ContractGenerationStatus.DELIVERED;
    this.deliveredAt = when;
    this.deliveryMethod = method;
  }

  public UUID getId() {
    return id;
  }

  public UUID getExpedienteId() {
    return expedienteId;
  }

  public int getVersionNumber() {
    return versionNumber;
  }

  public String getSnapshotJson() {
    return snapshotJson;
  }

  public String getDocxStorageKey() {
    return docxStorageKey;
  }

  public String getPdfStorageKey() {
    return pdfStorageKey;
  }

  public Instant getGeneratedAt() {
    return generatedAt;
  }

  public UUID getGeneratedBy() {
    return generatedBy;
  }

  public String getSha256() {
    return sha256;
  }

  public ContractGenerationStatus getStatus() {
    return status;
  }

  public Instant getSignedAt() {
    return signedAt;
  }

  public Instant getDeliveredAt() {
    return deliveredAt;
  }

  public String getDeliveryMethod() {
    return deliveryMethod;
  }
}
