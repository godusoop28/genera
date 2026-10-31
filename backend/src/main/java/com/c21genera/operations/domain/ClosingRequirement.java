package com.c21genera.operations.domain;

import com.c21genera.operations.domain.OperationParty.PartySide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * CC#32: requisito de una operación (copiado de la matriz al iniciar el cierre). Recibir un archivo
 * lo deja "Recibido", nunca "Validado": solo el rol autorizado lo valida. "No aplica" pide
 * justificación y la aprobación del rol autorizado; los requisitos legales obligatorios no lo admiten.
 */
@Entity
@Table(name = "closing_requirement")
public class ClosingRequirement {

  public enum Section {
    OPERATION,
    PLD,
    PROFECO
  }

  public enum Status {
    PENDING,
    RECEIVED,
    IN_REVIEW,
    VALIDATED,
    NEEDS_CORRECTION,
    NOT_APPLICABLE
  }

  public enum RequestedFrom {
    CLIENT,
    COUNTERPARTY,
    OFFICE,
    THIRD_PARTY,
    INTERNAL
  }

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID operationId;

  private UUID templateId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Section section;

  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  private PartySide partySide;

  @Column(nullable = false)
  private String label;

  private String description;
  private boolean mandatory;
  private Integer validityDays;
  private String documentTypeHint;
  private int sortOrder;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private Status status;

  @Enumerated(EnumType.STRING)
  @Column(length = 24)
  private RequestedFrom requestedFrom;

  private Instant requestedAt;
  private String followUpOwner;
  private LocalDate dueDate;
  private String observation;
  private String reviewedByName;
  private Instant reviewedAt;
  private String naJustification;
  private String naRequestedByName;
  private Instant naRequestedAt;
  private String naApprovedByName;
  private Instant naApprovedAt;

  @Version
  private long version;

  protected ClosingRequirement() {}

  public ClosingRequirement(UUID operationId, RequirementTemplate t, RequestedFrom requestedFrom, Instant now) {
    this.id = UUID.randomUUID();
    this.operationId = operationId;
    this.templateId = t.getId();
    this.section = t.getSection();
    this.partySide = t.getPartySide();
    this.label = t.getLabel();
    this.description = t.getDescription();
    this.mandatory = t.isMandatory();
    this.validityDays = t.getValidityDays();
    this.documentTypeHint = t.getDocumentTypeHint();
    this.sortOrder = t.getSortOrder();
    this.status = Status.PENDING;
    // La solicitud se activa al iniciar el cierre, no al marcar la operación como cerrada.
    this.requestedFrom = requestedFrom;
    this.requestedAt = now;
  }

  public void evidenceReceived() {
    this.status = Status.RECEIVED;
    this.reviewedByName = null;
    this.reviewedAt = null;
    clearNotApplicable();
  }

  public void review(Status status, String observation, String byName, Instant now) {
    this.status = status;
    this.observation = observation;
    this.reviewedByName = byName;
    this.reviewedAt = now;
    if (status != Status.NOT_APPLICABLE) {
      clearNotApplicable();
    }
  }

  public void requestNotApplicable(String justification, String byName, Instant now) {
    this.status = Status.NOT_APPLICABLE;
    this.naJustification = justification;
    this.naRequestedByName = byName;
    this.naRequestedAt = now;
    this.naApprovedByName = null;
    this.naApprovedAt = null;
  }

  public void approveNotApplicable(String byName, Instant now) {
    this.naApprovedByName = byName;
    this.naApprovedAt = now;
    this.reviewedByName = byName;
    this.reviewedAt = now;
  }

  public void rejectNotApplicable(String observation, String byName, Instant now) {
    this.status = Status.PENDING;
    this.observation = observation;
    this.reviewedByName = byName;
    this.reviewedAt = now;
    clearNotApplicable();
  }

  public void followUp(RequestedFrom requestedFrom, String owner, LocalDate dueDate, Instant now) {
    if (requestedFrom != this.requestedFrom) {
      this.requestedAt = now;
    }
    this.requestedFrom = requestedFrom;
    this.followUpOwner = owner;
    this.dueDate = dueDate;
  }

  private void clearNotApplicable() {
    this.naJustification = null;
    this.naRequestedByName = null;
    this.naRequestedAt = null;
    this.naApprovedByName = null;
    this.naApprovedAt = null;
  }

  /** Cuenta como cumplido para autorizar: validado, o "No aplica" justificado y aprobado. */
  public boolean isSatisfied() {
    return status == Status.VALIDATED || (status == Status.NOT_APPLICABLE && naApprovedAt != null && !mandatory);
  }

  public UUID getId() {
    return id;
  }

  public UUID getOperationId() {
    return operationId;
  }

  public UUID getTemplateId() {
    return templateId;
  }

  public Section getSection() {
    return section;
  }

  public PartySide getPartySide() {
    return partySide;
  }

  public String getLabel() {
    return label;
  }

  public String getDescription() {
    return description;
  }

  public boolean isMandatory() {
    return mandatory;
  }

  public Integer getValidityDays() {
    return validityDays;
  }

  public String getDocumentTypeHint() {
    return documentTypeHint;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public Status getStatus() {
    return status;
  }

  public RequestedFrom getRequestedFrom() {
    return requestedFrom;
  }

  public Instant getRequestedAt() {
    return requestedAt;
  }

  public String getFollowUpOwner() {
    return followUpOwner;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public String getObservation() {
    return observation;
  }

  public String getReviewedByName() {
    return reviewedByName;
  }

  public Instant getReviewedAt() {
    return reviewedAt;
  }

  public String getNaJustification() {
    return naJustification;
  }

  public String getNaRequestedByName() {
    return naRequestedByName;
  }

  public Instant getNaRequestedAt() {
    return naRequestedAt;
  }

  public String getNaApprovedByName() {
    return naApprovedByName;
  }

  public Instant getNaApprovedAt() {
    return naApprovedAt;
  }
}
