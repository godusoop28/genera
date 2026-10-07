package com.c21genera.expedientes.infrastructure;

import com.c21genera.expedientes.domain.ExpedienteParticipant;
import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.expedientes.domain.ParticipantRole;
import java.util.Collection;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpedienteParticipantRepository extends JpaRepository<ExpedienteParticipant, UUID> {

  List<ExpedienteParticipant> findByExpedienteIdOrderByOrdinalAsc(UUID expedienteId);

  /**
   * Titulares (propietario o copropietario) de los expedientes que siguen en
   * curso; filas: expedienteId, nombre completo. La comparación de nombres se
   * hace en Java con ClientNameKey (acentos, orden y puntuación).
   */
  @Query(
      "SELECT p.expedienteId, p.fullName FROM ExpedienteParticipant p, Expediente e"
          + " WHERE e.id = p.expedienteId AND p.role IN :roles AND e.status NOT IN :finished")
  List<Object[]> findHolderNames(@Param("roles") Collection<ParticipantRole> roles, @Param("finished") Collection<ExpedienteStatus> finished);
}
