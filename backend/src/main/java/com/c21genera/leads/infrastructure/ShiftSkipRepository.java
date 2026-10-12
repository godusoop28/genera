package com.c21genera.leads.infrastructure;

import com.c21genera.leads.domain.ShiftSkip;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftSkipRepository extends JpaRepository<ShiftSkip, UUID> {

  boolean existsByShiftIdAndOccurrenceDate(UUID shiftId, LocalDate occurrenceDate);
}
