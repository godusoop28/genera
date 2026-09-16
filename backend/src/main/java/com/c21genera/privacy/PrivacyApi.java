package com.c21genera.privacy;

import java.util.Optional;
import java.util.UUID;

/** API pública del módulo privacy (ver AGENTS §7). */
public interface PrivacyApi {

  Optional<ConsentView> consentOf(UUID expedienteId);

  record ConsentView(boolean mainPurposesAccepted, boolean secondaryPurposesAccepted, java.time.Instant acceptedAt) {}
}
