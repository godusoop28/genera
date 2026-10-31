package com.c21genera.operations.infrastructure;

import com.c21genera.operations.domain.RequirementEvidence;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequirementEvidenceRepository extends JpaRepository<RequirementEvidence, UUID> {

  List<RequirementEvidence> findByRequirementIdInOrderByVersionNumberDesc(Collection<UUID> requirementIds);

  long countByRequirementId(UUID requirementId);
}
