package com.c21genera.leads.infrastructure;

import com.c21genera.leads.domain.AssignmentLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssignmentLogRepository extends JpaRepository<AssignmentLog, UUID> {

  List<AssignmentLog> findByLeadIdOrderByCreatedAtDesc(UUID leadId);
}
