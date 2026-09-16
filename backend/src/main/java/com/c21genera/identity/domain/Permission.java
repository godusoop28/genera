package com.c21genera.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Catálogo de permisos persistido (para el endpoint GET /permissions). Clave natural: code. */
@Entity
@Table(name = "permission")
public class Permission {

  @Id
  @Column(length = 64, nullable = false)
  private String code;

  @Column(nullable = false)
  private String description;

  protected Permission() {}

  public Permission(PermissionCode code, String description) {
    this.code = code.name();
    this.description = description;
  }

  public String getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }
}
