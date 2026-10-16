package com.c21genera.contracts.infrastructure;

import com.c21genera.contracts.domain.SignedContractUpload;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SignedContractUploadRepository extends JpaRepository<SignedContractUpload, UUID> {

  List<SignedContractUpload> findByExpedienteIdOrderByUploadedAtDesc(UUID expedienteId);
}
