package com.c21genera.operations.infrastructure;

import com.c21genera.operations.domain.ClosingOperation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ClosingOperationRepository extends JpaRepository<ClosingOperation, UUID> {

  List<ClosingOperation> findAllByOrderByUpdatedAtDesc();

  List<ClosingOperation> findByExpedienteIdOrderByCreatedAtDesc(UUID expedienteId);

  Optional<ClosingOperation> findFirstByExpedienteIdAndStatusNot(UUID expedienteId, ClosingOperation.Status status);

  @Query(value = "SELECT nextval('closing_operation_folio_seq')", nativeQuery = true)
  long nextFolioSequenceValue();
}
