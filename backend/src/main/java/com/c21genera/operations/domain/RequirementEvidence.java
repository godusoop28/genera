package com.c21genera.operations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * CC#32: evidencia de un requisito, versionada. Puede ser un archivo cargado al cierre, un documento
 * ya existente del expediente (se vincula, no se duplica) o una nota (p. ej. número de registro).
 */
@Entity
@Table(name = "closing_requirement_evidence")
public class RequirementEvidence {

  public enum Kind {
    FILE,
    EXPEDIENTE_DOCUMENT,
    NOTE
  }

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID requirementId;

  private int versionNumber;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private Kind kind;

  private String storageKey;
  private String filename;
  private String contentType;
  private Long sizeBytes;
  private String sha256;
  private UUID documentId;
  private UUID documentVersionId;
  private String documentName;
  private String note;
  private LocalDate documentDate;
  private String attachedByName;

  @Column(nullable = false)
  private Instant attachedAt;

  protected RequirementEvidence() {}

  public static RequirementEvidence file(UUID requirementId, int version, String key, String filename, String contentType, long size, String sha256, String note, LocalDate documentDate, String by, Instant now) {
    RequirementEvidence e = base(requirementId, version, Kind.FILE, note, documentDate, by, now);
    e.storageKey = key;
    e.filename = filename;
    e.contentType = contentType;
    e.sizeBytes = size;
    e.sha256 = sha256;
    return e;
  }

  public static RequirementEvidence document(UUID requirementId, int version, UUID documentId, UUID documentVersionId, String name, String note, LocalDate documentDate, String by, Instant now) {
    RequirementEvidence e = base(requirementId, version, Kind.EXPEDIENTE_DOCUMENT, note, documentDate, by, now);
    e.documentId = documentId;
    e.documentVersionId = documentVersionId;
    e.documentName = name;
    return e;
  }

  public static RequirementEvidence note(UUID requirementId, int version, String note, LocalDate documentDate, String by, Instant now) {
    return base(requirementId, version, Kind.NOTE, note, documentDate, by, now);
  }

  private static RequirementEvidence base(UUID requirementId, int version, Kind kind, String note, LocalDate documentDate, String by, Instant now) {
    RequirementEvidence e = new RequirementEvidence();
    e.id = UUID.randomUUID();
    e.requirementId = requirementId;
    e.versionNumber = version;
    e.kind = kind;
    e.note = note;
    e.documentDate = documentDate;
    e.attachedByName = by;
    e.attachedAt = now;
    return e;
  }

  public UUID getId() {
    return id;
  }

  public UUID getRequirementId() {
    return requirementId;
  }

  public int getVersionNumber() {
    return versionNumber;
  }

  public Kind getKind() {
    return kind;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public String getFilename() {
    return filename;
  }

  public String getContentType() {
    return contentType;
  }

  public Long getSizeBytes() {
    return sizeBytes;
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public UUID getDocumentVersionId() {
    return documentVersionId;
  }

  public String getDocumentName() {
    return documentName;
  }

  public String getNote() {
    return note;
  }

  public LocalDate getDocumentDate() {
    return documentDate;
  }

  public String getAttachedByName() {
    return attachedByName;
  }

  public Instant getAttachedAt() {
    return attachedAt;
  }
}
