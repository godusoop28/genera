package com.c21genera.leads.infrastructure;

import com.c21genera.leads.domain.Lead;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadRepository extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {

  Optional<Lead> findByRequestKey(String requestKey);

  Optional<Lead> findFirstByPhoneDigitsAndCreatedAtAfterOrderByCreatedAtDesc(String phoneDigits, Instant after);

  List<Lead> findByCreatedAtGreaterThanEqual(Instant from);

  List<Lead> findByCreatedAtGreaterThanEqualAndAdvisorUserId(Instant from, UUID advisorUserId);

  /** Leads asignados desde una fecha, por asesor (para la ruleta y el límite diario). */
  @Query("select l.advisorUserId, count(l) from Lead l where l.advisorUserId is not null and l.assignedAt >= :since group by l.advisorUserId")
  List<Object[]> countAssignedSince(@Param("since") Instant since);

  /**
   * Pendientes por antigüedad, bloqueados para este proceso: otra instancia o
   * un alta simultánea no puede tomar el mismo lead (SKIP LOCKED).
   */
  @Query(
      value = "select * from lead where assignment_status in ('PENDING_OWNER', 'PENDING_SCHEDULE') order by pending_since nulls first, created_at limit :limit for update skip locked",
      nativeQuery = true)
  List<Lead> lockPending(@Param("limit") int limit);

  long countByAssignmentStatusIn(List<com.c21genera.leads.AssignmentStatus> statuses);

  /** Control de Cambios #25: leads abiertos asignados a un asesor (antes de darle de baja). */
  long countByAdvisorUserIdAndStageIn(UUID advisorUserId, java.util.Collection<com.c21genera.leads.LeadStage> stages);
}
