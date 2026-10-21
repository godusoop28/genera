package com.c21genera.identity.application;

import com.c21genera.identity.CurrentUser;
import com.c21genera.identity.application.AccountLinkService.SetupLink;
import com.c21genera.identity.domain.DuplicateEmailException;
import com.c21genera.identity.domain.PasswordSetupPurpose;
import com.c21genera.identity.domain.Role;
import com.c21genera.identity.domain.RoleCode;
import com.c21genera.identity.domain.User;
import com.c21genera.identity.domain.UserStatus;
import com.c21genera.identity.infrastructure.RefreshTokenRepository;
import com.c21genera.identity.infrastructure.RoleRepository;
import com.c21genera.identity.infrastructure.SessionRevocationValidator;
import com.c21genera.identity.infrastructure.UserRepository;
import com.c21genera.shared.domain.ConflictException;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.events.Actor;
import com.c21genera.shared.events.UserAccountEvents.UserAccountChanged;
import com.c21genera.shared.staff.StaffWorkload;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Control de Cambios #25: administración de cuentas (solo quien tiene
 * USER_MANAGE; asignar o cambiar el rol además pide ROLE_MANAGE).
 *
 * <p>Reglas fijas: nadie conoce la contraseña de otro (el alta y el
 * restablecimiento generan un enlace de un solo uso); nadie se cambia el rol,
 * se desactiva ni se da de baja a sí mismo; nunca se queda el sistema sin un
 * administrador activo; dar de baja es definitivo, no borra nada y pide
 * reasignar antes lo que la persona tenga a su cargo. Cada acción queda en la
 * bitácora (quién, a qué cuenta y cuándo), nunca con contraseñas.
 */
@Service
@Transactional
public class UserService {

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final AccountLinkService accountLinks;
  private final SessionRevocationValidator sessions;
  private final List<StaffWorkload> workloads;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public UserService(
      UserRepository userRepository,
      RoleRepository roleRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      AccountLinkService accountLinks,
      SessionRevocationValidator sessions,
      List<StaffWorkload> workloads,
      ApplicationEventPublisher events,
      Clock clock) {
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.accountLinks = accountLinks;
    this.sessions = sessions;
    this.workloads = workloads;
    this.events = events;
    this.clock = clock;
  }

  /** Cuenta recién creada con el enlace para que la persona defina su contraseña. */
  public record CreatedUser(User user, SetupLink link) {}

  public CreatedUser create(String name, String email, RoleCode roleCode, CurrentUser actor) {
    requireRoleManage(actor);
    String cleanEmail = email.strip();
    if (userRepository.existsByEmailIgnoreCase(cleanEmail)) {
      throw new DuplicateEmailException(cleanEmail);
    }
    User user = userRepository.save(User.pending(name.strip(), cleanEmail, findRole(roleCode)));
    SetupLink link = accountLinks.issue(user, PasswordSetupPurpose.INVITE, actor.id());
    publish(user, "CREATED", "Alta de la cuenta con rol " + user.getRole().getName() + " (pendiente de que defina su contraseña)", actor);
    return new CreatedUser(user, link);
  }

  /** Solo para el administrador de demostración del perfil local (ver DevAdminSeeder). */
  public User createWithPassword(String name, String email, String rawPassword, RoleCode roleCode) {
    if (userRepository.existsByEmailIgnoreCase(email)) {
      throw new DuplicateEmailException(email);
    }
    return userRepository.save(new User(name, email, passwordEncoder.encode(rawPassword), findRole(roleCode)));
  }

