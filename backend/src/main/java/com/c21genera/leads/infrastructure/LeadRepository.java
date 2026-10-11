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
}
