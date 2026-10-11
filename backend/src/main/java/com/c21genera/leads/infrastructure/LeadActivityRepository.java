package com.c21genera.leads.infrastructure;

import com.c21genera.leads.domain.LeadActivity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadActivityRepository extends JpaRepository<LeadActivity, UUID> {

  List<LeadActivity> findByLeadIdOrderByCreatedAtDesc(UUID leadId);
}
