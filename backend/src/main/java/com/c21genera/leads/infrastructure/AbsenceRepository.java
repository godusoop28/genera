package com.c21genera.leads.infrastructure;

import com.c21genera.leads.domain.Absence;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AbsenceRepository extends JpaRepository<Absence, UUID> {

  List<Absence> findByDeletedAtIsNullAndEndsAtAfter(Instant after);

  List<Absence> findByAdvisorUserIdAndDeletedAtIsNullAndEndsAtAfter(UUID advisorUserId, Instant after);
}
