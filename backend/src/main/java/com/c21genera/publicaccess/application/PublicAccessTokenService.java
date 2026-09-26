package com.c21genera.publicaccess.application;

import com.c21genera.publicaccess.PublicAccessTokenApi;
import com.c21genera.publicaccess.domain.PublicAccessToken;
import com.c21genera.publicaccess.infrastructure.PublicAccessTokenRepository;
import com.c21genera.shared.config.PublicLinkProperties;
import com.c21genera.shared.events.Actor;
import com.c21genera.shared.events.ExpedienteEvents.PropertyAccepted;
import com.c21genera.shared.events.ExpedienteEvents.PropertyRejected;
import com.c21genera.shared.events.PublicAccessEvents.PublicLinkGenerated;
import com.c21genera.shared.events.PublicAccessEvents.PublicLinkRevoked;
import com.c21genera.shared.security.OpaqueTokens;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ligas de cliente con vencimiento (app.security.public-link.ttl), una sola
 * activa por expediente, revocables por el staff y revocadas
 * automáticamente cuando el expediente ya se decidió.
 */
@Service
@Transactional
public class PublicAccessTokenService implements PublicAccessTokenApi {

  private final PublicAccessTokenRepository repository;
  private final PublicLinkProperties properties;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  public PublicAccessTokenService(
      PublicAccessTokenRepository repository, PublicLinkProperties properties, Clock clock, ApplicationEventPublisher events) {
    this.repository = repository;
    this.properties = properties;
    this.clock = clock;
    this.events = events;
  }

  @Override
  public IssuedToken generate(UUID expedienteId, Actor actor) {
    revokeActiveTokens(expedienteId);
    String raw = OpaqueTokens.generate();
    Instant now = clock.instant();
    Instant expiresAt = now.plus(properties.ttl());
    repository.save(new PublicAccessToken(expedienteId, OpaqueTokens.sha256Hex(raw), now, expiresAt));
    // expedientes escucha este evento para salir de DRAFT y quedar listo para
    // el consentimiento del cliente; publicaccess no llama a expedientes
    // directamente para no invertir la dependencia ya existente en sentido
    // contrario (expedientes.web -> publicaccess, ver AGENTS §7).
    events.publishEvent(new PublicLinkGenerated(expedienteId, actor, expiresAt));
    return new IssuedToken(raw, expiresAt);
  }

  @Override
  public void revoke(UUID expedienteId, Actor actor, String reason) {
    if (revokeActiveTokens(expedienteId) > 0) {
      events.publishEvent(new PublicLinkRevoked(expedienteId, actor, reason));
    }
  }

  @Override
  public Optional<UUID> resolve(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      return Optional.empty();
    }
    String hash = OpaqueTokens.sha256Hex(rawToken);
    Optional<PublicAccessToken> found = repository.findByTokenHash(hash).filter(t -> t.isUsable(clock.instant()));
    found.ifPresent(t -> t.touchUsage(clock.instant()));
    return found.map(PublicAccessToken::getExpedienteId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<LinkStatus> statusOf(UUID expedienteId) {
    Instant now = clock.instant();
    return repository
        .findFirstByExpedienteIdOrderByCreatedAtDesc(expedienteId)
        .map(t -> new LinkStatus(t.getCreatedAt(), t.getExpiresAt(), t.getRevokedAt(), t.getLastUsedAt(), t.isUsable(now)));
  }

  /** Un expediente ya decidido no debe seguir accesible por una liga que pudo reenviarse. */
  @ApplicationModuleListener
  void on(PropertyAccepted event) {
    revoke(event.expedienteId(), Actor.system(), "El inmueble fue aceptado; el expediente ya no recibe documentos.");
  }

  @ApplicationModuleListener
  void on(PropertyRejected event) {
    revoke(event.expedienteId(), Actor.system(), "El inmueble fue rechazado; el expediente ya no recibe documentos.");
  }

  private int revokeActiveTokens(UUID expedienteId) {
    List<PublicAccessToken> active = repository.findByExpedienteIdAndActiveTrue(expedienteId);
    Instant now = clock.instant();
    active.forEach(t -> t.revoke(now));
    return active.size();
  }
}
