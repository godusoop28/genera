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

  /**
   * El asesor decidió que el cliente lo suba después, cuando lo tenga: sigue
   * siendo obligatorio para aprobar la documentación y generar el contrato,
   * pero no impide que el cliente envíe lo que se le pidió primero.
   */
  @Column(nullable = false)
  private boolean deferred;

  /** Por qué el asesor no lo pidió en la primera entrega (excepción justificada). */
  private String deferralReason;

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

  /** Documento agregado a mano porque no está en el listado (Control de Cambios #1): su nombre. */
  @Column(length = 120)
  private String customName;

  /**
   * Excepción aprobada por el asesor (Control de Cambios #5): falta el documento pero no
   * bloquea la aprobación ni el contrato; el cliente puede entregarlo después.
   */
  private String exceptionNote;

  private UUID exceptionByUserId;
  private Instant exceptionAt;

  /** Se pidió una versión actualizada (Control de Cambios #10); la anterior se conserva. */
  private String updateRequestNote;

  private Instant updateRequestedAt;

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

  /** Documento agregado a mano con su propio nombre (Control de Cambios #1). */
  public static Document custom(UUID expedienteId, String name, UUID participantId) {
    Document document = new Document(expedienteId, "extra-" + UUID.randomUUID(), DocumentTypeCode.OTHER, participantId, true);
    document.customName = name;
    return document;
  }

  public int startNewVersion() {
    if (status == DocumentStatus.NOT_APPLICABLE) {
      throw new ConflictException(
          "DOCUMENT_NOT_APPLICABLE", "Este documento está marcado como \"No aplica\". Vuelve a solicitarlo antes de cargar un archivo.");
    }
    this.currentVersionNumber += 1;
    this.status = DocumentStatus.UPLOADED;
    // La versión nueva es la actualización que se pidió; la anterior queda en el historial.
    this.updateRequestNote = null;
    this.updateRequestedAt = null;
    return this.currentVersionNumber;
  }

  /** Aprueba que falte por ahora (con nota): no bloquea el flujo y se puede entregar después. */
  public void approveException(String note, UUID byUserId, Instant when) {
    if (status == DocumentStatus.ACCEPTED || status == DocumentStatus.NOT_APPLICABLE) {
      throw new ConflictException(
          "DOCUMENT_ALREADY_RESOLVED", "Este documento ya está aceptado o marcado como \"No aplica\"; no necesita una excepción.");
    }
    this.exceptionNote = note;
    this.exceptionByUserId = byUserId;
    this.exceptionAt = when;
  }

  public void revokeException() {
    this.exceptionNote = null;
    this.exceptionByUserId = null;
    this.exceptionAt = null;
  }

  public boolean hasException() {
    return exceptionAt != null;
  }

  /** Pide una versión actualizada de un documento ya entregado (p. ej. una constancia vencida). */
  public void requestUpdate(String note, Instant when) {
    if (currentVersionNumber == 0 || status == DocumentStatus.NOT_APPLICABLE) {
      throw new ConflictException("DOCUMENT_WITHOUT_FILE", "Este documento todavía no tiene un archivo que actualizar.");
    }
    this.updateRequestNote = note;
    this.updateRequestedAt = when;
  }

  public void cancelUpdateRequest() {
    this.updateRequestNote = null;
    this.updateRequestedAt = null;
  }

  public boolean isUpdateRequested() {
    return updateRequestedAt != null;
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

  /**
   * El archivo cargado aquí se movió a otro requisito (p. ej. era un predial
   * cargado como acta de matrimonio): este requisito vuelve a pendiente. El
   * historial de versiones se conserva.
   */
  public void detachCurrentFile() {
    if (status == DocumentStatus.ACCEPTED) {
      throw new ConflictException("DOCUMENT_ALREADY_ACCEPTED", "Un documento ya aceptado no se puede mover a otro requisito.");
    }
    this.status = DocumentStatus.PENDING;
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

  /** Cuenta como "cargado" para poder enviar el expediente (un "No aplica" también cuenta; uno para después no se espera). */
  public boolean isSatisfiedForSubmission() {
    return deferred || hasException() || status != DocumentStatus.PENDING;
  }

  /** Pedirlo desde la primera entrega o dejar que el cliente lo suba después. */
  public void changeDeferred(boolean deferred, String reason) {
    if (status == DocumentStatus.ACCEPTED || status == DocumentStatus.NOT_APPLICABLE) {
      throw new ConflictException(
          "DOCUMENT_ALREADY_RESOLVED", "Este documento ya está aceptado o marcado como \"No aplica\"; no hace falta decidir cuándo pedirlo.");
    }
    this.deferred = deferred;
    this.deferralReason = deferred ? reason : null;
  }

  public boolean isDeferred() {
    return deferred;
  }

  public String getDeferralReason() {
    return deferralReason;
  }

  /** Cuenta como resuelto para aprobar la documentación: aceptado, "No aplica" justificado o excepción aprobada. */
  public boolean isSatisfiedForApproval() {
    return status == DocumentStatus.ACCEPTED || status == DocumentStatus.NOT_APPLICABLE || hasException();
  }

  public boolean isCustom() {
    return customName != null;
  }

  public String getCustomName() {
    return customName;
  }

  /** Nombre con el que se muestra: el capturado si se agregó a mano, si no el del tipo. */
  public String displayName() {
    return customName != null ? customName : com.c21genera.shared.domain.DocumentTypeLabels.of(type);
  }

  public String getExceptionNote() {
    return exceptionNote;
  }

  public UUID getExceptionByUserId() {
    return exceptionByUserId;
  }

  public Instant getExceptionAt() {
    return exceptionAt;
  }

  public String getUpdateRequestNote() {
    return updateRequestNote;
  }

  public Instant getUpdateRequestedAt() {
    return updateRequestedAt;
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
