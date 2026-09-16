package com.c21genera.extraction.infrastructure;

import com.c21genera.extraction.domain.DataConflict;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataConflictRepository extends JpaRepository<DataConflict, UUID> {

  List<DataConflict> findByExpedienteIdOrderByDetectedAtDesc(UUID expedienteId);

  boolean existsByExpedienteIdAndFieldNameAndResolvedFalse(UUID expedienteId, String fieldName);

  boolean existsByExpedienteIdAndResolvedFalse(UUID expedienteId);
}
