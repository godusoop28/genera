package com.c21genera.publicaccess.application;

import com.c21genera.publicaccess.PublicAccessTokenApi;
import com.c21genera.publicaccess.domain.PublicAccessToken;
import com.c21genera.publicaccess.infrastructure.PublicAccessTokenRepository;
import com.c21genera.shared.events.PublicAccessEvents.PublicLinkGenerated;
import com.c21genera.shared.security.OpaqueTokens;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PublicAccessTokenService implements PublicAccessTokenApi {

  private final PublicAccessTokenRepository repository;
  private final Clock clock;
  private final ApplicationEventPublisher events;

  public PublicAccessTokenService(PublicAccessTokenRepository repository, Clock clock, ApplicationEventPublisher events) {
    this.repository = repository;
    this.clock = clock;
    this.events = events;
  }

  @Override
  public IssuedToken generate(UUID expedienteId) {
    revokeActiveTokens(expedienteId);
    String raw = OpaqueTokens.generate();
    PublicAccessToken token = new PublicAccessToken(expedienteId, OpaqueTokens.sha256Hex(raw), clock.instant(), null);
    repository.save(token);
    // expedientes escucha este evento para salir de DRAFT y quedar listo para
    // el consentimiento del cliente; publicaccess no llama a expedientes
    // directamente para no invertir la dependencia ya existente en sentido
    // contrario (expedientes.web -> publicaccess, ver AGENTS §7).
    events.publishEvent(new PublicLinkGenerated(expedienteId));
    return new IssuedToken(raw, null);
  }

  @Override
  public IssuedToken regenerate(UUID expedienteId) {
    return generate(expedienteId);
  }

  @Override
  public void revoke(UUID expedienteId) {
    revokeActiveTokens(expedienteId);
  }

  @Override
  public Optional<UUID> resolve(String rawToken) {
    String hash = OpaqueTokens.sha256Hex(rawToken);
    Optional<PublicAccessToken> found = repository.findByTokenHash(hash).filter(t -> t.isUsable(clock.instant()));
    found.ifPresent(t -> t.touchUsage(clock.instant()));
    return found.map(PublicAccessToken::getExpedienteId);
  }

  private void revokeActiveTokens(UUID expedienteId) {
    List<PublicAccessToken> active = repository.findByExpedienteIdAndActiveTrue(expedienteId);
    Instant now = clock.instant();
    active.forEach(t -> t.revoke(now));
  }
}
