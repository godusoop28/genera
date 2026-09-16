package com.c21genera.contracts.infrastructure;

import com.c21genera.contracts.domain.ContractGeneration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractGenerationRepository extends JpaRepository<ContractGeneration, UUID> {

  List<ContractGeneration> findByExpedienteIdOrderByVersionNumberDesc(UUID expedienteId);

  Optional<ContractGeneration> findFirstByExpedienteIdOrderByVersionNumberDesc(UUID expedienteId);
}
