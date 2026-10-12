package com.c21genera.leads.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.c21genera.identity.UserDirectory.StaffMember;
import com.c21genera.leads.AssignmentMethod;
import com.c21genera.leads.AssignmentStatus;
import com.c21genera.leads.LeadInterest;
import com.c21genera.leads.LeadSource;
import com.c21genera.leads.application.AssignmentService.Decision;
import com.c21genera.leads.domain.Absence;
import com.c21genera.leads.domain.AdvisorProfile;
import com.c21genera.leads.domain.AssignmentEngine.Mode;
import com.c21genera.leads.domain.AssignmentSettings;
import com.c21genera.leads.domain.AssignmentSettings.OwnerOffShiftPolicy;
import com.c21genera.leads.domain.Lead;
import com.c21genera.leads.domain.Schedule;
import com.c21genera.leads.domain.Schedule.Kind;
import com.c21genera.leads.domain.Schedule.Recurrence;
import com.c21genera.leads.domain.Shift;
import com.c21genera.leads.infrastructure.AbsenceRepository;
import com.c21genera.leads.infrastructure.AdvisorProfileRepository;
import com.c21genera.leads.infrastructure.AssignmentSettingsRepository;
import com.c21genera.leads.infrastructure.LeadRepository;
import com.c21genera.leads.infrastructure.ShiftRepository;
import com.c21genera.leads.infrastructure.ShiftSkipRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Escenarios de negocio del servicio único de asignación, con repositorios
 * simulados y reloj controlado (no prueban la base de datos real ni el
 * bloqueo de filas: eso se verifica en el despliegue).
 */
class AssignmentScenariosTest {

  // Lunes 12/10/2026 (hora de Ciudad de México).
  private static final LocalDate MON = LocalDate.of(2026, 10, 12);

  private final UUID ana = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private final UUID luis = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
  private final UUID pedro = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

  private final List<Shift> shifts = new ArrayList<>();
  private final List<Absence> absences = new ArrayList<>();
  private final Map<UUID, AdvisorProfile> profiles = new HashMap<>();
  private final Map<UUID, Integer> assignedToday = new HashMap<>();
  private final List<StaffMember> staffList = new ArrayList<>();
  private final MutableClock clock = new MutableClock(at(MON, 10, 0));
  private AssignmentSettings settings;
  private AssignmentService service;

  static Instant at(LocalDate d, int h, int m) {
    return LocalDateTime.of(d, LocalTime.of(h, m)).atZone(Schedule.ZONE).toInstant();
  }

  @BeforeEach
  void setUp() {
    ShiftRepository shiftRepo = mock(ShiftRepository.class);
    when(shiftRepo.findByDeletedAtIsNull()).thenAnswer(i -> shifts);
    ShiftSkipRepository skipRepo = mock(ShiftSkipRepository.class);
    when(skipRepo.findAll()).thenReturn(List.of());
    AbsenceRepository absenceRepo = mock(AbsenceRepository.class);
    when(absenceRepo.findByDeletedAtIsNullAndEndsAtAfter(any())).thenAnswer(i -> absences);
    AdvisorProfileRepository profileRepo = mock(AdvisorProfileRepository.class);
    when(profileRepo.findAll()).thenAnswer(i -> new ArrayList<>(profiles.values()));
    when(profileRepo.lockAll()).thenAnswer(i -> new ArrayList<>(profiles.values()));
    LeadRepository leadRepo = mock(LeadRepository.class);
    when(leadRepo.countAssignedSince(any())).thenAnswer(i -> {
      List<Object[]> rows = new ArrayList<>();
      assignedToday.forEach((id, n) -> rows.add(new Object[] {id, (long) n}));
      return rows;
    });
    StaffNames staff = mock(StaffNames.class);
    when(staff.activeStaff()).thenAnswer(i -> staffList);
    settings = mock(AssignmentSettings.class);
    when(settings.getRoundMode()).thenReturn(Mode.EQUAL);
    when(settings.getOwnerOffShiftPolicy()).thenReturn(OwnerOffShiftPolicy.RESPECT_OWNER);
    AssignmentSettingsRepository settingsRepo = mock(AssignmentSettingsRepository.class);
    when(settingsRepo.findById((short) 1)).thenAnswer(i -> Optional.of(settings));

    AvailabilityService availability = new AvailabilityService(shiftRepo, skipRepo, absenceRepo, profileRepo, leadRepo, staff, clock);
    service = new AssignmentService(profileRepo, settingsRepo, availability);

    advisor(ana, 5, null, 1001L);
    advisor(luis, 5, null, 1002L);
    advisor(pedro, 5, null, null);
  }

