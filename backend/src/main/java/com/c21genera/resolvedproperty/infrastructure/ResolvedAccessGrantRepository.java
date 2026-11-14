package com.c21genera.resolvedproperty.infrastructure;

import com.c21genera.resolvedproperty.domain.ResolvedAccessGrant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResolvedAccessGrantRepository extends JpaRepository<ResolvedAccessGrant, UUID> {

  List<ResolvedAccessGrant> findByUserIdAndRevokedAtIsNull(UUID userId);

  Optional<ResolvedAccessGrant> findFirstByProjectIdAndUserIdAndRevokedAtIsNull(UUID projectId, UUID userId);

  List<ResolvedAccessGrant> findByProjectIdOrderByGrantedAtDesc(UUID projectId);
}
