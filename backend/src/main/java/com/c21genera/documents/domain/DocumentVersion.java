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

  private UUID uploadedByUserId;
  private String uploadedByName;

  private Boolean aiTypeMatches;
  private Boolean aiLegible;
  private String aiDetectedKind;
  private String aiObservations;
  private Instant aiAssessedAt;

  @Column(nullable = false)
  private boolean aiCheckFailed;

  protected DocumentVersion() {}

  public DocumentVersion(
      UUID documentId, int versionNumber, Instant uploadedAt, UploadedVia uploadedVia, UUID uploadedByUserId, String uploadedByName) {
    this.id = UUID.randomUUID();
    this.documentId = documentId;
    this.versionNumber = versionNumber;
    this.uploadedAt = uploadedAt;
    this.uploadedVia = uploadedVia;
    this.uploadedByUserId = uploadedByUserId;
    this.uploadedByName = uploadedByName;
    this.processingStatus = ProcessingStatus.QUEUED;
  }

  public void recordAiAssessment(
      Boolean typeMatches, Boolean legible, String detectedKind, String observations, boolean checkFailed, Instant when) {
    this.aiCheckFailed = checkFailed;
    this.aiTypeMatches = typeMatches;
    this.aiLegible = legible;
    this.aiDetectedKind = detectedKind;
    this.aiObservations = observations;
    this.aiAssessedAt = when;
  }

  /**
   * Alertas que impiden aceptar esta versión sin una autorización de
   * excepción: calidad automática fallida, archivo sin procesar, o la IA
   * indicó que no corresponde al documento solicitado o que es ilegible.
   */
  public java.util.List<String> blockingIssues() {
    java.util.List<String> issues = new java.util.ArrayList<>();
    switch (processingStatus) {
      case QUALITY_FAILED -> issues.add("No pasó la verificación automática de calidad: " + processingError);
      case FAILED -> issues.add("El archivo no se pudo procesar: " + processingError);
      default -> {
        /* sin alertas de procesamiento */
      }
    }
    if (Boolean.FALSE.equals(aiTypeMatches)) {
      issues.add(
          "La revisión automática indica que el archivo no corresponde al documento solicitado"
              + (aiDetectedKind != null && !aiDetectedKind.isBlank() ? " (parece: " + aiDetectedKind + ")" : ""));
    }
    if (Boolean.FALSE.equals(aiLegible)) {
      issues.add("La revisión automática indica que el documento no es legible");
    }
    if (aiCheckFailed) {
      issues.add("No se pudo hacer la revisión automática del contenido (el servicio de IA no respondió); verifica a mano que sea el documento correcto");
    }
    return issues;
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

  public UUID getUploadedByUserId() {
    return uploadedByUserId;
  }

  public String getUploadedByName() {
    return uploadedByName;
  }

  public Boolean getAiTypeMatches() {
    return aiTypeMatches;
  }

  public Boolean getAiLegible() {
    return aiLegible;
  }

  public String getAiDetectedKind() {
    return aiDetectedKind;
  }

  public String getAiObservations() {
    return aiObservations;
  }

  public boolean isAiCheckFailed() {
    return aiCheckFailed;
  }

  public Instant getAiAssessedAt() {
    return aiAssessedAt;
  }
}
