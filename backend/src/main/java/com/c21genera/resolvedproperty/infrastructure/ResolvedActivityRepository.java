package com.c21genera.resolvedproperty.infrastructure;

import com.c21genera.resolvedproperty.domain.ResolvedActivity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResolvedActivityRepository extends JpaRepository<ResolvedActivity, UUID> {

  List<ResolvedActivity> findTop100ByProjectIdOrderByOccurredAtDesc(UUID projectId);
}
