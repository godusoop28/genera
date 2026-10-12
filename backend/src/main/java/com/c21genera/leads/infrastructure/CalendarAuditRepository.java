package com.c21genera.leads.infrastructure;

import com.c21genera.leads.domain.CalendarAudit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CalendarAuditRepository extends JpaRepository<CalendarAudit, UUID> {

  List<CalendarAudit> findTop100ByOrderByCreatedAtDesc();

  List<CalendarAudit> findTop100ByAdvisorUserIdOrderByCreatedAtDesc(UUID advisorUserId);
}
