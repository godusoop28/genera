package com.c21genera.identity.application;

import com.c21genera.identity.domain.PasswordPolicy;
import com.c21genera.identity.domain.RefreshToken;
import com.c21genera.identity.domain.User;
import com.c21genera.identity.infrastructure.JwtTokenService;
import com.c21genera.identity.infrastructure.RefreshTokenRepository;
import com.c21genera.identity.infrastructure.SessionRevocationValidator;
import com.c21genera.identity.infrastructure.UserRepository;
import com.c21genera.shared.config.AccountProperties;
import com.c21genera.shared.config.JwtProperties;
import com.c21genera.shared.domain.DomainException;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.events.Actor;
import com.c21genera.shared.events.UserAccountEvents.UserAccountChanged;
import com.c21genera.shared.security.OpaqueTokens;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenService jwtTokenService;
  private final JwtProperties jwtProperties;
  private final AccountProperties accountProperties;
  private final SessionRevocationValidator sessions;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenService jwtTokenService,
      JwtProperties jwtProperties,
      AccountProperties accountProperties,
      SessionRevocationValidator sessions,
      ApplicationEventPublisher events,
      Clock clock) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenService = jwtTokenService;
    this.jwtProperties = jwtProperties;
    this.accountProperties = accountProperties;
    this.sessions = sessions;
    this.events = events;
    this.clock = clock;
  }

  /**
   * Control de Cambios #25: tras varios intentos fallidos la cuenta se bloquea un
   * rato. El contador se guarda aunque el login falle (por eso noRollbackFor).
   */
  @Transactional(noRollbackFor = {BadCredentialsException.class, AccountLockedException.class})
  public AuthResult login(String email, String rawPassword) {
    User user =
        userRepository.findByEmailIgnoreCase(email).orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));
    Instant now = clock.instant();
    if (user.isLocked(now)) {
      throw new AccountLockedException();
    }
    if (!user.isActive() || !user.hasPassword() || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
      if (user.isActive() && user.registerFailedLogin(now, accountProperties.maxFailedLogins(), accountProperties.lockDuration())) {
        events.publishEvent(
            new UserAccountChanged(
                user.getId(), user.getName(), "LOCKED",
                "Cuenta bloqueada " + accountProperties.lockDuration().toMinutes() + " minutos por " + accountProperties.maxFailedLogins() + " intentos fallidos de acceso",
                Actor.system()));
        throw new AccountLockedException();
      }
      throw new BadCredentialsException("Credenciales inválidas");
    }
    user.registerSuccessfulLogin(now);
    return issueTokenPair(user);
  }

  /**
   * Control de Cambios #25: la persona cambia su contraseña desde "Mi perfil"
   * escribiendo la actual. Se cierran todas sus sesiones y se devuelve una
   * nueva para el dispositivo desde el que hizo el cambio.
   */
  public AuthResult changeOwnPassword(UUID userId, String currentPassword, String newPassword) {
    User user = userRepository.findById(userId).filter(User::isActive).orElseThrow(() -> new BadCredentialsException("Usuario inactivo"));
    if (currentPassword == null || !user.hasPassword() || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
      throw new UnprocessableException("WRONG_CURRENT_PASSWORD", "La contraseña actual no es correcta.");
    }
    if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
      throw new UnprocessableException("SAME_PASSWORD", "La contraseña nueva debe ser distinta de la actual.");
    }
    PasswordPolicy.validate(newPassword, user.getEmail(), user.getName());
    Instant now = clock.instant();
    user.changePasswordHash(passwordEncoder.encode(newPassword), now);
    refreshTokenRepository.revokeAllOf(user.getId(), now);
    sessions.evict(user.getId());
    events.publishEvent(
        new UserAccountChanged(
            user.getId(), user.getName(), "PASSWORD_CHANGED", "Cambió su contraseña desde su perfil; se cerraron sus demás sesiones",
            Actor.staff(user.getId(), user.getName(), user.getRole().getCode().name())));
    return issueTokenPair(user);
  }

  /** Cuenta bloqueada temporalmente por intentos fallidos (423). */
  public static class AccountLockedException extends DomainException {
    AccountLockedException() {
      super("ACCOUNT_LOCKED", HttpStatus.LOCKED,
          "Por seguridad, la cuenta se bloqueó unos minutos por varios intentos fallidos. Espera e intenta de nuevo, o pide al administrador que la desbloquee.");
    }
  }

  public AuthResult refresh(String rawRefreshToken) {
    String hash = OpaqueTokens.sha256Hex(rawRefreshToken);
    RefreshToken existing =
        refreshTokenRepository
            .findByTokenHash(hash)
            .filter(t -> t.isActive(clock.instant()))
            .orElseThrow(() -> new BadCredentialsException("Refresh token inválido o expirado"));

    User user = userRepository.findById(existing.getUserId()).orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));
    if (!user.isActive()) {
      throw new BadCredentialsException("Usuario inactivo");
    }

    AuthResult next = issueTokenPair(user);
    existing.revoke(next.refreshTokenId(), clock.instant());
    return next;
  }

  public void logout(String rawRefreshToken) {
    String hash = OpaqueTokens.sha256Hex(rawRefreshToken);
    refreshTokenRepository.findByTokenHash(hash).ifPresent(t -> t.revoke(null, clock.instant()));
  }

  private AuthResult issueTokenPair(User user) {
    JwtTokenService.IssuedAccessToken accessToken = jwtTokenService.issueAccessToken(user);

    String rawRefresh = OpaqueTokens.generate();
    Instant now = clock.instant();
    RefreshToken refreshToken =
        new RefreshToken(user.getId(), OpaqueTokens.sha256Hex(rawRefresh), now, now.plus(jwtProperties.refreshTtl()));
    refreshTokenRepository.save(refreshToken);

    return new AuthResult(accessToken.token(), accessToken.expiresAt(), rawRefresh, refreshToken.getId(), user);
  }

  public record AuthResult(
      String accessToken, Instant accessTokenExpiresAt, String refreshToken, java.util.UUID refreshTokenId, User user) {}
}
