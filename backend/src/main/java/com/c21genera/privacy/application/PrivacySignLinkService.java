package com.c21genera.privacy.application;

import com.c21genera.privacy.application.PrivacyConsentService.RecordConsentCommand;
import com.c21genera.privacy.domain.PrivacyConsent;
import com.c21genera.privacy.domain.PrivacySignLink;
import com.c21genera.privacy.infrastructure.PrivacySignLinkRepository;
import com.c21genera.publicaccess.PublicLinkRevokedException;
import com.c21genera.shared.config.PublicLinkProperties;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.events.Actor;
import com.c21genera.shared.events.PrivacyEvents.PrivacySignLinkIssued;
import com.c21genera.shared.events.PrivacyEvents.PrivacySignLinkRevoked;
import com.c21genera.shared.security.OpaqueTokens;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Control de Cambios #44: liga exclusiva para que el cliente firme el aviso de privacidad de su
 * expediente, sin poder ver ni cargar nada más.
 *
 * <ul>
 *   <li>Una liga abierta por expediente: generar otra invalida la anterior (sirve como reenvío).
 *   <li>Vence a los 7 días y deja de funcionar en cuanto se firma (un solo uso).
 *   <li>Generarla o compartirla no es firmar: el aviso sigue «Pendiente de firma» hasta que el
 *       cliente lo firma en la liga.
 * </ul>
 *
 * Pendiente con el área legal: verificación de identidad del firmante y vigencia definitiva.
 */
@Service
@Transactional
public class PrivacySignLinkService {

  public static final Duration TTL = Duration.ofDays(7);

  private final PrivacySignLinkRepository repository;
  private final PrivacyConsentService consentService;
  private final PublicLinkProperties publicLinkProperties;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public PrivacySignLinkService(
      PrivacySignLinkRepository repository,
      PrivacyConsentService consentService,
      PublicLinkProperties publicLinkProperties,
      ApplicationEventPublisher events,
      Clock clock) {
    this.repository = repository;
    this.consentService = consentService;
    this.publicLinkProperties = publicLinkProperties;
    this.events = events;
    this.clock = clock;
  }

  public record IssuedSignLink(String token, String url, Instant expiresAt) {}

  /** createdAt/expiresAt de la liga más reciente; usable = se puede abrir y firmar ahora. */
  public record SignLinkStatus(Instant createdAt, String createdByName, Instant expiresAt, Instant revokedAt, Instant usedAt, Instant lastOpenedAt, boolean usable) {}

  public IssuedSignLink issue(UUID expedienteId, Actor actor) {
    if (consentService.consentOf(expedienteId).map(c -> c.mainPurposesAccepted() && "DIGITAL".equals(c.signatureMethod())).orElse(false)) {
      throw new UnprocessableException("PRIVACY_ALREADY_SIGNED", "El cliente ya firmó el aviso de privacidad de este expediente.");
    }
    Instant now = clock.instant();
    repository.findByExpedienteIdAndRevokedAtIsNullAndUsedAtIsNull(expedienteId).forEach(l -> l.revoke(now));
    String raw = OpaqueTokens.generate();
    Instant expiresAt = now.plus(TTL);
    repository.save(new PrivacySignLink(expedienteId, OpaqueTokens.sha256Hex(raw), now, expiresAt, actor.userId(), actor.name()));
    events.publishEvent(new PrivacySignLinkIssued(expedienteId, expiresAt, actor));
    return new IssuedSignLink(raw, urlFor(raw), expiresAt);
  }

  public void revoke(UUID expedienteId, Actor actor) {
    Instant now = clock.instant();
    var open = repository.findByExpedienteIdAndRevokedAtIsNullAndUsedAtIsNull(expedienteId);
    open.forEach(l -> l.revoke(now));
    if (!open.isEmpty()) {
      events.publishEvent(new PrivacySignLinkRevoked(expedienteId, actor));
    }
  }

  @Transactional(readOnly = true)
  public Optional<SignLinkStatus> statusOf(UUID expedienteId) {
    Instant now = clock.instant();
    return repository
        .findFirstByExpedienteIdOrderByCreatedAtDesc(expedienteId)
        .map(l -> new SignLinkStatus(l.getCreatedAt(), l.getCreatedByName(), l.getExpiresAt(), l.getRevokedAt(), l.getUsedAt(), l.getLastOpenedAt(), l.isUsable(now)));
  }

  /** Abre la liga: solo dice qué versión del aviso se va a firmar (ningún dato del expediente). */
  public PrivacySignLink open(String rawToken) {
    PrivacySignLink link = resolve(rawToken);
    link.touch(clock.instant());
    return link;
  }

  public PrivacyConsent sign(String rawToken, boolean secondaryAccepted, String signatureBase64Png, String ipAddress, String userAgent) {
    PrivacySignLink link = resolve(rawToken);
    if (signatureBase64Png == null || signatureBase64Png.isBlank()) {
      throw new UnprocessableException("SIGNATURE_REQUIRED", "Dibuja tu firma para firmar el aviso.");
    }
    PrivacyConsent consent =
        consentService.recordConsent(
            new RecordConsentCommand(
                link.getExpedienteId(), true, secondaryAccepted, signatureBase64Png, ipAddress, userAgent, PrivacyConsentService.CHANNEL_SIGN_LINK));
    link.markUsed(clock.instant());
    return consent;
  }

  private PrivacySignLink resolve(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new PublicLinkRevokedException();
    }
    return repository
        .findByTokenHash(OpaqueTokens.sha256Hex(rawToken))
        .filter(l -> l.isUsable(clock.instant()))
        .orElseThrow(PublicLinkRevokedException::new);
  }

  /** La liga de carga termina en /carga; la del aviso es la misma raíz con /aviso. */
  private String urlFor(String raw) {
    String base = publicLinkProperties.baseUrl() == null ? "" : publicLinkProperties.baseUrl().replaceAll("/+$", "");
    base = base.endsWith("/carga") ? base.substring(0, base.length() - "/carga".length()) : base;
    return base + "/aviso/" + raw;
  }
}
