package com.c21genera.leads.infrastructure;

import com.c21genera.leads.domain.Shift;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

  List<Shift> findByDeletedAtIsNull();

  List<Shift> findByAdvisorUserIdAndDeletedAtIsNull(UUID advisorUserId);
}
