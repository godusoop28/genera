package com.c21genera.operations.infrastructure;

import com.c21genera.operations.domain.OperationEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationEventRepository extends JpaRepository<OperationEvent, UUID> {

  List<OperationEvent> findByOperationIdOrderByOccurredAtDesc(UUID operationId);
}
