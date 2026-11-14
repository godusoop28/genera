package com.c21genera.privacy.application;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.privacy.domain.PrivacyConsent;
import com.c21genera.privacy.infrastructure.PrivacyConsentRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Control de Cambios #45: revisión de los avisos registrados antes del cambio, al arrancar.
 *
 * <ul>
 *   <li>Firmas digitales sin copia en Documentos: se genera la copia firmada (aviso + constancia)
 *       con los datos y la imagen de firma que ya estaban guardados.
 *   <li>Firmas en papel: se comprueba que el escaneo siga en los documentos del expediente con su
 *       archivo; si no, queda en el log (no se inventa nada) para recuperarlo.
 * </ul>
 *
 * Es idempotente: una vez generada la copia, el consentimiento ya no vuelve a aparecer.
 */
@Component
class PrivacySignedCopyBackfill {

  private static final Logger log = LoggerFactory.getLogger(PrivacySignedCopyBackfill.class);
  private static final int MAX_PER_START = 200;

  private final PrivacyConsentRepository consentRepository;
  private final PrivacySignedCopyService copyService;
  private final DocumentsApi documentsApi;

  PrivacySignedCopyBackfill(PrivacyConsentRepository consentRepository, PrivacySignedCopyService copyService, DocumentsApi documentsApi) {
    this.consentRepository = consentRepository;
    this.copyService = copyService;
    this.documentsApi = documentsApi;
  }

  @EventListener(ApplicationReadyEvent.class)
  void onReady() {
    Thread worker = new Thread(this::run, "privacy-signed-copy-backfill");
    worker.setDaemon(true);
    worker.start();
  }

  void run() {
    try {
      List<PrivacyConsent> digital = consentRepository.findBySignatureMethodAndMainPurposesAcceptedTrueAndSignedDocumentIdIsNull("DIGITAL");
      int written = 0;
      int failed = 0;
      for (PrivacyConsent consent : digital.stream().limit(MAX_PER_START).toList()) {
        try {
          if (copyService.writeCopyIfMissing(consent)) {
            written++;
          }
        } catch (RuntimeException e) {
          failed++;
          log.warn("CC#45: no se pudo generar la copia firmada del aviso del expediente {}: {}", consent.getExpedienteId(), e.getMessage());
        }
      }
      int missingScans = 0;
      for (PrivacyConsent consent : consentRepository.findBySignatureMethod("PHYSICAL")) {
        boolean present =
            consent.getSignedDocumentId() != null
                && documentsApi.requirementStatusOf(consent.getExpedienteId()).stream()
                    .anyMatch(d -> d.documentId().equals(consent.getSignedDocumentId()) && d.currentVersionId() != null);
        if (!present) {
          missingScans++;
          log.warn("CC#45: el aviso firmado en papel del expediente {} no tiene archivo en sus documentos; hay que volver a cargarlo.", consent.getExpedienteId());
        }
      }
      if (!digital.isEmpty() || missingScans > 0) {
        log.info(
            "CC#45: revisión de avisos anteriores: {} firmas digitales sin copia, {} copias generadas, {} con error, {} escaneos en papel sin archivo.",
            digital.size(), written, failed, missingScans);
      }
    } catch (RuntimeException e) {
      log.warn("CC#45: la revisión de avisos anteriores no terminó: {}", e.getMessage());
    }
  }
}
