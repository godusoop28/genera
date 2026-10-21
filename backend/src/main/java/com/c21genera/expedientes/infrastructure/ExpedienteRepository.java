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

  Page<Expediente> findByAssignedAdvisorId(UUID assignedAdvisorId, Pageable pageable);

  Page<Expediente> findByStatus(ExpedienteStatus status, Pageable pageable);

  List<Expediente> findByStatusIn(java.util.Collection<ExpedienteStatus> statuses);

  /**
   * Control de Cambios #24/#26: cartera agregada (filas: tratamiento del inmueble, operación, asesor
   * asignado, nombre del asesor, estatus, total, categoría, subcategoría).
   */
  @Query(
      "SELECT e.propertyCaseType, e.operationType, e.assignedAdvisorId, e.assignedAdvisorName, e.status, COUNT(e), e.propertyCategoryId, e.propertySubtypeId"
          + " FROM Expediente e"
          + " GROUP BY e.propertyCaseType, e.operationType, e.assignedAdvisorId, e.assignedAdvisorName, e.status, e.propertyCategoryId, e.propertySubtypeId")
  List<Object[]> portfolioRows();

  @Query(
      "SELECT e.propertyCaseType, e.operationType, e.assignedAdvisorId, e.assignedAdvisorName, e.status, COUNT(e), e.propertyCategoryId, e.propertySubtypeId"
          + " FROM Expediente e WHERE e.assignedAdvisorId = :userId"
          + " GROUP BY e.propertyCaseType, e.operationType, e.assignedAdvisorId, e.assignedAdvisorName, e.status, e.propertyCategoryId, e.propertySubtypeId")
  List<Object[]> portfolioRowsOf(@Param("userId") UUID userId);

  /** Control de Cambios #25: expedientes en curso asignados a un asesor (antes de darle de baja). */
  long countByAssignedAdvisorIdAndStatusNotIn(UUID assignedAdvisorId, java.util.Collection<ExpedienteStatus> statuses);

  @Query(value = "SELECT nextval('expediente_folio_seq')", nativeQuery = true)
  long nextFolioSequenceValue();
}
