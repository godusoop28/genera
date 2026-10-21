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

  /** null mientras la cuenta está PENDING (aún no define su contraseña). */
  private String passwordHash;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "role_id", nullable = false)
  private Role role;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private UserStatus status;

  private Instant lastActivityAt;

  @Column(nullable = false)
  private int failedLoginAttempts;

  private Instant lockedUntil;

  /** Los access tokens emitidos antes de esta fecha ya no valen (ver SessionRevocation). */
  private Instant sessionsValidAfter;

  private Instant passwordChangedAt;

  private Instant offboardedAt;

  protected User() {}

  public User(String name, String email, String passwordHash, Role role) {
    this.id = UUID.randomUUID();
    this.name = name;
    this.email = email.toLowerCase();
    this.passwordHash = passwordHash;
    this.role = role;
    this.status = UserStatus.ACTIVE;
  }

  /** Control de Cambios #25: alta sin contraseña; la persona la define con su enlace. */
  public static User pending(String name, String email, Role role) {
    User user = new User(name, email, null, role);
    user.status = UserStatus.PENDING;
    return user;
  }

  public void rename(String name) {
    this.name = name;
  }

  public void changeEmail(String email) {
    this.email = email.toLowerCase();
  }

  /**
   * Nueva contraseña (cambio propio o con enlace): desbloquea la cuenta y
   * revoca las sesiones abiertas. Una cuenta PENDING queda activa.
   */
  public void changePasswordHash(String passwordHash, Instant when) {
    this.passwordHash = passwordHash;
    this.passwordChangedAt = when;
    this.failedLoginAttempts = 0;
    this.lockedUntil = null;
    if (status == UserStatus.PENDING) {
      status = UserStatus.ACTIVE;
    }
    revokeSessions(when);
  }

  public boolean hasPassword() {
    return passwordHash != null;
  }

  public void changeRole(Role role) {
    this.role = role;
  }

  /** Reactiva una cuenta desactivada; si nunca definió contraseña vuelve a PENDING. */
  public void activate() {
    if (status == UserStatus.OFFBOARDED) {
      throw new IllegalStateException("Una cuenta dada de baja no se puede reactivar");
    }
    this.status = hasPassword() ? UserStatus.ACTIVE : UserStatus.PENDING;
    this.failedLoginAttempts = 0;
    this.lockedUntil = null;
  }

  public void deactivate(Instant when) {
    this.status = UserStatus.INACTIVE;
    revokeSessions(when);
  }

  /** Baja definitiva: no se borra nada, solo deja de poder entrar. */
  public void offboard(Instant when) {
    this.status = UserStatus.OFFBOARDED;
    this.offboardedAt = when;
    this.lockedUntil = null;
    revokeSessions(when);
  }

  /** Los access tokens emitidos antes de este momento dejan de valer. */
  public void revokeSessions(Instant when) {
    this.sessionsValidAfter = when.truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
  }

  public boolean isLocked(Instant now) {
    return lockedUntil != null && now.isBefore(lockedUntil);
  }

  /** Registra un intento fallido; devuelve true si con este la cuenta quedó bloqueada. */
  public boolean registerFailedLogin(Instant now, int maxAttempts, java.time.Duration lockDuration) {
    if (lockedUntil != null && !now.isBefore(lockedUntil)) {
      // El bloqueo anterior ya venció: se empieza a contar de nuevo.
      lockedUntil = null;
      failedLoginAttempts = 0;
    }
    failedLoginAttempts++;
    if (failedLoginAttempts >= maxAttempts) {
      lockedUntil = now.plus(lockDuration);
      failedLoginAttempts = 0;
      return true;
    }
    return false;
  }

  public void registerSuccessfulLogin(Instant now) {
    this.failedLoginAttempts = 0;
    this.lockedUntil = null;
    this.lastActivityAt = now;
  }

  public void unlock() {
    this.failedLoginAttempts = 0;
    this.lockedUntil = null;
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

  public Instant getLockedUntil() {
    return lockedUntil;
  }

  public Instant getSessionsValidAfter() {
    return sessionsValidAfter;
  }

  public Instant getPasswordChangedAt() {
    return passwordChangedAt;
  }

  public Instant getOffboardedAt() {
    return offboardedAt;
  }
}
