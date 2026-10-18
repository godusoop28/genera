package com.c21genera.leads.infrastructure;

import com.c21genera.leads.domain.CrmProperty;
import com.c21genera.leads.domain.SyncStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CrmPropertyRepository extends JpaRepository<CrmProperty, UUID> {

  List<CrmProperty> findTop200ByOrderByCreatedAtDesc();

  List<CrmProperty> findTop25BySyncStatusOrderByCreatedAtAsc(SyncStatus status);

  List<CrmProperty> findByOnline21IdIn(Collection<Long> online21Ids);
}
