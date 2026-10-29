package com.c21genera.documents.infrastructure;

import com.c21genera.documents.domain.DocumentVersion;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

  List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(UUID documentId);

  /** Sin las eliminadas: la primera es la vigente. */
  List<DocumentVersion> findByDocumentIdAndRemovedAtIsNullOrderByVersionNumberDesc(UUID documentId);

  /** Versiones no eliminadas de varios documentos a la vez (la lista de un expediente, en una consulta). */
  List<DocumentVersion> findByDocumentIdInAndRemovedAtIsNull(Collection<UUID> documentIds);

  Optional<DocumentVersion> findByDocumentIdAndVersionNumber(UUID documentId, int versionNumber);
}
