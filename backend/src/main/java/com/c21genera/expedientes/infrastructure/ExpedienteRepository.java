package com.c21genera.expedientes.infrastructure;

import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.expedientes.domain.Expediente;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExpedienteRepository extends JpaRepository<Expediente, UUID> {

  Optional<Expediente> findByFolio(String folio);

  Page<Expediente> findByCreatedByUserId(UUID createdByUserId, Pageable pageable);

  Page<Expediente> findByStatus(ExpedienteStatus status, Pageable pageable);

  @Query(value = "SELECT nextval('expediente_folio_seq')", nativeQuery = true)
  long nextFolioSequenceValue();
}
