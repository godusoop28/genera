package com.c21genera.documents.domain;

import com.c21genera.documents.ProcessingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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

  /** ACCEPTED, ACCEPTED_WITH_WARNINGS o UNREADABLE (ver documentprocessing). */
  @Column(length = 32)
  private String qualityLevel;

  /** Advertencias de calidad, una por línea: se muestran, nunca bloquean. */
  private String qualityWarnings;

  /** Advertencias de la revisión con IA, una por línea. */
  private String aiWarnings;

  private Integer aiPagesAnalyzed;
  private Integer aiPagesTotal;
  private Integer aiFieldsExpected;
  private Integer aiFieldsFound;

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
    recordAiAssessment(typeMatches, legible, detectedKind, observations, checkFailed, when, AiDetails.NONE);
  }

  /** Detalle adicional de la revisión con IA (todo opcional). */
  public record AiDetails(List<String> warnings, Integer pagesAnalyzed, Integer pagesTotal, Integer fieldsExpected, Integer fieldsFound) {
    public static final AiDetails NONE = new AiDetails(List.of(), null, null, null, null);
  }

  public void recordAiAssessment(
      Boolean typeMatches, Boolean legible, String detectedKind, String observations, boolean checkFailed, Instant when, AiDetails details) {
    this.aiCheckFailed = checkFailed;
    this.aiTypeMatches = typeMatches;
    this.aiLegible = legible;
    this.aiDetectedKind = detectedKind;
    this.aiObservations = observations;
    this.aiAssessedAt = when;
    AiDetails d = details == null ? AiDetails.NONE : details;
    this.aiWarnings = joinLines(d.warnings());
    this.aiPagesAnalyzed = d.pagesAnalyzed();
    this.aiPagesTotal = d.pagesTotal();
    this.aiFieldsExpected = d.fieldsExpected();
    this.aiFieldsFound = d.fieldsFound();
  }

  /** Se va a volver a ejecutar la revisión con IA: el resultado anterior deja de contar mientras tanto. */
  public void clearAiAssessment() {
    recordAiAssessment(null, null, null, null, false, null, AiDetails.NONE);
  }

  /** Se va a volver a procesar desde los archivos originales (p. ej. con reglas de calidad nuevas). */
  public void resetForReprocessing() {
    this.processingStatus = ProcessingStatus.QUEUED;
    this.processingError = null;
    this.qualityLevel = null;
    this.qualityWarnings = null;
    clearAiAssessment();
  }

  /**
   * Lo único que impide aceptar esta versión sin una autorización de
   * excepción: que no se pueda leer (ninguna página utilizable, archivo
   * dañado, o la IA confirma que es ilegible). Todo lo demás (advertencias de
   * calidad, que parezca otro tipo de documento, que la IA no haya respondido)
   * es una advertencia: el revisor la ve y decide (ver {@link #warnings()}).
   */
  public List<String> blockingIssues() {
    List<String> issues = new ArrayList<>();
    switch (processingStatus) {
      case QUALITY_FAILED -> issues.add("No se puede leer el archivo: " + processingError);
      case FAILED -> issues.add("El archivo no se pudo procesar: " + processingError);
      default -> {
        /* sin alertas de procesamiento */
      }
    }
    if (Boolean.FALSE.equals(aiLegible)) {
      issues.add("La revisión automática indica que el documento no es legible");
    }
    return issues;
  }

  /** Avisos para el revisor que NO impiden aceptar. */
  public List<String> warnings() {
    List<String> result = new ArrayList<>(splitLines(qualityWarnings));
    if (Boolean.FALSE.equals(aiTypeMatches)) {
      result.add(
          "La revisión automática indica que el archivo parece ser otro documento"
              + (aiDetectedKind != null && !aiDetectedKind.isBlank() ? " (" + aiDetectedKind + ")" : "")
              + "; los datos que se leyeron se conservaron");
    }
    if (aiCheckFailed) {
      result.add(
          "No se pudo hacer la revisión automática del contenido (el servicio de IA no respondió); verifícalo visualmente o reprocésalo con IA");
    }
    result.addAll(splitLines(aiWarnings));
    return result.stream().distinct().toList();
  }

  private static String joinLines(List<String> lines) {
    if (lines == null || lines.isEmpty()) {
      return null;
    }
    String joined = String.join("\n", lines.stream().filter(l -> l != null && !l.isBlank()).map(String::strip).toList());
    return joined.isBlank() ? null : joined;
  }

  private static List<String> splitLines(String text) {
    return text == null || text.isBlank() ? List.of() : Arrays.stream(text.split("\n")).filter(l -> !l.isBlank()).toList();
  }

  public void startProcessing() {
    this.processingStatus = ProcessingStatus.PROCESSING;
  }

  public void completeProcessing(String pdfStorageKey, String normalizedStorageKey) {
    completeProcessing(pdfStorageKey, normalizedStorageKey, "ACCEPTED", List.of());
  }

  public void completeProcessing(String pdfStorageKey, String normalizedStorageKey, String qualityLevel, List<String> qualityWarnings) {
    this.processingStatus = ProcessingStatus.PROCESSED;
    this.processingError = null;
    this.storageKeyPdf = pdfStorageKey;
    this.storageKeyNormalized = normalizedStorageKey;
    this.qualityLevel = qualityLevel;
    this.qualityWarnings = joinLines(qualityWarnings);
  }

  public void failQuality(String reason) {
    this.processingStatus = ProcessingStatus.QUALITY_FAILED;
    this.processingError = reason;
    this.qualityLevel = "UNREADABLE";
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

  public String getQualityLevel() {
    return qualityLevel;
  }

  public Integer getAiPagesAnalyzed() {
    return aiPagesAnalyzed;
  }

  public Integer getAiPagesTotal() {
    return aiPagesTotal;
  }

  public Integer getAiFieldsExpected() {
    return aiFieldsExpected;
  }

  public Integer getAiFieldsFound() {
    return aiFieldsFound;
  }
}
