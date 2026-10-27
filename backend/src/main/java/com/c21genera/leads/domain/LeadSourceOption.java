package com.c21genera.leads.domain;

import com.c21genera.leads.LeadSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Control de Cambios #29: una fuente del catálogo "¿De dónde viene el lead?".
 * El administrador la activa o desactiva; desactivarla nunca toca los leads
 * que ya la usaron.
 */
@Entity
@Table(name = "lead_source_option")
public class LeadSourceOption {

  public enum Category {
    PORTALS,
    SOCIAL,
    SEARCH,
    OUTDOOR,
    REFERRALS,
    IN_PERSON,
    REACTIVATION,
    UNKNOWN
  }

  @Id
  private String code;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Category category;

  @Column(nullable = false)
  private String label;

  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private LeadSource channel;

  @Column(nullable = false)
  private boolean active;

  @Column(nullable = false)
  private boolean requiresDetail;

  @Column(nullable = false)
  private boolean system;

  @Column(nullable = false)
  private int sortOrder;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  protected LeadSourceOption() {}

  public LeadSourceOption(String code, Category category, String label, String description, LeadSource channel, boolean requiresDetail, int sortOrder, Instant now) {
    this.code = code;
    this.category = category;
    this.label = label;
    this.description = description;
    this.channel = channel;
    this.active = true;
    this.requiresDetail = requiresDetail;
    this.system = false;
    this.sortOrder = sortOrder;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void update(String label, String description, boolean active, boolean requiresDetail, Instant now) {
    if (label != null && !label.isBlank()) {
      this.label = label.strip();
    }
    this.description = description == null || description.isBlank() ? null : description.strip();
    this.active = active;
    this.requiresDetail = requiresDetail;
    this.updatedAt = now;
  }

  public String getCode() {
    return code;
  }

  public Category getCategory() {
    return category;
  }

  public String getLabel() {
    return label;
  }

  public String getDescription() {
    return description;
  }

  public LeadSource getChannel() {
    return channel;
  }

  public boolean isActive() {
    return active;
  }

  public boolean isRequiresDetail() {
    return requiresDetail;
  }

  public boolean isSystem() {
    return system;
  }

  public int getSortOrder() {
    return sortOrder;
  }
}
