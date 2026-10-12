package com.c21genera.leads.domain;

import com.c21genera.leads.domain.AssignmentEngine.Mode;
import com.c21genera.shared.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Política de asignación de la oficina (una sola fila). */
@Entity
@Table(name = "crm_assignment_settings")
public class AssignmentSettings extends AuditableEntity {

  /** Qué hacer si el asesor de la propiedad está fuera de horario o ausente. */
  public enum OwnerOffShiftPolicy {
    /** Conservar la atención para él y dejarla pendiente de su próximo turno (predeterminado). */
    RESPECT_OWNER,
    /** Asignar la atención a un asesor disponible (de guardia), conservando la referencia del dueño. */
    ROUTE_TO_GUARD
  }

  @Id
  private short id = 1;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Mode roundMode = Mode.EQUAL;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OwnerOffShiftPolicy ownerOffShiftPolicy = OwnerOffShiftPolicy.RESPECT_OWNER;

  private String updatedByName;

  protected AssignmentSettings() {}

  public void update(Mode roundMode, OwnerOffShiftPolicy policy, String byName) {
    this.roundMode = roundMode;
    this.ownerOffShiftPolicy = policy;
    this.updatedByName = byName;
  }

  public Mode getRoundMode() {
    return roundMode;
  }

  public OwnerOffShiftPolicy getOwnerOffShiftPolicy() {
    return ownerOffShiftPolicy;
  }

  public String getUpdatedByName() {
    return updatedByName;
  }
}
