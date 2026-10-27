package com.c21genera.privacy.application;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.privacy.PrivacyApi;
import com.c21genera.privacy.domain.LegalTemplate;
import com.c21genera.privacy.domain.LegalTemplateType;
import com.c21genera.privacy.domain.PrivacyConsent;
import com.c21genera.privacy.infrastructure.LegalTemplateRepository;
import com.c21genera.privacy.infrastructure.PrivacyConsentRepository;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.events.Actor;
import com.c21genera.shared.events.PrivacyEvents.PrivacyAccepted;
import com.c21genera.shared.storage.FileStorage;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
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
  private final DocumentsApi documentsApi;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public PrivacyConsentService(
      LegalTemplateRepository legalTemplateRepository,
      PrivacyConsentRepository privacyConsentRepository,
      FileStorage fileStorage,
      DocumentsApi documentsApi,
      ApplicationEventPublisher events,
      Clock clock) {
    this.legalTemplateRepository = legalTemplateRepository;
    this.privacyConsentRepository = privacyConsentRepository;
    this.fileStorage = fileStorage;
    this.documentsApi = documentsApi;
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
    consent.markDigital();
    events.publishEvent(
        new PrivacyAccepted(command.expedienteId(), command.mainPurposesAccepted(), command.secondaryPurposesAccepted()));

    return consent;
  }

  /**
   * Control de Cambios #2: el titular firmó el aviso en papel en la oficina. El escaneo se guarda
   * como documento del expediente y queda registrado quién lo cargó; vale como la aceptación de la
   * finalidad principal (la secundaria, según lo que marcó en papel).
   */
  public PrivacyConsent recordPhysicalSignature(
      UUID expedienteId, List<DocumentsApi.IncomingFile> files, boolean secondaryPurposesAccepted, Actor actor) {
    if (files == null || files.isEmpty()) {
      throw new com.c21genera.shared.domain.UnprocessableException("NO_FILES", "Carga el escaneo o la foto del aviso firmado.");
    }
    LegalTemplate notice = currentNotice();
    UUID documentId = documentsApi.attachSignedPrivacyNotice(expedienteId, files, actor);
    String where = "Firma en papel (oficina)";
    PrivacyConsent consent =
        privacyConsentRepository
            .findByExpedienteId(expedienteId)
            .map(
                existing -> {
                  existing.reRecord(notice.getId(), true, secondaryPurposesAccepted, clock.instant(), where, null, null, null);
                  return existing;
                })
            .orElseGet(
                () ->
                    privacyConsentRepository.save(
                        new PrivacyConsent(expedienteId, notice.getId(), true, secondaryPurposesAccepted, clock.instant(), where, null, null, null)));
    consent.markPhysical(actor.userId(), documentId);
    events.publishEvent(new PrivacyAccepted(expedienteId, true, secondaryPurposesAccepted, true, actor));
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
        .map(
            c ->
                new ConsentView(
                    c.isMainPurposesAccepted(), c.isSecondaryPurposesAccepted(), c.getAcceptedAt(), c.getSignatureMethod(), c.getSignedDocumentId()));
  }

  @Override
  @Transactional(readOnly = true)
  public boolean activePrivacyNoticeTemplateExists() {
    return legalTemplateRepository.findByTypeAndActiveTrue(LegalTemplateType.PRIVACY_NOTICE_RECEPTION).isPresent();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<NoticeVersion> noticeFor(UUID expedienteId) {
    Optional<PrivacyConsent> consent = privacyConsentRepository.findByExpedienteId(expedienteId);
    Optional<LegalTemplate> template =
        consent.map(PrivacyConsent::getNoticeTemplateId).flatMap(legalTemplateRepository::findById).or(() -> legalTemplateRepository.findByTypeAndActiveTrue(LegalTemplateType.PRIVACY_NOTICE_RECEPTION));
    return template.map(
        t ->
            new NoticeVersion(
                t.getVersion(),
                t.getEffectiveFrom(),
                t.getSha256(),
                t.getStorageKey(),
                consent.isPresent(),
                consent.map(PrivacyConsent::getAcceptedAt).orElse(null),
                consent.map(PrivacyConsent::getSignatureMethod).orElse(null)));
  }

  @Override
  @Transactional(readOnly = true)
  public boolean activeContractTemplateExists() {
    return legalTemplateRepository.findByTypeAndActiveTrue(LegalTemplateType.INTERMEDIATION_CONTRACT).isPresent();
  }

  private static byte[] decodeSignature(String base64) {
    String cleaned = base64.contains(",") ? base64.substring(base64.indexOf(',') + 1) : base64;
    return Base64.getDecoder().decode(cleaned);
  }
}
