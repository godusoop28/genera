package com.c21genera.closing.infrastructure;

import com.c21genera.closing.domain.ClosingCase;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClosingCaseRepository extends JpaRepository<ClosingCase, UUID> {

  Optional<ClosingCase> findByExpedienteId(UUID expedienteId);

  boolean existsByExpedienteId(UUID expedienteId);
}
