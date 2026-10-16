package com.c21genera.notary.infrastructure;

import com.c21genera.notary.domain.NotarySubmission;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotarySubmissionRepository extends JpaRepository<NotarySubmission, UUID> {

  List<NotarySubmission> findByExpedienteIdOrderBySentAtDesc(UUID expedienteId);
}
