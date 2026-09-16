package com.c21genera.extraction.infrastructure;

import com.c21genera.extraction.domain.ExtractedFieldObservation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractedFieldObservationRepository extends JpaRepository<ExtractedFieldObservation, UUID> {

  List<ExtractedFieldObservation> findByDocumentIdOrderByFieldNameAsc(UUID documentId);

  List<ExtractedFieldObservation> findByExpedienteIdOrderByFieldNameAsc(UUID expedienteId);

  List<ExtractedFieldObservation> findByDocumentVersionId(UUID documentVersionId);
}
