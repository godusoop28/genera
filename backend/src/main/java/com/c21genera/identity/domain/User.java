package com.c21genera.identity.domain;

import com.c21genera.shared.jpa.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Personal interno de CENTURY 21 Genera. Los clientes/propietarios NUNCA son
 * User (ver AGENTS §13): no tienen cuenta, no aparecen aquí.
 */
@Entity
@Table(name = "app_user")
public class User extends AuditableEntity {

  @Id
  private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false)
  private String passwordHash;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "role_id", nullable = false)
  private Role role;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private UserStatus status;

  private Instant lastActivityAt;

  protected User() {}

  public User(String name, String email, String passwordHash, Role role) {
    this.id = UUID.randomUUID();
    this.name = name;
    this.email = email.toLowerCase();
    this.passwordHash = passwordHash;
    this.role = role;
    this.status = UserStatus.ACTIVE;
  }

  public void rename(String name) {
    this.name = name;
  }

  public void changeEmail(String email) {
    this.email = email.toLowerCase();
  }

  public void changePasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public void changeRole(Role role) {
    this.role = role;
  }

  public void activate() {
    this.status = UserStatus.ACTIVE;
  }

  public void deactivate() {
    this.status = UserStatus.INACTIVE;
  }

  public void touchActivity(Instant when) {
    this.lastActivityAt = when;
  }

  public boolean isActive() {
    return status == UserStatus.ACTIVE;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Role getRole() {
    return role;
  }

  public UserStatus getStatus() {
    return status;
  }

  public Instant getLastActivityAt() {
    return lastActivityAt;
  }
}
