package com.c21genera.propertycatalog.domain;

import com.c21genera.shared.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Categoría del catálogo de inmuebles (p. ej. "Habitacional"). Nunca se borra: se desactiva. */
@Entity
@Table(name = "property_category")
public class PropertyCategory extends AuditableEntity {

  @Id
  private UUID id;

  @Column(nullable = false, length = 80)
  private String name;

  @Column(nullable = false)
  private int sortOrder;

  @Column(nullable = false)
  private boolean active;

  protected PropertyCategory() {}

  public PropertyCategory(String name, int sortOrder) {
    this.id = UUID.randomUUID();
    this.name = name;
    this.sortOrder = sortOrder;
    this.active = true;
  }

  public void update(String name, int sortOrder, boolean active) {
    this.name = name;
    this.sortOrder = sortOrder;
    this.active = active;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public boolean isActive() {
    return active;
  }
}
