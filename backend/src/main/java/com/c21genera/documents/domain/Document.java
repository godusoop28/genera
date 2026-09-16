package com.c21genera.documents.domain;

import com.c21genera.shared.domain.ReviewDecision;

import com.c21genera.documents.DocumentStatus;
import com.c21genera.shared.domain.DocumentTypeCode;
import com.c21genera.shared.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Documento LÓGICO (el "slot" requerido), separado del archivo físico
 * (ver AGENTS §24). El archivo real vive en {@link DocumentVersion}.
 */
@Entity
@Table(name = "document")
public class Document extends AuditableEntity {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID expedienteId;

  @Column(nullable = false, length = 64)
  private String requirementCode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private DocumentTypeCode type;

  private UUID participantId;

  @Column(nullable = false)
  private boolean required;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private DocumentStatus status;

  @Column(nullable = false)
  private int currentVersionNumber;

  protected Document() {}

  public Document(UUID expedienteId, String requirementCode, DocumentTypeCode type, UUID participantId, boolean required) {
    this.id = UUID.randomUUID();
    this.expedienteId = expedienteId;
    this.requirementCode = requirementCode;
    this.type = type;
    this.participantId = participantId;
    this.required = required;
    this.status = DocumentStatus.PENDING;
    this.currentVersionNumber = 0;
  }

  public int startNewVersion() {
    this.currentVersionNumber += 1;
    this.status = DocumentStatus.UPLOADED;
    return this.currentVersionNumber;
  }

  public void markReadyForReview() {
    if (this.status == DocumentStatus.UPLOADED) {
      this.status = DocumentStatus.READY_FOR_REVIEW;
    }
  }

  public void applyReview(ReviewDecision decision) {
    this.status =
        switch (decision) {
          case ACCEPTED -> DocumentStatus.ACCEPTED;
          case RETURNED -> DocumentStatus.RETURNED;
          case REJECTED -> DocumentStatus.REJECTED;
        };
  }

  public UUID getId() {
    return id;
  }

  public UUID getExpedienteId() {
    return expedienteId;
  }

  public String getRequirementCode() {
    return requirementCode;
  }

  public DocumentTypeCode getType() {
    return type;
  }

  public UUID getParticipantId() {
    return participantId;
  }

  public boolean isRequired() {
    return required;
  }

  public DocumentStatus getStatus() {
    return status;
  }

  public int getCurrentVersionNumber() {
    return currentVersionNumber;
  }
}
