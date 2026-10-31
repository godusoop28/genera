package com.c21genera.operations.domain;

import com.c21genera.operations.domain.ClosingOperation.Origin;
import com.c21genera.operations.domain.ClosingOperation.Type;
import com.c21genera.operations.domain.ClosingRequirement.Section;
import com.c21genera.operations.domain.OperationParty.PartySide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * CC#32: renglón de la matriz documental de cierre. Aplica según la sección (operación, PLD o
 * PROFECO), la parte (vendedor, comprador o la operación), el tipo de operación y el origen
 * (null = todos). mandatory = obligación legal: no admite "No aplica".
 */
@Entity
@Table(name = "closing_requirement_template")
public class RequirementTemplate {

  @Id
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private Section section;

  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  private PartySide partySide;

  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  private Type operationType;

  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  private Origin origin;

  @Column(nullable = false)
  private String label;

  private String description;
  private boolean mandatory;
  private Integer validityDays;
  private String documentTypeHint;
  private int sortOrder;
  private boolean active;

  protected RequirementTemplate() {}

  public RequirementTemplate(UUID id) {
    this.id = id;
    this.active = true;
  }

  public void update(
      Section section,
      PartySide partySide,
      Type operationType,
      Origin origin,
      String label,
      String description,
      boolean mandatory,
      Integer validityDays,
      String documentTypeHint,
      int sortOrder,
      boolean active) {
    this.section = section;
    this.partySide = partySide;
    this.operationType = operationType;
    this.origin = origin;
    this.label = label;
    this.description = description;
    this.mandatory = mandatory;
    this.validityDays = validityDays;
    this.documentTypeHint = documentTypeHint;
    this.sortOrder = sortOrder;
    this.active = active;
  }

  public boolean appliesTo(ClosingOperation operation) {
    return active
        && (operationType == null || operationType == operation.getOperationType())
        && (origin == null || origin == operation.getOrigin());
  }

  public UUID getId() {
    return id;
  }

  public Section getSection() {
    return section;
  }

  public PartySide getPartySide() {
    return partySide;
  }

  public Type getOperationType() {
    return operationType;
  }

  public Origin getOrigin() {
    return origin;
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

  public boolean isActive() {
    return active;
  }
}
