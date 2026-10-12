package com.c21genera.leads.infrastructure;

import com.c21genera.leads.domain.AdvisorProfile;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AdvisorProfileRepository extends JpaRepository<AdvisorProfile, UUID> {

  Optional<AdvisorProfile> findByOnline21UserId(Long online21UserId);

  /**
   * Bloquea todos los perfiles mientras se decide una asignación: dos leads
   * simultáneos no pueden leer el mismo conteo ni el mismo estado de la ronda
   * (y así no se duplica la asignación ni se rebasa un límite).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from AdvisorProfile p order by p.userId")
  List<AdvisorProfile> lockAll();
}
