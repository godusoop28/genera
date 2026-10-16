package com.c21genera.notary.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Control de Cambios #23: el asesor confirma que la versión vigente de un documento de cierre está
 * vigente y al corriente de pago. Vale solo para esa versión: si se sube otra, hay que confirmarla.
 */
@Entity
@Table(name = "closing_document_check")
public class ClosingDocumentCheck {

  @Id
  private UUID documentId;

  @Column(nullable = false)
  private UUID expedienteId;

  @Column(nullable = false)
  private UUID documentVersionId;

  @Column(nullable = false)
  private boolean valid;

  private String note;

  @Column(length = 160)
  private String checkedByName;

  @Column(nullable = false)
  private Instant checkedAt;

  protected ClosingDocumentCheck() {}

  public ClosingDocumentCheck(UUID documentId, UUID expedienteId) {
    this.documentId = documentId;
    this.expedienteId = expedienteId;
  }

  public void record(UUID versionId, boolean valid, String note, String byName, Instant when) {
    this.documentVersionId = versionId;
    this.valid = valid;
    this.note = note;
    this.checkedByName = byName;
    this.checkedAt = when;
  }

  /** ¿Está confirmada como vigente y al corriente la versión indicada? */
  public boolean validFor(UUID versionId) {
    return valid && versionId != null && versionId.equals(documentVersionId);
  }

  public UUID getDocumentId() {
    return documentId;
  }

  public UUID getExpedienteId() {
    return expedienteId;
  }

  public UUID getDocumentVersionId() {
    return documentVersionId;
  }

  public boolean isValid() {
    return valid;
  }

  public String getNote() {
    return note;
  }

  public String getCheckedByName() {
    return checkedByName;
  }

  public Instant getCheckedAt() {
    return checkedAt;
  }
}
