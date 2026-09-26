package com.c21genera.contracts.domain;

import com.c21genera.shared.domain.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Snapshot INMUTABLE de un contrato generado (ver AGENTS §75). Si el
 * expediente cambia después, este registro NO cambia: se genera una nueva
 * versión y esta queda SUPERSEDED. El estado solo avanza a SIGNED cuando
 * todas las partes firmaron (ver ContractSignature); nunca por un cambio
 * manual.
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

  @Column(nullable = false)
  private String snapshotJson;

  @Column(nullable = false)
  private String docxStorageKey;

  private String pdfStorageKey;

  @Column(nullable = false)
  private Instant generatedAt;

  @Column(nullable = false)
  private UUID generatedBy;

  /** Huella del snapshot de datos con el que se generó. */
  @Column(nullable = false, length = 64)
  private String sha256;

  /** Huella del PDF que se firma: cada firma registra esta misma huella. */
  @Column(length = 64)
  private String documentSha256;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private ContractGenerationStatus status;

  private String missingItems;
  private String variantSummary;

  private Instant signedAt;
  private Instant deliveredAt;

  @Column(length = 32)
  private String deliveryMethod;

  private Instant supersededAt;
  private String supersededReason;

  private String signedPackageKey;

  protected ContractGeneration() {}

  public ContractGeneration(
      UUID expedienteId,
      int versionNumber,
      String snapshotJson,
      String docxStorageKey,
      String pdfStorageKey,
      Instant generatedAt,
      UUID generatedBy,
      String sha256,
      String documentSha256,
      boolean draft,
      List<String> missingItems,
      String variantSummary) {
    this.id = UUID.randomUUID();
    this.expedienteId = expedienteId;
    this.versionNumber = versionNumber;
    this.snapshotJson = snapshotJson;
    this.docxStorageKey = docxStorageKey;
    this.pdfStorageKey = pdfStorageKey;
    this.generatedAt = generatedAt;
    this.generatedBy = generatedBy;
    this.sha256 = sha256;
    this.documentSha256 = documentSha256;
    this.status = draft ? ContractGenerationStatus.DRAFT_INCOMPLETE : ContractGenerationStatus.GENERATED;
    this.missingItems = missingItems == null || missingItems.isEmpty() ? null : String.join("\n", missingItems);
    this.variantSummary = variantSummary;
  }

  public boolean isAwaitingSignatures() {
    return status == ContractGenerationStatus.GENERATED || status == ContractGenerationStatus.PARTIALLY_SIGNED;
  }

  /** Borradores, pendientes de firma y parcialmente firmados pueden quedar sin efecto; uno firmado no. */
  public boolean isSupersedable() {
    return status == ContractGenerationStatus.DRAFT_INCOMPLETE || isAwaitingSignatures();
  }

  public void ensureSignable() {
    if (status == ContractGenerationStatus.DRAFT_INCOMPLETE) {
      throw new ConflictException("CONTRACT_DRAFT", "Este contrato es un borrador INCOMPLETO y no se puede firmar.");
    }
    if (status == ContractGenerationStatus.SUPERSEDED) {
      throw new ConflictException(
          "CONTRACT_SUPERSEDED", "Esta versión del contrato quedó sin efecto porque se generó una versión nueva. Firma la versión vigente.");
    }
    if (!isAwaitingSignatures()) {
      throw new ConflictException("CONTRACT_ALREADY_SIGNED", "Este contrato ya está firmado por todas las partes.");
    }
  }

  public void markPartiallySigned() {
    if (status == ContractGenerationStatus.GENERATED) {
      this.status = ContractGenerationStatus.PARTIALLY_SIGNED;
    }
  }

  public void markFullySigned(Instant when, String signedPackageKey) {
    this.status = ContractGenerationStatus.SIGNED;
    this.signedAt = when;
    this.signedPackageKey = signedPackageKey;
  }

  public void supersede(Instant when, String reason) {
    this.status = ContractGenerationStatus.SUPERSEDED;
    this.supersededAt = when;
    this.supersededReason = reason;
  }

  public void markDelivered(Instant when, String method) {
    if (status != ContractGenerationStatus.SIGNED && status != ContractGenerationStatus.DELIVERED) {
      throw new ConflictException(
          "CONTRACT_NOT_SIGNED", "Solo se puede registrar la entrega de un contrato firmado por todas las partes.");
    }
    this.status = ContractGenerationStatus.DELIVERED;
    this.deliveredAt = when;
    this.deliveryMethod = method;
  }

  public List<String> missingItemList() {
    return missingItems == null ? List.of() : List.of(missingItems.split("\n"));
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

  public String getDocumentSha256() {
    return documentSha256;
  }

  public ContractGenerationStatus getStatus() {
    return status;
  }

  public String getVariantSummary() {
    return variantSummary;
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

  public Instant getSupersededAt() {
    return supersededAt;
  }

  public String getSupersededReason() {
    return supersededReason;
  }

  public String getSignedPackageKey() {
    return signedPackageKey;
  }
}