  private void advisor(UUID id, int weight, Integer limit, Long online21) {
    AdvisorProfile p = new AdvisorProfile(id);
    p.update(true, weight, limit, online21, null);
    profiles.put(id, p);
    staffList.add(new StaffMember(id, "Asesor " + id.toString().substring(34), id + "@test", "ADVISOR"));
  }

  private void weekly(UUID advisor, Set<DayOfWeek> days, int sh, int eh, Kind kind) {
    Shift s = new Shift(advisor, null, "prueba");
    s.define(kind, Recurrence.WEEKLY, days, LocalDate.of(2026, 10, 1), null, LocalTime.of(sh, 0), LocalTime.of(eh, 0), true, null);
    shifts.add(s);
  }

  private static final Set<DayOfWeek> WEEKDAYS = EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

  @Test
  void ownerOnShiftGetsThePropertyLead() {
    weekly(ana, WEEKDAYS, 9, 14, Kind.REGULAR);
    weekly(luis, WEEKDAYS, 9, 18, Kind.REGULAR);
    Decision d = service.decideNew(1001L, false);
    assertThat(d.status()).isEqualTo(AssignmentStatus.ASSIGNED);
    assertThat(d.advisorId()).isEqualTo(ana);
    assertThat(d.reason()).isEqualTo(AssignmentMethod.PROPERTY_OWNER);
    assertThat(d.shiftId()).isNotNull();
  }

  @Test
  void ownerOffShiftWithRespectPolicyStaysReservedUntilNextShift() {
    weekly(ana, WEEKDAYS, 15, 19, Kind.REGULAR); // Ana entra a las 15:00
    weekly(luis, WEEKDAYS, 9, 18, Kind.REGULAR);
    Decision d = service.decideNew(1001L, false);
    assertThat(d.status()).isEqualTo(AssignmentStatus.PENDING_OWNER);
    assertThat(d.advisorId()).isEqualTo(ana);
    assertThat(d.nextShiftAt()).isEqualTo(at(MON, 15, 0));
    assertThat(d.pendingReason()).contains("fuera de horario");
  }

  @Test
  void ownerOffShiftWithGuardPolicyRoutesToTheGuardKeepingOwnerReference() {
    when(settings.getOwnerOffShiftPolicy()).thenReturn(OwnerOffShiftPolicy.ROUTE_TO_GUARD);
    weekly(ana, WEEKDAYS, 15, 19, Kind.REGULAR);
    weekly(luis, WEEKDAYS, 9, 18, Kind.REGULAR);
    weekly(pedro, WEEKDAYS, 9, 18, Kind.GUARD);
    Decision d = service.decideNew(1001L, false);
    assertThat(d.status()).isEqualTo(AssignmentStatus.ASSIGNED);
    assertThat(d.advisorId()).isEqualTo(pedro); // prefiere la guardia
    assertThat(d.reason()).isEqualTo(AssignmentMethod.GUARD);
    assertThat(d.propertyAdvisorUserId()).isEqualTo(ana);
  }

  @Test
  void unlinkedOwnerFallsBackToTheRoundWithVisibleReason() {
    weekly(luis, WEEKDAYS, 9, 18, Kind.REGULAR);
    Decision d = service.decideNew(9999L, false);
    assertThat(d.advisorId()).isEqualTo(luis);
    assertThat(d.reason()).isEqualTo(AssignmentMethod.ROULETTE);
    assertThat(d.detail()).contains("no está vinculado");
  }

  @Test
  void pausedOwnerIsNotWaitedFor() {
    weekly(ana, WEEKDAYS, 9, 14, Kind.REGULAR);
    weekly(luis, WEEKDAYS, 9, 18, Kind.REGULAR);
    profiles.get(ana).update(false, 5, null, 1001L, null);
    Decision d = service.decideNew(1001L, false);
    assertThat(d.advisorId()).isEqualTo(luis);
    assertThat(d.detail()).contains("pausa");
    assertThat(d.propertyAdvisorUserId()).isEqualTo(ana);
  }

  @Test
  void absenceRemovesAdvisorFromTheRound() {
    weekly(ana, WEEKDAYS, 9, 14, Kind.REGULAR);
    weekly(luis, WEEKDAYS, 9, 18, Kind.REGULAR);
    Absence vacation = new Absence(luis, "prueba");
    vacation.define(luis, Absence.Kind.VACATION, at(MON, 0, 0), at(MON.plusDays(1), 0, 0), null);
    absences.add(vacation);
    for (int i = 0; i < 3; i++) {
      assertThat(service.decideNew(null, false).advisorId()).isEqualTo(ana);
    }
  }

