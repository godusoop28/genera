package com.c21genera.identity.application;

import com.c21genera.identity.domain.PasswordPolicy;
import com.c21genera.identity.domain.PasswordSetupPurpose;
import com.c21genera.identity.domain.PasswordSetupToken;
import com.c21genera.identity.domain.User;
import com.c21genera.identity.domain.UserStatus;
import com.c21genera.identity.infrastructure.PasswordSetupTokenRepository;
import com.c21genera.identity.infrastructure.RefreshTokenRepository;
import com.c21genera.identity.infrastructure.SessionRevocationValidator;
import com.c21genera.identity.infrastructure.UserRepository;
import com.c21genera.shared.config.AccountProperties;
import com.c21genera.shared.domain.DomainException;
import com.c21genera.shared.events.Actor;
import com.c21genera.shared.events.UserAccountEvents.UserAccountChanged;
import com.c21genera.shared.security.OpaqueTokens;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Control de Cambios #25: enlaces de un solo uso para que la persona defina su
 * contraseña, sin que nadie más la conozca. Los genera un administrador (alta
 * o "olvidé mi contraseña") y se los comparte por WhatsApp u otro medio; no
 * dependen del envío de correos. Solo se guarda el hash del enlace; generar uno
 * nuevo invalida el anterior, y al usarlo se cierran todas las sesiones.
 */
@Service
@Transactional
public class AccountLinkService {

  private final PasswordSetupTokenRepository tokens;
  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final SessionRevocationValidator sessions;
  private final AccountProperties properties;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  AccountLinkService(
      PasswordSetupTokenRepository tokens,
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      SessionRevocationValidator sessions,
      AccountProperties properties,
      ApplicationEventPublisher events,
      Clock clock) {
    this.tokens = tokens;
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.sessions = sessions;
    this.properties = properties;
    this.events = events;
    this.clock = clock;
  }

  /** El enlace en claro solo existe en esta respuesta; después no se puede volver a consultar. */
  public record SetupLink(String url, Instant expiresAt, PasswordSetupPurpose purpose) {}

  /** Lo que ve la persona al abrir su enlace (sin datos sensibles). */
  public record LinkInfo(String name, String email, PasswordSetupPurpose purpose, Instant expiresAt) {}

  public SetupLink issue(User user, PasswordSetupPurpose purpose, UUID createdByUserId) {
    revokeOpenLinks(user.getId());
    Instant now = clock.instant();
    Instant expiresAt = now.plus(purpose == PasswordSetupPurpose.INVITE ? properties.inviteTtl() : properties.resetTtl());
    String raw = OpaqueTokens.generate();
    tokens.save(new PasswordSetupToken(user.getId(), OpaqueTokens.sha256Hex(raw), purpose, now, expiresAt, createdByUserId));
    return new SetupLink(properties.linkBaseUrl() + "/" + raw, expiresAt, purpose);
  }

  public void revokeOpenLinks(UUID userId) {
    Instant now = clock.instant();
    tokens.findOpenByUserId(userId).forEach(t -> t.revoke(now));
  }

  @Transactional(readOnly = true)
  public LinkInfo describe(String rawToken) {
    PasswordSetupToken token = usableToken(rawToken);
    User user = userOf(token);
    return new LinkInfo(user.getName(), user.getEmail(), token.getPurpose(), token.getExpiresAt());
  }

  /** La persona define su contraseña con el enlace: queda activa, desbloqueada y sin sesiones anteriores. */
  public void complete(String rawToken, String newPassword) {
    PasswordSetupToken token = usableToken(rawToken);
    User user = userOf(token);
    PasswordPolicy.validate(newPassword, user.getEmail(), user.getName());
    Instant now = clock.instant();
    boolean firstTime = !user.hasPassword();
    user.changePasswordHash(passwordEncoder.encode(newPassword), now);
    token.markUsed(now);
    revokeOpenLinks(user.getId());
    refreshTokenRepository.revokeAllOf(user.getId(), now);
    sessions.evict(user.getId());
    events.publishEvent(
        new UserAccountChanged(
            user.getId(),
            user.getName(),
            "PASSWORD_SET",
            firstTime ? "Definió su contraseña con su enlace de alta: la cuenta quedó activa" : "Definió una contraseña nueva con su enlace de restablecimiento; se cerraron sus sesiones",
            Actor.staff(user.getId(), user.getName(), user.getRole().getCode().name())));
  }

  private PasswordSetupToken usableToken(String rawToken) {
    if (rawToken == null || rawToken.isBlank() || rawToken.length() > 200) {
      throw invalidLink();
    }
    return tokens.findByTokenHash(OpaqueTokens.sha256Hex(rawToken)).filter(t -> t.isUsable(clock.instant())).orElseThrow(AccountLinkService::invalidLink);
  }

  private User userOf(PasswordSetupToken token) {
    User user = userRepository.findById(token.getUserId()).orElseThrow(AccountLinkService::invalidLink);
    if (user.getStatus() == UserStatus.INACTIVE || user.getStatus() == UserStatus.OFFBOARDED) {
      throw invalidLink();
    }
    org.hibernate.Hibernate.initialize(user.getRole());
    return user;
  }

  /** Mismo mensaje para enlace inexistente, usado, vencido o de una cuenta desactivada: no revela cuál. */
  private static DomainException invalidLink() {
    return new DomainException(
        "INVALID_ACCOUNT_LINK", HttpStatus.GONE, "Este enlace ya no es válido: venció, ya se usó o se generó uno más nuevo. Pide al administrador un enlace nuevo.") {};
  }
}
