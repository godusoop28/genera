package com.c21genera.expedientes.domain;

import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.shared.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "expediente")
public class Expediente extends AuditableEntity {

  @Id
  private UUID id;

  @Column(nullable = false, unique = true, length = 32)
  private String folio;

  @Column(nullable = false)
  private String ownerDisplayName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ExpedienteStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private PersonType personType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private SignerCharacter signerCharacter;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AccreditationType accreditationType;

  @Column(nullable = false)
  private boolean condominiumRegime;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private PropertyCaseType propertyCaseType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private PropertyLegalStatus declaredLegalStatus;

  private String propertyAddress;

  private String decisionReason;

  private UUID decidedByUserId;

  private Instant decidedAt;

  @Column(nullable = false)
  private UUID createdByUserId;

  protected Expediente() {}

  public Expediente(
      String folio,
      String ownerDisplayName,
      PersonType personType,
      SignerCharacter signerCharacter,
      AccreditationType accreditationType,
      boolean condominiumRegime,
      PropertyCaseType propertyCaseType,
      PropertyLegalStatus declaredLegalStatus,
      String propertyAddress,
      UUID createdByUserId) {
    this.id = UUID.randomUUID();
    this.folio = folio;
    this.ownerDisplayName = ownerDisplayName;
    this.status = ExpedienteStatus.DRAFT;
    this.personType = personType;
    this.signerCharacter = signerCharacter;
    this.accreditationType = accreditationType;
    this.condominiumRegime = condominiumRegime;
    this.propertyCaseType = propertyCaseType;
    this.declaredLegalStatus = declaredLegalStatus;
    this.propertyAddress = propertyAddress;
    this.createdByUserId = createdByUserId;
  }

  public void transitionTo(ExpedienteStatus target) {
    ExpedienteStateMachine.ensureAllowed(this.status, target);
    this.status = target;
  }

  /** Transición idempotente: si ya está en el estado objetivo o más adelante, no hace nada. */
  public boolean transitionToIfAllowed(ExpedienteStatus target) {
    if (this.status == target) {
      return false;
    }
    if (!ExpedienteStateMachine.isAllowed(this.status, target)) {
      return false;
    }
    this.status = target;
    return true;
  }

  public void acceptProperty(UUID decidedByUserId, Instant when) {
    ExpedienteStateMachine.ensureAllowed(this.status, ExpedienteStatus.PROPERTY_ACCEPTED);
    this.status = ExpedienteStatus.PROPERTY_ACCEPTED;
    this.decidedByUserId = decidedByUserId;
    this.decidedAt = when;
  }

  public void rejectProperty(UUID decidedByUserId, String reason, Instant when) {
    ExpedienteStateMachine.ensureAllowed(this.status, ExpedienteStatus.PROPERTY_REJECTED);
    this.status = ExpedienteStatus.PROPERTY_REJECTED;
    this.decisionReason = reason;
    this.decidedByUserId = decidedByUserId;
    this.decidedAt = when;
  }

  public void close() {
    ExpedienteStateMachine.ensureAllowed(this.status, ExpedienteStatus.CLOSED);
    this.status = ExpedienteStatus.CLOSED;
  }

  public String getDecisionReason() {
    return decisionReason;
  }

  public UUID getDecidedByUserId() {
    return decidedByUserId;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public UUID getId() {
    return id;
  }

  public String getFolio() {
    return folio;
  }

  public String getOwnerDisplayName() {
    return ownerDisplayName;
  }

  public ExpedienteStatus getStatus() {
    return status;
  }

  public PersonType getPersonType() {
    return personType;
  }

  public SignerCharacter getSignerCharacter() {
    return signerCharacter;
  }

  public AccreditationType getAccreditationType() {
    return accreditationType;
  }

  public boolean isCondominiumRegime() {
    return condominiumRegime;
  }

  public PropertyCaseType getPropertyCaseType() {
    return propertyCaseType;
  }

  public PropertyLegalStatus getDeclaredLegalStatus() {
    return declaredLegalStatus;
  }

  public String getPropertyAddress() {
    return propertyAddress;
  }

  public UUID getCreatedByUserId() {
    return createdByUserId;
  }
}
