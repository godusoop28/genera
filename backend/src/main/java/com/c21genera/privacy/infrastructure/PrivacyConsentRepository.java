package com.c21genera.privacy.infrastructure;

import com.c21genera.privacy.domain.PrivacyConsent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivacyConsentRepository extends JpaRepository<PrivacyConsent, UUID> {

  Optional<PrivacyConsent> findByExpedienteId(UUID expedienteId);

  /** Control de Cambios #45: firmas digitales anteriores sin copia en los documentos del expediente. */
  List<PrivacyConsent> findBySignatureMethodAndMainPurposesAcceptedTrueAndSignedDocumentIdIsNull(String signatureMethod);

  List<PrivacyConsent> findBySignatureMethod(String signatureMethod);
}
