package com.c21genera.leads.application;

import com.c21genera.identity.UserDirectory.StaffMember;
import com.c21genera.leads.domain.AdvisorProfile;
import com.c21genera.leads.domain.Absence;
import com.c21genera.leads.domain.Schedule;
import com.c21genera.leads.domain.Schedule.Occurrence;
import com.c21genera.leads.domain.Schedule.Rule;
import com.c21genera.leads.domain.Schedule.Span;
import com.c21genera.leads.domain.Shift;
import com.c21genera.leads.domain.ShiftSkip;
import com.c21genera.leads.infrastructure.AbsenceRepository;
import com.c21genera.leads.infrastructure.AdvisorProfileRepository;
import com.c21genera.leads.infrastructure.LeadRepository;
import com.c21genera.leads.infrastructure.ShiftRepository;
import com.c21genera.leads.infrastructure.ShiftSkipRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Disponibilidad calculada en el servidor con el calendario (fuente única),
 * las ausencias, la pausa y el límite diario, siempre con la fecha y hora de
 * Ciudad de México. Se lee de la base en cada consulta: un cambio de
 * calendario afecta la siguiente asignación sin reiniciar nada.
 */
@Service
@Transactional(readOnly = true)
public class AvailabilityService {

  /** Días hacia adelante que se buscan para "próximo turno". */
  static final int HORIZON_DAYS = 21;

  public enum Status {
    AVAILABLE,
    OFF_SHIFT,
    ABSENT,
    PAUSED,
    LIMIT_REACHED,
    NO_CALENDAR
  }

  /** Foto de todo lo necesario para decidir en un instante. */
  public record Snapshot(
      Instant now,
      List<Rule> rules,
      List<Span> absences,
      Map<UUID, AdvisorProfile> profiles,
      Map<UUID, StaffMember> staff,
      Map<UUID, Integer> today) {

    public boolean active(UUID userId) {
      return staff.containsKey(userId);
    }
  }

  public record AdvisorState(
      UUID userId, Status status, Occurrence currentShift, boolean inAnyShift, Instant nextShiftAt, int assignedToday, Integer dailyLimit, String detail) {}

  private final ShiftRepository shifts;
  private final ShiftSkipRepository skips;
  private final AbsenceRepository absences;
  private final AdvisorProfileRepository profiles;
  private final LeadRepository leads;
  private final StaffNames staff;
  private final Clock clock;

  AvailabilityService(
      ShiftRepository shifts,
      ShiftSkipRepository skips,
      AbsenceRepository absences,
      AdvisorProfileRepository profiles,
      LeadRepository leads,
      StaffNames staff,
      Clock clock) {
    this.shifts = shifts;
    this.skips = skips;
    this.absences = absences;
    this.profiles = profiles;
    this.leads = leads;
    this.staff = staff;
    this.clock = clock;
  }

  public Instant now() {
    return clock.instant();
  }

  /** Reglas vigentes (no eliminadas) con sus fechas omitidas. */
  List<Rule> rules() {
    Map<UUID, Set<LocalDate>> skipped = new HashMap<>();
    for (ShiftSkip skip : skips.findAll()) {
      skipped.computeIfAbsent(skip.getShiftId(), k -> new HashSet<>()).add(skip.getOccurrenceDate());
    }
    return shifts.findByDeletedAtIsNull().stream().map(s -> s.toRule(skipped.getOrDefault(s.getId(), Set.of()))).toList();
  }

  List<Span> absenceSpans(Instant after) {
    return absences.findByDeletedAtIsNullAndEndsAtAfter(after).stream().map(Absence::toSpan).toList();
  }

  /** @param lockedProfiles perfiles ya bloqueados por quien asigna (o null para leerlos sin bloqueo). */
  Snapshot snapshot(List<AdvisorProfile> lockedProfiles) {
    Instant now = clock.instant();
    List<AdvisorProfile> list = lockedProfiles != null ? lockedProfiles : profiles.findAll();
    return new Snapshot(
        now,
        rules(),
        absenceSpans(now.minus(Duration.ofDays(1))),
        list.stream().collect(Collectors.toMap(AdvisorProfile::getUserId, Function.identity())),
        staff.activeStaff().stream().collect(Collectors.toMap(StaffMember::id, Function.identity())),
        todayCounts(now));
  }

  /** Leads asignados hoy (fecha de Ciudad de México) por asesor. */
  Map<UUID, Integer> todayCounts(Instant now) {
    Instant startOfDay = LocalDate.ofInstant(now, Schedule.ZONE).atStartOfDay(Schedule.ZONE).toInstant();
    Map<UUID, Integer> counts = new HashMap<>();
    for (Object[] row : leads.countAssignedSince(startOfDay)) {
      counts.put((UUID) row[0], ((Number) row[1]).intValue());
    }
    return counts;
  }

  /** Estado de un asesor para la ronda (turno habilitado para asignación). */
  AdvisorState state(Snapshot s, UUID userId) {
    AdvisorProfile p = s.profiles().get(userId);
    int today = s.today().getOrDefault(userId, 0);
    Integer limit = p != null ? p.getDailyLimit() : null;
    boolean hasRules = s.rules().stream().anyMatch(r -> r.advisorId().equals(userId));
    Optional<Occurrence> assignable = Schedule.activeAt(userId, s.rules(), s.absences(), s.now(), true);
    boolean inAny = Schedule.activeAt(userId, s.rules(), s.absences(), s.now(), false).isPresent();
    Instant next = assignable.isPresent() ? null : Schedule.nextAvailable(userId, s.rules(), s.absences(), s.now(), HORIZON_DAYS, true).orElse(null);
    Status status;
    String detail;
    if (p == null || p.pausedAt(s.now())) {
      status = Status.PAUSED;
      detail = p == null ? "No está configurado para recibir leads" : "En pausa: no recibe leads";
    } else if (!hasRules) {
      status = Status.NO_CALENDAR;
      detail = "No tiene turnos en el calendario";
    } else if (Schedule.absentAt(userId, s.absences(), s.now())) {
      status = Status.ABSENT;
      detail = "Ausente";
    } else if (assignable.isEmpty()) {
      status = Status.OFF_SHIFT;
      detail = inAny ? "En turno que no participa en la asignación automática" : "Fuera de horario";
    } else if (limit != null && today >= limit) {
      status = Status.LIMIT_REACHED;
      detail = "Alcanzó su límite diario (" + limit + ")";
    } else {
      status = Status.AVAILABLE;
      detail = "Disponible";
    }
    return new AdvisorState(userId, status, assignable.orElse(null), inAny, next, today, limit, detail);
  }

  /** Próximo inicio de turno habilitado de cualquier asesor que pueda recibir leads. */
  Optional<Instant> nextCoverage(Snapshot s) {
    return s.profiles().values().stream()
        .filter(p -> s.active(p.getUserId()) && p.isReceivesLeads())
        .map(p -> Schedule.nextAvailable(p.getUserId(), s.rules(), s.absences(), s.now(), HORIZON_DAYS, true))
        .flatMap(Optional::stream)
        .min(Instant::compareTo);
  }

  /** Uso interno de CalendarService. */
  List<Shift> shiftsOf(UUID advisorId) {
    return shifts.findByAdvisorUserIdAndDeletedAtIsNull(advisorId);
  }
}
