package com.c21genera.privacy.application;

import com.c21genera.privacy.PrivacyApi;
import com.c21genera.shared.events.PrivacyEvents.PrivacyAccepted;
import com.c21genera.privacy.domain.LegalTemplate;
import com.c21genera.privacy.domain.LegalTemplateType;
import com.c21genera.privacy.domain.PrivacyConsent;
import com.c21genera.privacy.infrastructure.LegalTemplateRepository;
import com.c21genera.privacy.infrastructure.PrivacyConsentRepository;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.storage.FileStorage;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PrivacyConsentService implements PrivacyApi {

  private final LegalTemplateRepository legalTemplateRepository;
  private final PrivacyConsentRepository privacyConsentRepository;
  private final FileStorage fileStorage;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public PrivacyConsentService(
      LegalTemplateRepository legalTemplateRepository,
      PrivacyConsentRepository privacyConsentRepository,
      FileStorage fileStorage,
      ApplicationEventPublisher events,
      Clock clock) {
    this.legalTemplateRepository = legalTemplateRepository;
    this.privacyConsentRepository = privacyConsentRepository;
    this.fileStorage = fileStorage;
    this.events = events;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public LegalTemplate currentNotice() {
    return legalTemplateRepository
        .findByTypeAndActiveTrue(LegalTemplateType.PRIVACY_NOTICE_RECEPTION)
        .orElseThrow(() -> new NotFoundException("Plantilla de aviso de privacidad activa"));
  }

  public record RecordConsentCommand(
      UUID expedienteId,
      boolean mainPurposesAccepted,
      boolean secondaryPurposesAccepted,
      String signatureBase64Png,
      String ipAddress,
      String userAgent) {}

  public PrivacyConsent recordConsent(RecordConsentCommand command) {
    LegalTemplate notice = currentNotice();

    String signatureStorageKey = null;
    String signatureSha256 = null;
    if (command.signatureBase64Png() != null && !command.signatureBase64Png().isBlank()) {
      byte[] signatureBytes = decodeSignature(command.signatureBase64Png());
      signatureStorageKey = "expedientes/%s/privacy/signature-%s.png".formatted(command.expedienteId(), UUID.randomUUID());
      FileStorage.StoredObjectMetadata stored =
          fileStorage.store(signatureStorageKey, new ByteArrayInputStream(signatureBytes), signatureBytes.length, "image/png");
      signatureSha256 = stored.sha256();
    }

    Optional<PrivacyConsent> existing = privacyConsentRepository.findByExpedienteId(command.expedienteId());
    PrivacyConsent consent;
    if (existing.isPresent()) {
      consent = existing.get();
      consent.reRecord(
          notice.getId(),
          command.mainPurposesAccepted(),
          command.secondaryPurposesAccepted(),
          clock.instant(),
          command.ipAddress(),
          command.userAgent(),
          signatureStorageKey,
          signatureSha256);
    } else {
      consent =
          new PrivacyConsent(
              command.expedienteId(),
              notice.getId(),
              command.mainPurposesAccepted(),
              command.secondaryPurposesAccepted(),
              clock.instant(),
              command.ipAddress(),
              command.userAgent(),
              signatureStorageKey,
              signatureSha256);
      privacyConsentRepository.save(consent);
    }

    // expedientes escucha este evento para avanzar su propio estado; privacy
    // no lo comanda directamente (evita una dependencia cíclica entre
    // módulos, ver AGENTS §7).
    events.publishEvent(
        new PrivacyAccepted(command.expedienteId(), command.mainPurposesAccepted(), command.secondaryPurposesAccepted()));

    return consent;
  }

  @Transactional(readOnly = true)
  public Optional<PrivacyConsent> consentEntityOf(UUID expedienteId) {
    return privacyConsentRepository.findByExpedienteId(expedienteId);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ConsentView> consentOf(UUID expedienteId) {
    return privacyConsentRepository
        .findByExpedienteId(expedienteId)
        .map(c -> new ConsentView(c.isMainPurposesAccepted(), c.isSecondaryPurposesAccepted(), c.getAcceptedAt()));
  }

  private static byte[] decodeSignature(String base64) {
    String cleaned = base64.contains(",") ? base64.substring(base64.indexOf(',') + 1) : base64;
    return Base64.getDecoder().decode(cleaned);
  }
}
