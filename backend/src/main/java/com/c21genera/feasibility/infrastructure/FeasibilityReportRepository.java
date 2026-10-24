package com.c21genera.feasibility.infrastructure;

import com.c21genera.feasibility.domain.FeasibilityReport;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeasibilityReportRepository extends JpaRepository<FeasibilityReport, UUID> {

  List<FeasibilityReport> findByExpedienteIdOrderByVersionNumberDesc(UUID expedienteId);

  Optional<FeasibilityReport> findFirstByExpedienteIdOrderByVersionNumberDesc(UUID expedienteId);

  boolean existsByExpedienteIdAndStatusIn(UUID expedienteId, Collection<FeasibilityReport.Status> statuses);

  List<FeasibilityReport> findByStatusInOrderByRequestedAtAsc(Collection<FeasibilityReport.Status> statuses);
}
