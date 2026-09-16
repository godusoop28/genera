package com.c21genera.identity.application;

import com.c21genera.identity.domain.RefreshToken;
import com.c21genera.identity.domain.User;
import com.c21genera.identity.infrastructure.JwtTokenService;
import com.c21genera.identity.infrastructure.RefreshTokenRepository;
import com.c21genera.identity.infrastructure.UserRepository;
import com.c21genera.shared.config.JwtProperties;
import com.c21genera.shared.security.OpaqueTokens;
import java.time.Clock;
import java.time.Instant;
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
  private final Clock clock;

  public AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenService jwtTokenService,
      JwtProperties jwtProperties,
      Clock clock) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenService = jwtTokenService;
    this.jwtProperties = jwtProperties;
    this.clock = clock;
  }

  public AuthResult login(String email, String rawPassword) {
    User user =
        userRepository.findByEmailIgnoreCase(email).orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));
    if (!user.isActive() || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
      throw new BadCredentialsException("Credenciales inválidas");
    }
    user.touchActivity(clock.instant());
    return issueTokenPair(user);
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
