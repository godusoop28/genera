package com.c21genera.leads.infrastructure;

import com.c21genera.leads.domain.LeadSharedProperty;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadSharedPropertyRepository extends JpaRepository<LeadSharedProperty, UUID> {

  List<LeadSharedProperty> findByLeadIdOrderByCreatedAtDesc(UUID leadId);
}
