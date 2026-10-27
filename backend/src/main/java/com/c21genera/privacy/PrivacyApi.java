package com.c21genera.privacy;

import java.util.Optional;
import java.util.UUID;

/** API pública del módulo privacy (ver AGENTS §7). */
public interface PrivacyApi {

  Optional<ConsentView> consentOf(UUID expedienteId);

  /** Usado por compliance para el checklist (ver AGENTS §42): no expone el enum interno de tipos de plantilla. */
  boolean activePrivacyNoticeTemplateExists();

  boolean activeContractTemplateExists();

  /**
   * Control de Cambios #31: versión del aviso de privacidad que cubre el expediente: la que aceptó
   * el cliente o, si todavía no lo firma, la vigente. storageKey: el PDF del aviso (para adjuntarlo).
   */
  Optional<NoticeVersion> noticeFor(UUID expedienteId);

  record NoticeVersion(int version, java.time.Instant effectiveFrom, String sha256, String storageKey, boolean acceptedByClient, java.time.Instant acceptedAt, String signatureMethod) {}

  /** signatureMethod: DIGITAL (en la liga) o PHYSICAL (en papel, escaneo en signedDocumentId). */
  record ConsentView(
      boolean mainPurposesAccepted,
      boolean secondaryPurposesAccepted,
      java.time.Instant acceptedAt,
      String signatureMethod,
      java.util.UUID signedDocumentId) {

    public ConsentView(boolean mainPurposesAccepted, boolean secondaryPurposesAccepted, java.time.Instant acceptedAt) {
      this(mainPurposesAccepted, secondaryPurposesAccepted, acceptedAt, "DIGITAL", null);
    }
  }
}
