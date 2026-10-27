package com.c21genera.receipts.infrastructure;

import com.c21genera.receipts.domain.DocumentReceipt;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentReceiptRepository extends JpaRepository<DocumentReceipt, UUID> {

  List<DocumentReceipt> findByExpedienteIdOrderBySequenceNumberDesc(UUID expedienteId);

  @Query("select coalesce(max(r.sequenceNumber), 0) from DocumentReceipt r where r.expedienteId = :expedienteId")
  int maxSequence(@Param("expedienteId") UUID expedienteId);
}
