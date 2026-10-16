package com.c21genera.notary.infrastructure;

import com.c21genera.notary.domain.ClosingDocumentCheck;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClosingDocumentCheckRepository extends JpaRepository<ClosingDocumentCheck, UUID> {

  List<ClosingDocumentCheck> findByExpedienteId(UUID expedienteId);
}
