package com.c21genera.receipts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Control de Cambios #31: entidad responsable, representante que firma y los
 * textos del acuse. Cualquier cambio a los textos los deja sin aprobar hasta
 * que el área legal los vuelva a aprobar.
 */
@Entity
@Table(name = "receipt_settings")
public class ReceiptSettings {

  @Id
  private short id = 1;

  @Column(nullable = false)
  private String responsibleEntity;

  private String representativeName;

  private String representativeTitle;

  @Column(nullable = false)
  private String responsibilityText;

  @Column(nullable = false)
  private String privacyText;

  @Column(nullable = false)
  private boolean approved;

  private String approvedByName;

  private Instant approvedAt;

  private String updatedByName;

  @Column(nullable = false)
  private Instant updatedAt;

  protected ReceiptSettings() {}

  public void update(String entity, String representativeName, String representativeTitle, String responsibilityText, String privacyText, String byName, Instant now) {
    boolean textsChanged = !this.responsibilityText.equals(responsibilityText.strip()) || !this.privacyText.equals(privacyText.strip()) || !this.responsibleEntity.equals(entity.strip());
    this.responsibleEntity = entity.strip();
    this.representativeName = representativeName == null || representativeName.isBlank() ? null : representativeName.strip();
    this.representativeTitle = representativeTitle == null || representativeTitle.isBlank() ? null : representativeTitle.strip();
    this.responsibilityText = responsibilityText.strip();
    this.privacyText = privacyText.strip();
    if (textsChanged) {
      this.approved = false;
      this.approvedByName = null;
      this.approvedAt = null;
    }
    this.updatedByName = byName;
    this.updatedAt = now;
  }

  public void approve(boolean approved, String byName, Instant now) {
    this.approved = approved;
    this.approvedByName = approved ? byName : null;
    this.approvedAt = approved ? now : null;
  }

  public String getResponsibleEntity() {
    return responsibleEntity;
  }

  public String getRepresentativeName() {
    return representativeName;
  }

  public String getRepresentativeTitle() {
    return representativeTitle;
  }

  public String getResponsibilityText() {
    return responsibilityText;
  }

  public String getPrivacyText() {
    return privacyText;
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

  public String getUpdatedByName() {
    return updatedByName;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
