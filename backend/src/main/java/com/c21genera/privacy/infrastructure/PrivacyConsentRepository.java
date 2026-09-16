package com.c21genera.privacy.infrastructure;

import com.c21genera.privacy.domain.PrivacyConsent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivacyConsentRepository extends JpaRepository<PrivacyConsent, UUID> {

  Optional<PrivacyConsent> findByExpedienteId(UUID expedienteId);
}
