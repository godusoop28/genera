package com.c21genera.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c21genera.identity.application.AccountLinkService;
import com.c21genera.identity.application.UserService;
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
import com.c21genera.shared.events.UserAccountEvents.UserAccountChanged;
import com.c21genera.shared.staff.StaffWorkload;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

/** Control de Cambios #25: ciclo de vida de cuentas, bloqueo y revocación de sesiones. */
class AccountAdministrationTest {

  private static final Instant NOW = Instant.parse("2026-10-20T18:00:00Z");

  private final Role admin = new Role(RoleCode.ADMINISTRATOR, "Administrador", "");
  private final Role advisorRole = new Role(RoleCode.ADVISOR, "Asesor", "");
  private final UserRepository users = mock(UserRepository.class);
  private final RoleRepository roles = mock(RoleRepository.class);
  private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
  private final AccountLinkService links = mock(AccountLinkService.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final SessionRevocationValidator sessions = new SessionRevocationValidator(users, clock);
  private final List<StaffWorkload.Item> workload = new java.util.ArrayList<>();
  private final UserService service =
      new UserService(users, roles, refreshTokens, mock(PasswordEncoder.class), links, sessions, List.of(id -> workload), events, clock);

  private final User me = new User("Jorge Admin", "jorge@genera.mx", "hash", admin);
  private final CurrentUser actor =
      new CurrentUser(me.getId(), me.getName(), me.getEmail(), "ADMINISTRATOR", List.of("USER_MANAGE", "ROLE_MANAGE", "AUDIT_VIEW"));

  private User stored(User user) {
    when(users.findById(user.getId())).thenReturn(Optional.of(user));
    return user;
  }

  @Test
  void createdAccountIsPendingWithoutPasswordAndGetsAnInviteLink() {
    when(roles.findByCode(RoleCode.ADVISOR)).thenReturn(Optional.of(advisorRole));
    when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
    when(links.issue(any(), eq(PasswordSetupPurpose.INVITE), eq(me.getId())))
        .thenReturn(new AccountLinkService.SetupLink("https://genera/acceso/x", NOW.plus(Duration.ofHours(72)), PasswordSetupPurpose.INVITE));

    UserService.CreatedUser created = service.create(" Ana Asesora ", "ana@genera.mx", RoleCode.ADVISOR, actor);

    assertThat(created.user().getStatus()).isEqualTo(UserStatus.PENDING);
    assertThat(created.user().hasPassword()).isFalse();
    assertThat(created.user().getName()).isEqualTo("Ana Asesora");
    assertThat(created.link().url()).isEqualTo("https://genera/acceso/x");
    verify(events).publishEvent(any(UserAccountChanged.class));
  }

  @Test
  void assigningARoleRequiresRoleManage() {
    CurrentUser withoutRoleManage = new CurrentUser(me.getId(), me.getName(), me.getEmail(), "ADMINISTRATOR", List.of("USER_MANAGE"));
    assertThatThrownBy(() -> service.create("Ana", "ana@genera.mx", RoleCode.ADVISOR, withoutRoleManage)).isInstanceOf(AccessDeniedException.class);

    User ana = stored(new User("Ana", "ana@genera.mx", "hash", advisorRole));
    assertThatThrownBy(() -> service.update(ana.getId(), "Ana", "ana@genera.mx", RoleCode.ADMINISTRATOR, withoutRoleManage))
        .isInstanceOf(AccessDeniedException.class);
    // Sin cambiar el rol sí puede editar los datos.
    service.update(ana.getId(), "Ana María", "ana@genera.mx", RoleCode.ADVISOR, withoutRoleManage);
    assertThat(ana.getName()).isEqualTo("Ana María");
  }

  @Test
  void nobodyChangesTheirOwnRoleOrDeactivatesThemselves() {
    stored(me);
    when(users.countByRole_CodeAndStatus(RoleCode.ADMINISTRATOR, UserStatus.ACTIVE)).thenReturn(2L);

    assertThatThrownBy(() -> service.update(me.getId(), me.getName(), me.getEmail(), RoleCode.ADVISOR, actor)).isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> service.deactivate(me.getId(), actor)).isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> service.offboard(me.getId(), "Dejó la oficina", actor)).isInstanceOf(ConflictException.class);
  }

  @Test
  void theLastActiveAdministratorCannotBeDeactivated() {
    User otherAdmin = stored(new User("Otra Admin", "otra@genera.mx", "hash", admin));
    when(users.countByRole_CodeAndStatus(RoleCode.ADMINISTRATOR, UserStatus.ACTIVE)).thenReturn(1L);

    assertThatThrownBy(() -> service.deactivate(otherAdmin.getId(), actor)).hasMessageContaining("único administrador");
  }

  @Test
  void deactivatingClosesSessionsImmediately() {
    User ana = stored(new User("Ana", "ana@genera.mx", "hash", advisorRole));
    Jwt beforeDeactivation = jwtOf(ana, NOW.minusSeconds(60));
    assertThat(sessions.validate(beforeDeactivation).hasErrors()).isFalse();

    service.deactivate(ana.getId(), actor);

    assertThat(ana.getStatus()).isEqualTo(UserStatus.INACTIVE);
    verify(refreshTokens).revokeAllOf(ana.getId(), NOW);
    verify(links).revokeOpenLinks(ana.getId());
    assertThat(sessions.validate(beforeDeactivation).hasErrors()).isTrue();
  }

  @Test
  void roleChangeInvalidatesOlderAccessTokensWithoutClosingTheSession() {
    User ana = stored(new User("Ana", "ana@genera.mx", "hash", advisorRole));
    when(roles.findByCode(RoleCode.DOCUMENT_REVIEWER)).thenReturn(Optional.of(new Role(RoleCode.DOCUMENT_REVIEWER, "Revisor", "")));

    service.update(ana.getId(), "Ana", "ana@genera.mx", RoleCode.DOCUMENT_REVIEWER, actor);

    assertThat(sessions.validate(jwtOf(ana, NOW.minusSeconds(5))).hasErrors()).isTrue();
    assertThat(sessions.validate(jwtOf(ana, NOW)).hasErrors()).isFalse();
    verify(refreshTokens, never()).revokeAllOf(any(), any());
  }

  @Test
  void offboardingRequiresAReasonAndReassigningPendingWork() {
    User ana = stored(new User("Ana", "ana@genera.mx", "hash", advisorRole));
    workload.add(new StaffWorkload.Item("Expedientes en curso", 3));

    assertThatThrownBy(() -> service.offboard(ana.getId(), "x", actor)).hasMessageContaining("motivo");
    assertThatThrownBy(() -> service.offboard(ana.getId(), "Dejó de colaborar", actor))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("3 expedientes en curso");

    workload.clear();
    service.offboard(ana.getId(), "Dejó de colaborar", actor);

    assertThat(ana.getStatus()).isEqualTo(UserStatus.OFFBOARDED);
    assertThat(ana.getOffboardedAt()).isEqualTo(NOW);
    ArgumentCaptor<UserAccountChanged> event = ArgumentCaptor.forClass(UserAccountChanged.class);
    verify(events).publishEvent(event.capture());
    assertThat(event.getValue().action()).isEqualTo("OFFBOARDED");
    assertThat(event.getValue().summary()).contains("Dejó de colaborar");
    // Una baja es definitiva.
    assertThatThrownBy(() -> service.activate(ana.getId(), actor)).isInstanceOf(ConflictException.class);
    assertThatThrownBy(() -> service.issuePasswordLink(ana.getId(), actor)).isInstanceOf(ConflictException.class);
  }

  @Test
  void accountLocksAfterRepeatedFailuresAndUnlocksWhenTheLockExpires() {
    User ana = new User("Ana", "ana@genera.mx", "hash", advisorRole);
    Duration lock = Duration.ofMinutes(15);
    for (int i = 0; i < 4; i++) {
      assertThat(ana.registerFailedLogin(NOW, 5, lock)).isFalse();
    }
    assertThat(ana.registerFailedLogin(NOW, 5, lock)).isTrue();
    assertThat(ana.isLocked(NOW.plusSeconds(60))).isTrue();
    assertThat(ana.isLocked(NOW.plus(lock))).isFalse();

    // Una contraseña nueva desbloquea y deja inválidos los tokens anteriores.
    ana.changePasswordHash("nuevo", NOW.plusSeconds(120));
    assertThat(ana.isLocked(NOW.plusSeconds(121))).isFalse();
    assertThat(ana.getSessionsValidAfter()).isEqualTo(NOW.plusSeconds(120));
  }

  @Test
  void pendingAccountBecomesActiveWhenItSetsItsPassword() {
    User pending = User.pending("Luis", "luis@genera.mx", advisorRole);
    assertThat(pending.isActive()).isFalse();

    pending.changePasswordHash("hash", NOW);

    assertThat(pending.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(pending.getPasswordChangedAt()).isEqualTo(NOW);
  }

  private static Jwt jwtOf(User user, Instant issuedAt) {
    return Jwt.withTokenValue("t").header("alg", "HS256").subject(user.getId().toString()).issuedAt(issuedAt).expiresAt(issuedAt.plusSeconds(900)).build();
  }
}
