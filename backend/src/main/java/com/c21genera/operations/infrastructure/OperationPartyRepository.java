package com.c21genera.operations.infrastructure;

import com.c21genera.operations.domain.OperationParty;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationPartyRepository extends JpaRepository<OperationParty, UUID> {

  List<OperationParty> findByOperationId(UUID operationId);

  List<OperationParty> findByOperationIdIn(java.util.Collection<UUID> operationIds);
}
