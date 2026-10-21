package com.c21genera.identity.infrastructure;

import com.c21genera.identity.domain.PasswordSetupToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PasswordSetupTokenRepository extends JpaRepository<PasswordSetupToken, UUID> {

  Optional<PasswordSetupToken> findByTokenHash(String tokenHash);

  @Query("SELECT t FROM PasswordSetupToken t WHERE t.userId = :userId AND t.usedAt IS NULL AND t.revokedAt IS NULL")
  List<PasswordSetupToken> findOpenByUserId(UUID userId);
}
