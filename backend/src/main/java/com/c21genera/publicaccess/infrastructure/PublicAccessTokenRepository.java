package com.c21genera.publicaccess.infrastructure;

import com.c21genera.publicaccess.domain.PublicAccessToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicAccessTokenRepository extends JpaRepository<PublicAccessToken, UUID> {

  Optional<PublicAccessToken> findByTokenHash(String tokenHash);

  List<PublicAccessToken> findByExpedienteIdAndActiveTrue(UUID expedienteId);
}
