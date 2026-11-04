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

  /**
   * Versiones cargadas entre {@code since} y {@code before} que siguen esperando: sin terminar de
   * procesar, o procesadas pero sin resultado de la revisión con IA (ver StuckDocumentWatchdog).
   */
  @org.springframework.data.jpa.repository.Query(
      "select v from DocumentVersion v where v.removedAt is null and v.uploadedAt > :since and v.uploadedAt < :before "
          + "and (v.processingStatus in :pending "
          + "or (v.processingStatus = :processed and v.aiAssessedAt is null and v.aiCheckFailed = false))")
  List<DocumentVersion> findWaiting(
      @org.springframework.data.repository.query.Param("since") java.time.Instant since,
      @org.springframework.data.repository.query.Param("before") java.time.Instant before,
      @org.springframework.data.repository.query.Param("pending") Collection<com.c21genera.documents.ProcessingStatus> pending,
      @org.springframework.data.repository.query.Param("processed") com.c21genera.documents.ProcessingStatus processed);
}
