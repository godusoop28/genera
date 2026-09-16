package com.c21genera.privacy;

import java.util.Optional;
import java.util.UUID;

/** API pública del módulo privacy (ver AGENTS §7). */
public interface PrivacyApi {

  Optional<ConsentView> consentOf(UUID expedienteId);

  /** Usado por compliance para el checklist (ver AGENTS §42): no expone el enum interno de tipos de plantilla. */
  boolean activePrivacyNoticeTemplateExists();

  boolean activeContractTemplateExists();

  record ConsentView(boolean mainPurposesAccepted, boolean secondaryPurposesAccepted, java.time.Instant acceptedAt) {}
}
