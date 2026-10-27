package com.c21genera.leads.infrastructure;

import com.c21genera.leads.domain.LeadStageHistory;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadStageHistoryRepository extends JpaRepository<LeadStageHistory, UUID> {

  List<LeadStageHistory> findByLeadIdOrderByEnteredAtAsc(UUID leadId);

  List<LeadStageHistory> findByLeadIdIn(Collection<UUID> leadIds);
}
