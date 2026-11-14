package com.c21genera.resolvedproperty.infrastructure;

import com.c21genera.resolvedproperty.domain.ResolvedProject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResolvedProjectRepository extends JpaRepository<ResolvedProject, UUID> {

  List<ResolvedProject> findByArchivedAtIsNullOrderByCreatedAtDesc();
}