  public User update(UUID userId, String name, String email, RoleCode roleCode, CurrentUser actor) {
    User user = get(userId);
    requireNotOffboarded(user);
    String cleanEmail = email.strip();
    if (!user.getEmail().equalsIgnoreCase(cleanEmail) && userRepository.existsByEmailIgnoreCase(cleanEmail)) {
      throw new DuplicateEmailException(cleanEmail);
    }
    List<String> changes = new ArrayList<>();
    if (!user.getName().equals(name.strip())) {
      changes.add("nombre");
    }
    if (!user.getEmail().equalsIgnoreCase(cleanEmail)) {
      changes.add("correo");
    }
    user.rename(name.strip());
    user.changeEmail(cleanEmail);
    if (!changes.isEmpty()) {
      publish(user, "UPDATED", "Datos de la cuenta actualizados (" + String.join(", ", changes) + ")", actor);
    }

    if (user.getRole().getCode() != roleCode) {
      requireRoleManage(actor);
      if (user.getId().equals(actor.id())) {
        throw new ConflictException("SELF_ROLE_CHANGE", "No puedes cambiar tu propio rol. Pídeselo a otro administrador.");
      }
      if (user.getRole().getCode() == RoleCode.ADMINISTRATOR) {
        requireAnotherActiveAdmin(user);
      }
      String previous = user.getRole().getName();
      user.changeRole(findRole(roleCode));
      // Los permisos viajan en el token: se invalida para que el cambio surta efecto ya
      // (el front renueva solo y recibe los permisos nuevos; no se cierra la sesión).
      user.revokeSessions(clock.instant());
      sessions.evict(user.getId());
      publish(user, "ROLE_CHANGED", "Rol cambiado de " + previous + " a " + user.getRole().getName(), actor);
    }
    return user;
  }

  public User activate(UUID userId, CurrentUser actor) {
    User user = get(userId);
    requireNotOffboarded(user);
    if (user.getStatus() != UserStatus.INACTIVE) {
      return user;
    }
    user.activate();
    sessions.evict(user.getId());
    publish(user, "ACTIVATED", user.hasPassword() ? "Cuenta reactivada" : "Cuenta reactivada (sigue pendiente de definir su contraseña)", actor);
    return user;
  }

  public User deactivate(UUID userId, CurrentUser actor) {
    User user = get(userId);
    requireNotOffboarded(user);
    requireNotSelf(user, actor, "desactivar tu propia cuenta");
    if (user.getStatus() == UserStatus.INACTIVE) {
      return user;
    }
    if (user.getRole().getCode() == RoleCode.ADMINISTRATOR) {
      requireAnotherActiveAdmin(user);
    }
    Instant now = clock.instant();
    user.deactivate(now);
    closeSessions(user, now);
    accountLinks.revokeOpenLinks(user.getId());
    publish(user, "DEACTIVATED", "Cuenta desactivada: se cerraron sus sesiones abiertas", actor);
    return user;
  }

  /** Baja definitiva. reason es obligatorio y queda en la bitácora. */
  public User offboard(UUID userId, String reason, CurrentUser actor) {
    User user = get(userId);
    requireNotOffboarded(user);
    requireNotSelf(user, actor, "darte de baja a ti mismo");
    String cleanReason = reason == null ? "" : reason.strip();
    if (cleanReason.length() < 5) {
      throw new UnprocessableException("OFFBOARD_REASON_REQUIRED", "Escribe el motivo de la baja (p. ej. \"Dejó de colaborar con la oficina\").");
    }
    if (user.getRole().getCode() == RoleCode.ADMINISTRATOR) {
      requireAnotherActiveAdmin(user);
    }
    List<StaffWorkload.Item> pending = workloadOf(user.getId());
    if (!pending.isEmpty()) {
      String detail = String.join(", ", pending.stream().map(i -> i.count() + " " + i.label().toLowerCase()).toList());
      throw new ConflictException(
          "OFFBOARD_HAS_ASSIGNMENTS", user.getName() + " todavía tiene a su cargo: " + detail + ". Reasígnalos a otra persona antes de darle de baja.");
    }
    Instant now = clock.instant();
    user.offboard(now);
    closeSessions(user, now);
    accountLinks.revokeOpenLinks(user.getId());
    publish(user, "OFFBOARDED", "Cuenta dada de baja. Motivo: " + cleanReason, actor);
    return user;
  }

