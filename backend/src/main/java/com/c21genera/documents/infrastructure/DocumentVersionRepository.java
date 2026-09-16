package com.c21genera.documents.infrastructure;

import com.c21genera.documents.domain.DocumentVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

  List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(UUID documentId);

  Optional<DocumentVersion> findByDocumentIdAndVersionNumber(UUID documentId, int versionNumber);
}
