package com.c21genera.operations.infrastructure;

import com.c21genera.operations.domain.ClosingRequirement;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClosingRequirementRepository extends JpaRepository<ClosingRequirement, UUID> {

  List<ClosingRequirement> findByOperationIdOrderBySortOrderAsc(UUID operationId);

  List<ClosingRequirement> findByOperationIdIn(Collection<UUID> operationIds);
}