  /** El administrador genera un enlace para que la persona defina una contraseña nueva. Nunca ve ni escribe la contraseña. */
  public SetupLink issuePasswordLink(UUID userId, CurrentUser actor) {
    User user = get(userId);
    requireNotOffboarded(user);
    if (user.getStatus() == UserStatus.INACTIVE) {
      throw new ConflictException("USER_INACTIVE", "La cuenta está desactivada. Reactívala antes de generar un enlace de contraseña.");
    }
    boolean invite = user.getStatus() == UserStatus.PENDING;
    SetupLink link = accountLinks.issue(user, invite ? PasswordSetupPurpose.INVITE : PasswordSetupPurpose.RESET, actor.id());
    publish(
        user,
        invite ? "INVITE_LINK_ISSUED" : "RESET_LINK_ISSUED",
        invite ? "Generó un nuevo enlace de alta (el anterior dejó de funcionar)" : "Generó un enlace de restablecimiento de contraseña (la contraseña actual sigue valiendo hasta que se use)",
        actor);
    return link;
  }

  public User unlock(UUID userId, CurrentUser actor) {
    User user = get(userId);
    if (!user.isLocked(clock.instant())) {
      return user;
    }
    user.unlock();
    publish(user, "UNLOCKED", "Desbloqueó la cuenta (estaba bloqueada por intentos fallidos)", actor);
    return user;
  }

  /** Lo que la persona tiene a su cargo (para avisar antes de desactivar y exigir reasignar antes de la baja). */
  @Transactional(readOnly = true)
  public List<StaffWorkload.Item> workloadOf(UUID userId) {
    return workloads.stream().flatMap(w -> w.assignedTo(userId).stream()).filter(i -> i.count() > 0).toList();
  }

  @Transactional(readOnly = true)
  public User get(UUID userId) {
    User user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("Usuario", userId));
    // role es LAZY y open-in-view está desactivado: los controllers mapean a
    // UserResponse (que lee user.getRole()) después de que esta transacción
    // ya cerró, así que hay que inicializarlo aquí o revienta con
    // LazyInitializationException.
    org.hibernate.Hibernate.initialize(user.getRole());
    return user;
  }

  @Transactional(readOnly = true)
  public Page<User> list(Pageable pageable) {
    return userRepository.findAll(pageable);
  }

  private void closeSessions(User user, Instant now) {
    refreshTokenRepository.revokeAllOf(user.getId(), now);
    sessions.evict(user.getId());
  }

  private void requireRoleManage(CurrentUser actor) {
    if (!actor.hasPermission("ROLE_MANAGE")) {
      throw new AccessDeniedException("Asignar o cambiar roles requiere ROLE_MANAGE");
    }
  }

  private static void requireNotSelf(User user, CurrentUser actor, String what) {
    if (user.getId().equals(actor.id())) {
      throw new ConflictException("SELF_ACCOUNT_ACTION", "No puedes " + what + ". Pídeselo a otro administrador.");
    }
  }

  private static void requireNotOffboarded(User user) {
    if (user.getStatus() == UserStatus.OFFBOARDED) {
      throw new ConflictException("USER_OFFBOARDED", "La cuenta de " + user.getName() + " está dada de baja; ya no se puede modificar.");
    }
  }

  private void requireAnotherActiveAdmin(User user) {
    long activeAdmins = userRepository.countByRole_CodeAndStatus(RoleCode.ADMINISTRATOR, UserStatus.ACTIVE);
    boolean userCounts = user.getStatus() == UserStatus.ACTIVE;
    if (activeAdmins - (userCounts ? 1 : 0) < 1) {
      throw new ConflictException("LAST_ADMIN", "Es el único administrador activo. Primero da el rol de administrador a otra persona.");
    }
  }

  private Role findRole(RoleCode code) {
    return roleRepository.findByCode(code).orElseThrow(() -> new NotFoundException("Rol", code));
  }

  private void publish(User user, String action, String summary, CurrentUser actor) {
    events.publishEvent(new UserAccountChanged(user.getId(), user.getName(), action, summary, actor != null ? actor.toActor() : Actor.system()));
  }
}
