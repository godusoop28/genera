package com.c21genera.feasibility.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Una factibilidad generada con IA para un expediente. Avanza ANALYZING (reporte del
 * "Analista de Factibilidad 3.0") → PRESENTING (datos de la presentación para el propietario)
 * → READY (Word y PowerPoint guardados). Cualquier etapa puede terminar en FAILED con el motivo.
 * Cada solicitud es una versión nueva: las anteriores se conservan.
 */
@Entity
@Table(name = "feasibility_report")
public class FeasibilityReport {

  public enum Status {
    ANALYZING,
    PRESENTING,
    READY,
    FAILED;

    public boolean inProgress() {
      return this == ANALYZING || this == PRESENTING;
    }
  }

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID expedienteId;

  @Column(nullable = false)
  private int versionNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Status status;

  @Column(nullable = false)
  private UUID requestedBy;

  @Column(length = 160)
  private String requestedByName;

  @Column(nullable = false)
  private Instant requestedAt;

  @Column(nullable = false)
  private Instant stageStartedAt;

  private Instant completedAt;

  @Column(nullable = false)
  private String inputJson;

  private String photoKeys;

  private String aiFileIds;

  @Column(length = 120)
  private String aiResponseId;

  @Column(length = 80)
  private String model;

  private String reportMarkdown;

  private String sourcesJson;

  private String presentationJson;

  private String docxStorageKey;

  private String pptxStorageKey;

  private String error;

  /** Versión de cada prompt maestro con la que se generó (0 = texto original del cliente). */
  private Integer analystPromptVersion;

  private Integer presentationPromptVersion;

  protected FeasibilityReport() {}

  public FeasibilityReport(
      UUID expedienteId, int versionNumber, UUID requestedBy, String requestedByName, String inputJson, String photoKeys, Instant now) {
    this.id = UUID.randomUUID();
    this.expedienteId = expedienteId;
    this.versionNumber = versionNumber;
    this.status = Status.ANALYZING;
    this.requestedBy = requestedBy;
    this.requestedByName = requestedByName;
    this.inputJson = inputJson;
    this.photoKeys = photoKeys;
    this.requestedAt = now;
    this.stageStartedAt = now;
  }

  /** Etapa 1 enviada a la IA. */
  public void analysisSubmitted(String responseId, String model, String aiFileIds) {
    this.aiResponseId = responseId;
    this.model = model;
    this.aiFileIds = aiFileIds;
  }

  /** Etapa 1 terminada: queda el reporte y arranca la etapa 2. */
  public void analysisCompleted(String markdown, String sourcesJson, String presentationResponseId, Instant now) {
    this.reportMarkdown = markdown;
    this.sourcesJson = sourcesJson;
    this.aiResponseId = presentationResponseId;
    this.status = Status.PRESENTING;
    this.stageStartedAt = now;
  }

  public void completed(String presentationJson, String sourcesJson, String docxKey, String pptxKey, Instant now) {
    this.presentationJson = presentationJson;
    this.sourcesJson = sourcesJson;
    this.docxStorageKey = docxKey;
    this.pptxStorageKey = pptxKey;
    this.status = Status.READY;
    this.aiResponseId = null;
    this.completedAt = now;
    this.error = null;
  }

  public void failed(String reason, Instant now) {
    this.status = Status.FAILED;
    this.error = reason != null && reason.length() > 2000 ? reason.substring(0, 2000) : reason;
    this.aiResponseId = null;
    this.completedAt = now;
  }

  public void aiFilesReleased() {
    this.aiFileIds = null;
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

  public Status getStatus() {
    return status;
  }

  public UUID getRequestedBy() {
    return requestedBy;
  }

  public String getRequestedByName() {
    return requestedByName;
  }

  public Instant getRequestedAt() {
    return requestedAt;
  }

  public Instant getStageStartedAt() {
    return stageStartedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public String getInputJson() {
    return inputJson;
  }

  public String getPhotoKeys() {
    return photoKeys;
  }

  public String getAiFileIds() {
    return aiFileIds;
  }

  public String getAiResponseId() {
    return aiResponseId;
  }

  public String getModel() {
    return model;
  }

  public String getReportMarkdown() {
    return reportMarkdown;
  }

  public String getSourcesJson() {
    return sourcesJson;
  }

  public String getPresentationJson() {
    return presentationJson;
  }

  public String getDocxStorageKey() {
    return docxStorageKey;
  }

  public String getPptxStorageKey() {
    return pptxStorageKey;
  }

  public void usedAnalystPrompt(int version) {
    this.analystPromptVersion = version;
  }

  public void usedPresentationPrompt(int version) {
    this.presentationPromptVersion = version;
  }

  public Integer getAnalystPromptVersion() {
    return analystPromptVersion;
  }

  public Integer getPresentationPromptVersion() {
    return presentationPromptVersion;
  }

  public String getError() {
    return error;
  }
}
