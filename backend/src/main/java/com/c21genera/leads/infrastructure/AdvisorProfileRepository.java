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
   * Bloquea las filas de quienes reciben leads mientras se elige al asesor:
   * dos leads simultáneos no pueden leer el mismo conteo y caer en la misma
   * persona por una condición de carrera.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from AdvisorProfile p where p.receivesLeads = true order by p.userId")
  List<AdvisorProfile> lockReceiving();
}
