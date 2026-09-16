package com.c21genera.documents.domain;

import com.c21genera.documents.ProcessingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Una carga concreta (un conjunto de páginas ordenadas) de un {@link
 * Document}. Nunca se sobrescribe ni se borra al reemplazarse (ver AGENTS
 * §25): el reemplazo crea una versión nueva.
 */
@Entity
@Table(name = "document_version")
public class DocumentVersion {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID documentId;

  @Column(nullable = false)
  private int versionNumber;

  private String storageKeyPdf;
  private String storageKeyNormalized;

  @Column(nullable = false)
  private Instant uploadedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private UploadedVia uploadedVia;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ProcessingStatus processingStatus;

  private String processingError;

  protected DocumentVersion() {}

  public DocumentVersion(UUID documentId, int versionNumber, Instant uploadedAt, UploadedVia uploadedVia) {
    this.id = UUID.randomUUID();
    this.documentId = documentId;
    this.versionNumber = versionNumber;
    this.uploadedAt = uploadedAt;
    this.uploadedVia = uploadedVia;
    this.processingStatus = ProcessingStatus.QUEUED;
  }

  public void startProcessing() {
    this.processingStatus = ProcessingStatus.PROCESSING;
  }

  public void completeProcessing(String pdfStorageKey, String normalizedStorageKey) {
    this.processingStatus = ProcessingStatus.PROCESSED;
    this.storageKeyPdf = pdfStorageKey;
    this.storageKeyNormalized = normalizedStorageKey;
  }

  public void failQuality(String reason) {
    this.processingStatus = ProcessingStatus.QUALITY_FAILED;
    this.processingError = reason;
  }

  public void fail(String reason) {
    this.processingStatus = ProcessingStatus.FAILED;
    this.processingError = reason;
  }

  public UUID getId() {
    return id;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public int getVersionNumber() {
    return versionNumber;
  }

  public String getStorageKeyPdf() {
    return storageKeyPdf;
  }

  public String getStorageKeyNormalized() {
    return storageKeyNormalized;
  }

  public Instant getUploadedAt() {
    return uploadedAt;
  }

  public UploadedVia getUploadedVia() {
    return uploadedVia;
  }

  public ProcessingStatus getProcessingStatus() {
    return processingStatus;
  }

  public String getProcessingError() {
    return processingError;
  }
}
