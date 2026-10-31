package com.c21genera.operations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Control de Cambios #32: operación de cierre (compraventa o arrendamiento) de un inmueble de la
 * cartera propia (ligada a su expediente) o de una propiedad externa en cuyo cierre participa C21
 * Genera, sin publicarla ni darla de alta como captación. El estado de la operación y el de su
 * revisión documental son independientes.
 */
@Entity
@Table(name = "closing_operation")
public class ClosingOperation {

  public enum Origin {
    OWN,
    EXTERNAL
  }

  public enum Type {
    SALE,
    RENT
  }

  public enum Side {
    SELLER,
    BUYER,
    BOTH
  }

  public enum Status {
    IN_PROCESS,
    CLOSING_REVIEW,
    CLOSED,
    CANCELLED
  }

  public enum DocStatus {
    PENDING,
    IN_REVIEW,
    COMPLETE_VALIDATED
  }

  @Id
  private UUID id;

  @Column(nullable = false, unique = true, length = 32)
  private String folio;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Origin origin;

  private UUID expedienteId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Type operationType;

  @Column(nullable = false)
  private String propertyDescription;

  private String propertyAddress;
  private String externalSource;
  private String externalOffice;
  private String externalContact;
  private String generaParticipation;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Side representedSide;

  private UUID advisorId;
  private String advisorName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status;

  private LocalDate closedOn;
  private String closedByName;
  private BigDecimal amount;

  @Column(length = 3)
  private String currency;

  private String cancelReason;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private DocStatus docStatus;

  private LocalDate docDueDate;
  private String docFollowUpOwner;
  private String docAuthorizedByName;
  private UUID docAuthorizedById;
  private Instant docAuthorizedAt;

  @Column(nullable = false)
  private UUID createdByUserId;

  private String createdByName;

  @Version
  private long version;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  protected ClosingOperation() {}

  public ClosingOperation(String folio, Origin origin, UUID expedienteId, Type type, Side representedSide, UUID createdByUserId, String createdByName, Instant now) {
    this.id = UUID.randomUUID();
    this.folio = folio;
    this.origin = origin;
    this.expedienteId = expedienteId;
    this.operationType = type;
    this.representedSide = representedSide;
    this.status = Status.IN_PROCESS;
    this.docStatus = DocStatus.PENDING;
    this.createdByUserId = createdByUserId;
    this.createdByName = createdByName;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void describe(
      String propertyDescription,
      String propertyAddress,
      String externalSource,
      String externalOffice,
      String externalContact,
      String generaParticipation,
      Side representedSide,
      UUID advisorId,
      String advisorName,
      Instant now) {
    this.propertyDescription = propertyDescription;
    this.propertyAddress = propertyAddress;
    this.externalSource = externalSource;
    this.externalOffice = externalOffice;
    this.externalContact = externalContact;
    this.generaParticipation = generaParticipation;
    this.representedSide = representedSide;
    this.advisorId = advisorId;
    this.advisorName = advisorName;
    this.updatedAt = now;
  }

  public void recordTerms(BigDecimal amount, String currency, Instant now) {
    this.amount = amount;
    this.currency = currency;
    this.updatedAt = now;
  }

  public void changeStatus(Status status, Instant now) {
    this.status = status;
    this.updatedAt = now;
  }

  public void close(LocalDate closedOn, String closedByName, Instant now) {
    this.status = Status.CLOSED;
    this.closedOn = closedOn;
    this.closedByName = closedByName;
    this.updatedAt = now;
  }

  public void reopen(Instant now) {
    this.status = Status.CLOSING_REVIEW;
    this.closedOn = null;
    this.closedByName = null;
    this.updatedAt = now;
  }

  public void cancel(String reason, Instant now) {
    this.status = Status.CANCELLED;
    this.cancelReason = reason;
    this.updatedAt = now;
  }

  public void followUp(LocalDate dueDate, String owner, Instant now) {
    this.docDueDate = dueDate;
    this.docFollowUpOwner = owner;
    this.updatedAt = now;
  }

  public void docStatus(DocStatus status, Instant now) {
    this.docStatus = status;
    if (status != DocStatus.COMPLETE_VALIDATED) {
      this.docAuthorizedAt = null;
      this.docAuthorizedById = null;
      this.docAuthorizedByName = null;
    }
    this.updatedAt = now;
  }

  public void authorize(UUID userId, String userName, Instant now) {
    this.docStatus = DocStatus.COMPLETE_VALIDATED;
    this.docAuthorizedById = userId;
    this.docAuthorizedByName = userName;
    this.docAuthorizedAt = now;
    this.updatedAt = now;
  }

  public void touch(Instant now) {
    this.updatedAt = now;
  }

  /** "Cerrada — Documentación pendiente": se concretó, pero su revisión no está completa y validada. */
  public boolean isClosedWithPendingDocs() {
    return status == Status.CLOSED && docStatus != DocStatus.COMPLETE_VALIDATED;
  }

  public UUID getId() {
    return id;
  }

  public String getFolio() {
    return folio;
  }

  public Origin getOrigin() {
    return origin;
  }

  public UUID getExpedienteId() {
    return expedienteId;
  }

  public Type getOperationType() {
    return operationType;
  }

  public String getPropertyDescription() {
    return propertyDescription;
  }

  public String getPropertyAddress() {
    return propertyAddress;
  }

  public String getExternalSource() {
    return externalSource;
  }

  public String getExternalOffice() {
    return externalOffice;
  }

  public String getExternalContact() {
    return externalContact;
  }

  public String getGeneraParticipation() {
    return generaParticipation;
  }

  public Side getRepresentedSide() {
    return representedSide;
  }

  public UUID getAdvisorId() {
    return advisorId;
  }

  public String getAdvisorName() {
    return advisorName;
  }

  public Status getStatus() {
    return status;
  }

  public LocalDate getClosedOn() {
    return closedOn;
  }

  public String getClosedByName() {
    return closedByName;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public String getCancelReason() {
    return cancelReason;
  }

  public DocStatus getDocStatus() {
    return docStatus;
  }

  public LocalDate getDocDueDate() {
    return docDueDate;
  }

  public String getDocFollowUpOwner() {
    return docFollowUpOwner;
  }

  public String getDocAuthorizedByName() {
    return docAuthorizedByName;
  }

  public Instant getDocAuthorizedAt() {
    return docAuthorizedAt;
  }

  public UUID getCreatedByUserId() {
    return createdByUserId;
  }

  public String getCreatedByName() {
    return createdByName;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
