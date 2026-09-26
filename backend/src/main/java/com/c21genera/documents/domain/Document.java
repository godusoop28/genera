package com.c21genera.documents.domain;

import com.c21genera.documents.DocumentStatus;
import com.c21genera.shared.domain.ConflictException;
import com.c21genera.shared.domain.DocumentTypeCode;
import com.c21genera.shared.domain.ReviewDecision;
import com.c21genera.shared.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
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

  private String notApplicableJustification;
  private UUID notApplicableByUserId;
  private Instant notApplicableAt;

  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  private ReviewDecision lastReviewDecision;

  @Enumerated(EnumType.STRING)
  @Column(length = 32)
  private ReturnReasonCode lastReviewReasonCode;

  private String lastReviewComment;
  private Instant lastReviewedAt;

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

  /**
   * Un requisito condicional (p. ej. acta de matrimonio) se materializa como
   * fila desde el inicio con required=false y puede volverse obligatorio
   * después, cuando cambie el dato que lo condiciona (ver
   * DocumentService.on(ExpedienteRequirementsChanged)).
   */
  public void updateRequired(boolean required) {
    this.required = required;
  }

  public int startNewVersion() {
    if (status == DocumentStatus.NOT_APPLICABLE) {
      throw new ConflictException(
          "DOCUMENT_NOT_APPLICABLE", "Este documento está marcado como \"No aplica\". Vuelve a solicitarlo antes de cargar un archivo.");
    }
    this.currentVersionNumber += 1;
    this.status = DocumentStatus.UPLOADED;
    return this.currentVersionNumber;
  }

  public void markReadyForReview() {
    if (this.status == DocumentStatus.UPLOADED) {
      this.status = DocumentStatus.READY_FOR_REVIEW;
    }
  }

  public void applyReview(ReviewDecision decision, ReturnReasonCode reasonCode, String comment, Instant when) {
    this.status =
        switch (decision) {
          case ACCEPTED -> DocumentStatus.ACCEPTED;
          case RETURNED -> DocumentStatus.RETURNED;
          case REJECTED -> DocumentStatus.REJECTED;
        };
    this.lastReviewDecision = decision;
    this.lastReviewReasonCode = reasonCode;
    this.lastReviewComment = comment;
    this.lastReviewedAt = when;
  }

  public void markNotApplicable(String justification, UUID byUserId, Instant when) {
    if (status == DocumentStatus.ACCEPTED) {
      throw new ConflictException("DOCUMENT_ALREADY_ACCEPTED", "Un documento ya aceptado no puede marcarse como \"No aplica\".");
    }
    this.status = DocumentStatus.NOT_APPLICABLE;
    this.notApplicableJustification = justification;
    this.notApplicableByUserId = byUserId;
    this.notApplicableAt = when;
  }

  /** Revierte un "No aplica": vuelve a pendiente, o a revisión si ya tenía archivo cargado. */
  public void requestAgain() {
    if (status != DocumentStatus.NOT_APPLICABLE) {
      throw new ConflictException("DOCUMENT_NOT_NOT_APPLICABLE", "El documento no está marcado como \"No aplica\".");
    }
    this.status = currentVersionNumber > 0 ? DocumentStatus.READY_FOR_REVIEW : DocumentStatus.PENDING;
    this.notApplicableJustification = null;
    this.notApplicableByUserId = null;
    this.notApplicableAt = null;
  }

  /** Cuenta como "cargado" para poder enviar el expediente (un "No aplica" también cuenta). */
  public boolean isSatisfiedForSubmission() {
    return status != DocumentStatus.PENDING;
  }

  /** Cuenta como resuelto para aprobar la documentación: aceptado, o "No aplica" justificado. */
  public boolean isSatisfiedForApproval() {
    return status == DocumentStatus.ACCEPTED || status == DocumentStatus.NOT_APPLICABLE;
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

  public String getNotApplicableJustification() {
    return notApplicableJustification;
  }

  public UUID getNotApplicableByUserId() {
    return notApplicableByUserId;
  }

  public Instant getNotApplicableAt() {
    return notApplicableAt;
  }

  public ReviewDecision getLastReviewDecision() {
    return lastReviewDecision;
  }

  public ReturnReasonCode getLastReviewReasonCode() {
    return lastReviewReasonCode;
  }

  public String getLastReviewComment() {
    return lastReviewComment;
  }

  public Instant getLastReviewedAt() {
    return lastReviewedAt;
  }
}
