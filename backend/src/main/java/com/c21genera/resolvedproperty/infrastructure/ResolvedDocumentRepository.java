package com.c21genera.resolvedproperty.infrastructure;

import com.c21genera.resolvedproperty.domain.ResolvedDocument;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResolvedDocumentRepository extends JpaRepository<ResolvedDocument, UUID> {

  List<ResolvedDocument> findByProjectIdAndRemovedAtIsNullOrderByUploadedAtDesc(UUID projectId);

  List<ResolvedDocument> findByRemovedAtIsNull();
}
