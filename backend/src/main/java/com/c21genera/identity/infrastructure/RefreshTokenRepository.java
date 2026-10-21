package com.c21genera.identity.infrastructure;

import com.c21genera.identity.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  /** Control de Cambios #25: cierra todas las sesiones de la persona (celular y otros navegadores). */
  @Modifying
  @Query("UPDATE RefreshToken t SET t.revokedAt = :when WHERE t.userId = :userId AND t.revokedAt IS NULL")
  int revokeAllOf(UUID userId, Instant when);
}
