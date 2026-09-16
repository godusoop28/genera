package com.c21genera.documents.infrastructure;

import com.c21genera.documents.domain.Document;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

  List<Document> findByExpedienteId(UUID expedienteId);

  Optional<Document> findByExpedienteIdAndRequirementCode(UUID expedienteId, String requirementCode);

  boolean existsByExpedienteIdAndRequirementCode(UUID expedienteId, String requirementCode);
}
