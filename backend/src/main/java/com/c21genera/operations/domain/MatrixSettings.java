package com.c21genera.operations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * CC#32: aprobación de la matriz documental por el área legal o responsable de cumplimiento.
 * Cualquier cambio a la matriz la deja sin aprobar (y sube la revisión) hasta que se vuelva a aprobar.
 */
@Entity
@Table(name = "closing_matrix_settings")
public class MatrixSettings {

  @Id
  private short id = 1;

  private boolean approved;
  private String approvedByName;
  private Instant approvedAt;
  private int revision;
  private String updatedByName;

  @Column(nullable = false)
  private Instant updatedAt;

  protected MatrixSettings() {}

  public void changed(String byName, Instant now) {
    if (approved) {
      revision++;
    }
    approved = false;
    approvedByName = null;
    approvedAt = null;
    updatedByName = byName;
    updatedAt = now;
  }

  public void approve(boolean approve, String byName, Instant now) {
    this.approved = approve;
    this.approvedByName = approve ? byName : null;
    this.approvedAt = approve ? now : null;
    this.updatedByName = byName;
    this.updatedAt = now;
  }

  public boolean isApproved() {
    return approved;
  }

  public String getApprovedByName() {
    return approvedByName;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public int getRevision() {
    return revision;
  }

  public String getUpdatedByName() {
    return updatedByName;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
