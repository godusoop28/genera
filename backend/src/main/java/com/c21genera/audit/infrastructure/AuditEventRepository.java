package com.c21genera.audit.infrastructure;

import com.c21genera.audit.domain.AuditEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

  List<AuditEvent> findByAggregateIdOrderByOccurredAtDesc(UUID aggregateId);

  Page<AuditEvent> findByAggregateTypeOrderByOccurredAtDesc(String aggregateType, Pageable pageable);
}
