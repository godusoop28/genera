package com.c21genera.identity.infrastructure;

import com.c21genera.identity.domain.UserStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Control de Cambios #25: el access token (JWT, 15 min) no se puede "borrar",
 * así que en cada petición se verifica que la cuenta siga activa y que el
 * token sea posterior a la última revocación (desactivar, dar de baja, cambiar
 * contraseña o rol). Así esas acciones surten efecto de inmediato. Con un 401
 * el front intenta renovar: si la cuenta sigue activa (p. ej. solo cambió su
 * rol) recibe un token nuevo con sus permisos actuales; si no, la sesión se cierra.
 *
 * <p>El estado se cachea un momento para no consultar la base en cada
 * petición, y se invalida en cuanto cambia (el backend corre en una sola instancia).
 */
@Component
public class SessionRevocationValidator implements OAuth2TokenValidator<Jwt> {

  private static final Duration CACHE_TTL = Duration.ofSeconds(30);
  private static final OAuth2Error REVOKED = new OAuth2Error("invalid_token", "La sesión ya no es válida.", null);

  private record State(UserStatus status, Instant sessionsValidAfter, Instant loadedAt) {}

  private final UserRepository userRepository;
  private final Clock clock;
  private final Map<UUID, State> cache = new ConcurrentHashMap<>();

  public SessionRevocationValidator(UserRepository userRepository, Clock clock) {
    this.userRepository = userRepository;
    this.clock = clock;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    UUID userId;
    try {
      userId = UUID.fromString(jwt.getSubject());
    } catch (RuntimeException e) {
      return OAuth2TokenValidatorResult.failure(REVOKED);
    }
    Optional<State> state = stateOf(userId);
    if (state.isEmpty() || state.get().status() != UserStatus.ACTIVE) {
      return OAuth2TokenValidatorResult.failure(REVOKED);
    }
    Instant validAfter = state.get().sessionsValidAfter();
    Instant issuedAt = jwt.getIssuedAt();
    if (validAfter != null && (issuedAt == null || issuedAt.isBefore(validAfter))) {
      return OAuth2TokenValidatorResult.failure(REVOKED);
    }
    return OAuth2TokenValidatorResult.success();
  }

  /** Olvida el estado cacheado de la cuenta (después del commit, si hay transacción). */
  public void evict(UUID userId) {
    cache.remove(userId);
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              cache.remove(userId);
            }
          });
    }
  }

  private Optional<State> stateOf(UUID userId) {
    Instant now = clock.instant();
    State cached = cache.get(userId);
    if (cached != null && cached.loadedAt().plus(CACHE_TTL).isAfter(now)) {
      return Optional.of(cached);
    }
    Optional<State> loaded = userRepository.findById(userId).map(u -> new State(u.getStatus(), u.getSessionsValidAfter(), now));
    loaded.ifPresentOrElse(s -> cache.put(userId, s), () -> cache.remove(userId));
    return loaded;
  }
}
