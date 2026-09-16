package com.c21genera.identity.domain;

import com.c21genera.shared.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "app_role")
public class Role extends AuditableEntity {

  @Id
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, unique = true, length = 32)
  private RoleCode code;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String description;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "role_permission",
      joinColumns = @JoinColumn(name = "role_id"),
      inverseJoinColumns = @JoinColumn(name = "permission_code"))
  private Set<Permission> permissions = new HashSet<>();

  protected Role() {}

  public Role(RoleCode code, String name, String description) {
    this.id = UUID.randomUUID();
    this.code = code;
    this.name = name;
    this.description = description;
  }

  public void grant(Permission permission) {
    this.permissions.add(permission);
  }

  public UUID getId() {
    return id;
  }

  public RoleCode getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public Set<Permission> getPermissions() {
    return Set.copyOf(permissions);
  }
}
