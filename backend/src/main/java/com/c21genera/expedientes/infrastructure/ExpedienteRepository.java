package com.c21genera.expedientes.infrastructure;

import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.expedientes.domain.Expediente;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpedienteRepository extends JpaRepository<Expediente, UUID>, JpaSpecificationExecutor<Expediente> {

  Optional<Expediente> findByFolio(String folio);

  Page<Expediente> findByCreatedByUserId(UUID createdByUserId, Pageable pageable);

  Page<Expediente> findByStatus(ExpedienteStatus status, Pageable pageable);

  /** Cuántos expedientes hay en cada estatus (filas: estatus, total). */
  @Query("SELECT e.status, COUNT(e) FROM Expediente e GROUP BY e.status")
  List<Object[]> countAllByStatus();

  @Query("SELECT e.status, COUNT(e) FROM Expediente e WHERE e.createdByUserId = :userId GROUP BY e.status")
  List<Object[]> countOwnByStatus(@Param("userId") UUID userId);

  @Query(value = "SELECT nextval('expediente_folio_seq')", nativeQuery = true)
  long nextFolioSequenceValue();
}
