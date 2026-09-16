package com.c21genera.expedientes.infrastructure;

import com.c21genera.expedientes.domain.ExpedienteParticipant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpedienteParticipantRepository extends JpaRepository<ExpedienteParticipant, UUID> {

  List<ExpedienteParticipant> findByExpedienteIdOrderByOrdinalAsc(UUID expedienteId);
}