  @Test
  void equalRoundDistributesAmongSimultaneousAdvisors() {
    weekly(ana, WEEKDAYS, 9, 18, Kind.REGULAR);
    weekly(luis, WEEKDAYS, 9, 18, Kind.REGULAR);
    weekly(pedro, WEEKDAYS, 9, 18, Kind.REGULAR);
    Map<UUID, Integer> counts = new HashMap<>();
    for (int i = 0; i < 9; i++) {
      clock.set(at(MON, 10, i));
      counts.merge(service.decideNew(null, false).advisorId(), 1, Integer::sum);
    }
    assertThat(counts).containsEntry(ana, 3).containsEntry(luis, 3).containsEntry(pedro, 3);
  }

  @Test
  void weightedRoundRespectsWeights() {
    when(settings.getRoundMode()).thenReturn(Mode.WEIGHTED);
    profiles.get(ana).update(true, 10, null, 1001L, null);
    weekly(ana, WEEKDAYS, 9, 18, Kind.REGULAR);
    weekly(luis, WEEKDAYS, 9, 18, Kind.REGULAR);
    Map<UUID, Integer> counts = new HashMap<>();
    for (int i = 0; i < 15; i++) {
      counts.merge(service.decideNew(null, false).advisorId(), 1, Integer::sum);
    }
    assertThat(counts).containsEntry(ana, 10).containsEntry(luis, 5);
  }

  @Test
  void dailyLimitIsCountedWithMexicoCityDate() {
    weekly(ana, WEEKDAYS, 9, 18, Kind.REGULAR);
    weekly(luis, WEEKDAYS, 9, 18, Kind.REGULAR);
    profiles.get(ana).update(true, 5, 2, 1001L, null);
    assignedToday.put(ana, 2);
    assertThat(service.decideNew(null, false).advisorId()).isEqualTo(luis);
    assertThat(service.decideNew(1001L, false).advisorId()).isEqualTo(ana); // el dueño de la propiedad no se limita por cupo
  }

  @Test
  void nobodyAvailableLeavesItPendingAndItIsAssignedWhenTheShiftStarts() {
    weekly(luis, WEEKDAYS, 14, 20, Kind.REGULAR);
    clock.set(at(MON, 8, 0));
    Decision d = service.decideNew(null, false);
    assertThat(d.status()).isEqualTo(AssignmentStatus.PENDING_SCHEDULE);
    assertThat(d.advisorId()).isNull();
    assertThat(d.nextShiftAt()).isEqualTo(at(MON, 14, 0));

    Lead lead = new Lead("Cliente", "+52 777 000 0000", LeadSource.WHATSAPP_BOT, null, LeadInterest.BUY, clock.instant());
    lead.pending(d.status(), null, d.rule(), d.pendingReason(), d.nextShiftAt(), clock.instant());
    clock.set(at(MON, 14, 5));
    Decision later = service.decidePending(lead, service.lockedSnapshot());
    assertThat(later.status()).isEqualTo(AssignmentStatus.ASSIGNED);
    assertThat(later.advisorId()).isEqualTo(luis);
  }

  @Test
  void reservedOwnerLeadIsAssignedWhenTheOwnerStarts() {
    weekly(ana, WEEKDAYS, 15, 19, Kind.REGULAR);
    Decision d = service.decideNew(1001L, false);
    Lead lead = new Lead("Cliente", "+52 777 000 0001", LeadSource.WHATSAPP_BOT, null, LeadInterest.BUY, clock.instant());
    lead.pending(d.status(), d.advisorId(), d.rule(), d.pendingReason(), d.nextShiftAt(), clock.instant());
    clock.set(at(MON, 15, 1));
    Decision later = service.decidePending(lead, service.lockedSnapshot());
    assertThat(later.status()).isEqualTo(AssignmentStatus.ASSIGNED);
    assertThat(later.advisorId()).isEqualTo(ana);
    assertThat(later.reason()).isEqualTo(AssignmentMethod.PROPERTY_OWNER);
  }

  @Test
  void noUpcomingShiftAtAllHasNoNextCoverage() {
    Decision d = service.decideNew(null, false);
    assertThat(d.status()).isEqualTo(AssignmentStatus.PENDING_SCHEDULE);
    assertThat(d.nextShiftAt()).isNull();
    assertThat(d.pendingReason()).contains("sin calendario");
  }

  @Test
  void simulatorDoesNotMoveTheRound() {
    weekly(ana, WEEKDAYS, 9, 18, Kind.REGULAR);
    weekly(luis, WEEKDAYS, 9, 18, Kind.REGULAR);
    UUID first = service.decideNew(null, true).advisorId();
    UUID second = service.decideNew(null, true).advisorId();
    assertThat(second).isEqualTo(first);
    assertThat(profiles.get(first).getRoundLastAt()).isNull();
  }

  /** Reloj que la prueba puede adelantar. */
  static final class MutableClock extends Clock {
    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void set(Instant now) {
      this.now = now;
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
