package com.c21genera.leads.application;

import com.c21genera.identity.UserDirectory.StaffMember;
import com.c21genera.leads.AssignmentMethod;
import com.c21genera.leads.domain.AdvisorProfile;
import com.c21genera.leads.domain.AssignmentEngine;
import com.c21genera.leads.domain.AssignmentEngine.Candidate;
import com.c21genera.leads.infrastructure.AdvisorProfileRepository;
import com.c21genera.leads.infrastructure.LeadRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Elige al asesor de un lead con las reglas de la empresa (ver {@link AssignmentEngine}). */
@Service
class AssignmentService {

  record Assignment(UUID advisorUserId, AssignmentMethod method) {}

  private final AdvisorProfileRepository profiles;
  private final LeadRepository leads;
  private final StaffNames staff;
  private final Clock clock;

  AssignmentService(AdvisorProfileRepository profiles, LeadRepository leads, StaffNames staff, Clock clock) {
    this.profiles = profiles;
    this.leads = leads;
    this.staff = staff;
    this.clock = clock;
  }

  /**
   * Debe llamarse dentro de una transacción: bloquea los perfiles de la ruleta
   * hasta que se guarda el lead (salvo en simulación).
   */
  Optional<Assignment> choose(Long propertyAdvisorOnline21Id, boolean dryRun) {
    Set<UUID> active = staff.activeStaff().stream().map(StaffMember::id).collect(Collectors.toSet());

    if (propertyAdvisorOnline21Id != null) {
      Optional<AdvisorProfile> owner = profiles.findByOnline21UserId(propertyAdvisorOnline21Id);
      if (owner.isPresent() && active.contains(owner.get().getUserId())) {
        return Optional.of(new Assignment(owner.get().getUserId(), AssignmentMethod.PROPERTY_OWNER));
      }
    }

    List<AdvisorProfile> receiving =
        dryRun ? profiles.findAll().stream().filter(AdvisorProfile::isReceivesLeads).toList() : profiles.lockReceiving();
    List<Candidate> candidates =
        receiving.stream()
            .filter(p -> active.contains(p.getUserId()))
            .map(p -> new Candidate(p.getUserId(), p.isReceivesLeads(), p.getWeight(), p.getDailyLimit(), p.getShiftStart(), p.getShiftEnd(), p.getPausedUntil()))
            .toList();
    Instant now = clock.instant();
    ZonedDateTime local = now.atZone(StaffNames.MEXICO_CITY);
    Map<UUID, Integer> today = todayCounts(local);
    return AssignmentEngine.pickRoulette(candidates, today, now, local.toLocalTime(), n -> ThreadLocalRandom.current().nextInt(n))
        .map(id -> new Assignment(id, AssignmentMethod.ROULETTE));
  }

  /** Leads asignados hoy (hora de Ciudad de México) por asesor. */
  Map<UUID, Integer> todayCounts(ZonedDateTime local) {
    Instant startOfDay = local.toLocalDate().atStartOfDay(StaffNames.MEXICO_CITY).toInstant();
    Map<UUID, Integer> counts = new HashMap<>();
    for (Object[] row : leads.countAssignedSince(startOfDay)) {
      counts.put((UUID) row[0], ((Number) row[1]).intValue());
    }
    return counts;
  }
}
