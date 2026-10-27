package com.c21genera.leads.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Control de Cambios #30: inmueble que se le mostró o propuso al prospecto. */
@Entity
@Table(name = "lead_shared_property")
public class LeadSharedProperty {

  @Id
  private UUID id;

  @Column(nullable = false)
  private UUID leadId;

  private Long propertyOnline21Id;

  private String propertyKey;

  private String propertyTitle;

  private String note;

  private String actorName;

  @Column(nullable = false)
  private Instant createdAt;

  protected LeadSharedProperty() {}

  public LeadSharedProperty(UUID leadId, Long online21Id, String key, String title, String note, String actorName, Instant createdAt) {
    this.id = UUID.randomUUID();
    this.leadId = leadId;
    this.propertyOnline21Id = online21Id;
    this.propertyKey = key;
    this.propertyTitle = title;
    this.note = note == null || note.isBlank() ? null : note.strip();
    this.actorName = actorName;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getLeadId() {
    return leadId;
  }

  public Long getPropertyOnline21Id() {
    return propertyOnline21Id;
  }

  public String getPropertyKey() {
    return propertyKey;
  }

  public String getPropertyTitle() {
    return propertyTitle;
  }

  public String getNote() {
    return note;
  }

  public String getActorName() {
    return actorName;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
