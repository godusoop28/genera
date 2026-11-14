package com.c21genera.privacy.infrastructure;

import com.c21genera.privacy.domain.PrivacySignLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrivacySignLinkRepository extends JpaRepository<PrivacySignLink, UUID> {

  Optional<PrivacySignLink> findByTokenHash(String tokenHash);

  List<PrivacySignLink> findByExpedienteIdAndRevokedAtIsNullAndUsedAtIsNull(UUID expedienteId);

  Optional<PrivacySignLink> findFirstByExpedienteIdOrderByCreatedAtDesc(UUID expedienteId);
}
