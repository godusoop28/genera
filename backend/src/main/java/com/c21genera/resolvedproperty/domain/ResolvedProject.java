package com.c21genera.resolvedproperty.domain;

import com.c21genera.shared.domain.ConflictException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Control de Cambios #42: proyecto de Propiedad Resuelta. Se prepara y revisa aquí; solo un
 * proyecto aprobado puede incorporarse al flujo de comercialización de CENTURY 21 Genera, y
 * aprobarlo no lo incorpora ni lo publica por sí solo.
 */
@Entity
@Table(name = "resolved_project")
public class ResolvedProject {

  public static final String PENDING = "PENDING";
  public static final String APPROVED = "APPROVED";
  public static final String NOT_INCORPORATED = "NOT_INCORPORATED";
  public static final String INCORPORATED = "INCORPORATED";

  @Id
  private UUID id;

  @Column(nullable = false)
  private String name;

  private String description;
  private String location;
  private BigDecimal estimatedInvestment;
  private BigDecimal estimatedValue;

  @Column(nullable = false)
  private String approvalStatus = PENDING;

  private UUID approvedByUserId;
  private String approvedByName;
  private Instant approvedAt;
  private String approvalNote;

  @Column(nullable = false)
  private String commercializationStatus = NOT_INCORPORATED;

  private String incorporatedByName;
  private Instant incorporatedAt;
  private String incorporationNote;
  private String linkedReference;

  private UUID createdByUserId;
  private String createdByName;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  private Instant archivedAt;

  protected ResolvedProject() {}

  public ResolvedProject(String name, UUID createdByUserId, String createdByName, Instant now) {
    this.id = UUID.randomUUID();
    this.name = name;
    this.createdByUserId = createdByUserId;
    this.createdByName = createdByName;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void update(String name, String description, String location, BigDecimal estimatedInvestment, BigDecimal estimatedValue, Instant now) {
    this.name = name;
    this.description = description;
    this.location = location;
    this.estimatedInvestment = estimatedInvestment;
    this.estimatedValue = estimatedValue;
    this.updatedAt = now;
  }

  public void approve(UUID userId, String userName, String note, Instant now) {
    if (APPROVED.equals(approvalStatus)) {
      throw new ConflictException("PROJECT_ALREADY_APPROVED", "El proyecto ya está aprobado para comercialización.");
    }
    this.approvalStatus = APPROVED;
    this.approvedByUserId = userId;
    this.approvedByName = userName;
    this.approvedAt = now;
    this.approvalNote = note;
    this.updatedAt = now;
  }

  /** Vuelve a «pendiente de aprobación» (p. ej. se aprobó por error). No si ya se incorporó. */
  public void withdrawApproval(Instant now) {
    if (INCORPORATED.equals(commercializationStatus)) {
      throw new ConflictException("PROJECT_ALREADY_INCORPORATED", "El proyecto ya se incorporó a comercialización; su aprobación no se puede retirar aquí.");
    }
    this.approvalStatus = PENDING;
    this.approvedByUserId = null;
    this.approvedByName = null;
    this.approvedAt = null;
    this.approvalNote = null;
    this.updatedAt = now;
  }

  public void incorporate(String userName, String note, String linkedReference, Instant now) {
    if (!APPROVED.equals(approvalStatus)) {
      throw new ConflictException(
          "PROJECT_NOT_APPROVED", "Un proyecto pendiente de aprobación no puede pasar al flujo de comercialización de CENTURY 21 Genera.");
    }
    if (INCORPORATED.equals(commercializationStatus)) {
      throw new ConflictException("PROJECT_ALREADY_INCORPORATED", "El proyecto ya se incorporó a comercialización.");
    }
    this.commercializationStatus = INCORPORATED;
    this.incorporatedByName = userName;
    this.incorporatedAt = now;
    this.incorporationNote = note;
    this.linkedReference = linkedReference;
    this.updatedAt = now;
  }

  public void archive(Instant now) {
    this.archivedAt = now;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getLocation() {
    return location;
  }

  public BigDecimal getEstimatedInvestment() {
    return estimatedInvestment;
  }

  public BigDecimal getEstimatedValue() {
    return estimatedValue;
  }

  public String getApprovalStatus() {
    return approvalStatus;
  }

  public String getApprovedByName() {
    return approvedByName;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public String getApprovalNote() {
    return approvalNote;
  }

  public String getCommercializationStatus() {
    return commercializationStatus;
  }

  public String getIncorporatedByName() {
    return incorporatedByName;
  }

  public Instant getIncorporatedAt() {
    return incorporatedAt;
  }

  public String getIncorporationNote() {
    return incorporationNote;
  }

  public String getLinkedReference() {
    return linkedReference;
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

  public Instant getArchivedAt() {
    return archivedAt;
  }
}
