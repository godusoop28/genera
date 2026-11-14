package com.c21genera.privacy.application;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.privacy.domain.LegalTemplate;
import com.c21genera.privacy.domain.PrivacyConsent;
import com.c21genera.privacy.infrastructure.LegalTemplateRepository;
import com.c21genera.privacy.infrastructure.PrivacyConsentRepository;
import com.c21genera.privacy.infrastructure.PrivacySignedCopyRenderer;
import com.c21genera.privacy.infrastructure.PrivacySignedCopyRenderer.Evidence;
import com.c21genera.shared.events.Actor;
import com.c21genera.shared.events.PrivacyEvents.PrivacyAccepted;
import com.c21genera.shared.storage.FileStorage;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Control de Cambios #45: cuando el cliente firma el aviso en una liga (la del expediente o la
 * exclusiva del CC#44), se guarda una copia firmada como documento «Aviso de Privacidad» del
 * mismo expediente, para que se vea y consulte en Documentos como el escaneo de la firma en papel.
 *
 * <p>Corre después de confirmar la firma (en su propia transacción), así un problema al generar el
 * PDF nunca deshace la firma del cliente.
 */
@Service
public class PrivacySignedCopyService {

  private static final Logger log = LoggerFactory.getLogger(PrivacySignedCopyService.class);
  private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ZoneId.of("America/Mexico_City"));

  private final PrivacyConsentRepository consentRepository;
  private final LegalTemplateRepository templateRepository;
  private final FileStorage fileStorage;
  private final DocumentsApi documentsApi;
  private final PrivacySignedCopyRenderer renderer;

  public PrivacySignedCopyService(
      PrivacyConsentRepository consentRepository,
      LegalTemplateRepository templateRepository,
      FileStorage fileStorage,
      DocumentsApi documentsApi,
      PrivacySignedCopyRenderer renderer) {
    this.consentRepository = consentRepository;
    this.templateRepository = templateRepository;
    this.fileStorage = fileStorage;
    this.documentsApi = documentsApi;
    this.renderer = renderer;
  }

  @ApplicationModuleListener
  void on(PrivacyAccepted event) {
    if (event.physical() || !event.mainPurposesAccepted()) {
      return;
    }
    consentRepository.findByExpedienteId(event.expedienteId()).ifPresent(this::writeCopyIfMissing);
  }

  /** Genera y guarda la copia firmada si el consentimiento digital vigente todavía no la tiene. */
  @Transactional
  public boolean writeCopyIfMissing(PrivacyConsent consent) {
    if (!"DIGITAL".equals(consent.getSignatureMethod()) || !consent.isMainPurposesAccepted() || consent.getSignedDocumentId() != null) {
      return false;
    }
    LegalTemplate notice = templateRepository.findById(consent.getNoticeTemplateId()).orElse(null);
    byte[] noticePdf = notice == null ? null : read(notice.getStorageKey());
    byte[] signature = read(consent.getSignatureStorageKey());
    String channel =
        PrivacyConsentService.CHANNEL_SIGN_LINK.equals(consent.getSignatureChannel())
            ? "Firma electrónica en la liga exclusiva del aviso de privacidad"
            : "Firma electrónica en la liga del expediente";
    byte[] pdf =
        renderer.render(
            noticePdf,
            new Evidence(
                channel,
                consent.getAcceptedAt(),
                notice == null ? 0 : notice.getVersion(),
                notice == null ? null : notice.getSha256(),
                consent.isMainPurposesAccepted(),
                consent.isSecondaryPurposesAccepted(),
                consent.getIpAddress(),
                consent.getUserAgent(),
                signature,
                consent.getSignatureSha256()));
    String filename = "aviso-de-privacidad-firmado-" + FILE_DATE.format(consent.getAcceptedAt()) + ".pdf";
    UUID documentId =
        documentsApi.attachSignedPrivacyNotice(
            consent.getExpedienteId(),
            List.of(new DocumentsApi.IncomingFile(pdf.length, filename, new ByteArrayResource(pdf))),
            Actor.client());
    PrivacyConsent managed = consentRepository.findById(consent.getId()).orElse(consent);
    managed.attachSignedCopy(documentId);
    consentRepository.save(managed);
    return true;
  }

  private byte[] read(String storageKey) {
    if (storageKey == null || storageKey.isBlank()) {
      return null;
    }
    try (InputStream in = fileStorage.get(storageKey)) {
      return in.readAllBytes();
    } catch (Exception e) {
      log.warn("No se pudo leer {} para la copia firmada del aviso de privacidad: {}", storageKey, e.getMessage());
      return null;
    }
  }
}
