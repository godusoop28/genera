package com.c21genera.contracts.infrastructure;

import com.c21genera.contracts.domain.ContractSignature;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractSignatureRepository extends JpaRepository<ContractSignature, UUID> {

  List<ContractSignature> findByContractGenerationIdOrderByRequestedAtAsc(UUID contractGenerationId);

  Optional<ContractSignature> findByTokenHash(String tokenHash);
}
