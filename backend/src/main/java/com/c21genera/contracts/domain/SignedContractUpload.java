package com.c21genera.contracts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Control de Cambios #20: contrato firmado fuera del sistema (DocuSign o en papel) que se subió al
 * expediente. El archivo vive en documents (tipo SIGNED_CONTRACT, con su visor y permisos por rol);
 * aquí queda cómo se firmó, si es de una operación anterior al sistema y quién lo subió.
 */
@Entity
@Table(name = "signed_contract_upload")
public class SignedContractUpload {

  public enum SignatureMethod {
    DOCUSIGN,
    PHYSICAL
  }

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID expedienteId;

  @Column(nullable = false)
  private UUID documentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private SignatureMethod signatureMethod;

  /** Operación anterior al sistema (contrato histórico). */
  @Column(nullable = false)
  private boolean historical;

  private String note;

  private UUID uploadedByUserId;

  @Column(length = 160)
  private String uploadedByName;

  @Column(nullable = false)
  private Instant uploadedAt;

  protected SignedContractUpload() {}

  public SignedContractUpload(
      UUID expedienteId, UUID documentId, SignatureMethod method, boolean historical, String note, UUID byUserId, String byName, Instant now) {
    this.id = UUID.randomUUID();
    this.expedienteId = expedienteId;
    this.documentId = documentId;
    this.signatureMethod = method;
    this.historical = historical;
    this.note = note;
    this.uploadedByUserId = byUserId;
    this.uploadedByName = byName;
    this.uploadedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getExpedienteId() {
    return expedienteId;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public SignatureMethod getSignatureMethod() {
    return signatureMethod;
  }

  public boolean isHistorical() {
    return historical;
  }

  public String getNote() {
    return note;
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
}
