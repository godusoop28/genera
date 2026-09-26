package com.c21genera.expedientes.infrastructure;

import com.c21genera.expedientes.domain.ExpedienteChange;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpedienteChangeRepository extends JpaRepository<ExpedienteChange, UUID> {

  List<ExpedienteChange> findByExpedienteIdOrderByChangedAtDesc(UUID expedienteId);
}
