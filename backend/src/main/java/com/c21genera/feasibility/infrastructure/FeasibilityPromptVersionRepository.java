package com.c21genera.feasibility.infrastructure;

import com.c21genera.feasibility.domain.FeasibilityPromptVersion;
import com.c21genera.feasibility.domain.FeasibilityPromptVersion.PromptKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeasibilityPromptVersionRepository extends JpaRepository<FeasibilityPromptVersion, UUID> {

  Optional<FeasibilityPromptVersion> findFirstByKeyOrderByVersionNumberDesc(PromptKey key);

  List<FeasibilityPromptVersion> findByKeyOrderByVersionNumberDesc(PromptKey key);

  Optional<FeasibilityPromptVersion> findByKeyAndVersionNumber(PromptKey key, int versionNumber);
}
