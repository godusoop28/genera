package com.c21genera.leads;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.leads.domain.Schedule;
import com.c21genera.leads.domain.Schedule.Kind;
import com.c21genera.leads.domain.Schedule.Occurrence;
import com.c21genera.leads.domain.Schedule.Recurrence;
import com.c21genera.leads.domain.Schedule.Rule;
import com.c21genera.leads.domain.Schedule.Span;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScheduleTest {

  // Semana del lunes 12/10/2026.
  private static final LocalDate MON = LocalDate.of(2026, 10, 12);
  private final UUID ana = UUID.randomUUID();
  private final UUID luis = UUID.randomUUID();
  private final UUID pedro = UUID.randomUUID();

  static Instant at(LocalDate d, int h, int m) {
    return LocalDateTime.of(d, LocalTime.of(h, m)).atZone(Schedule.ZONE).toInstant();
  }

  static Rule weekly(UUID advisor, Set<DayOfWeek> days, int sh, int sm, int eh, int em) {
    return new Rule(UUID.randomUUID(), advisor, Kind.REGULAR, Recurrence.WEEKLY, days, LocalDate.of(2026, 10, 1), null, LocalTime.of(sh, sm), LocalTime.of(eh, em), true, Set.of());
  }

  static Rule once(UUID advisor, Kind kind, LocalDate date, int sh, int eh) {
    return new Rule(UUID.randomUUID(), advisor, kind, Recurrence.ONCE, Set.of(), date, null, LocalTime.of(sh, 0), LocalTime.of(eh, 0), true, Set.of());
  }

  @Test
  void twoAdvisorsWithDifferentSchedulesAndAGuard() {
    Rule a = weekly(ana, EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), 9, 0, 14, 0);
    Rule l = weekly(luis, EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), 14, 0, 20, 0);
    Rule p = once(pedro, Kind.GUARD, MON.plusDays(5), 10, 16);
    List<Rule> rules = List.of(a, l, p);

    assertThat(Schedule.activeAt(ana, rules, List.of(), at(MON, 10, 0), true)).isPresent();
    assertThat(Schedule.activeAt(ana, rules, List.of(), at(MON.plusDays(1), 10, 0), true)).isEmpty(); // martes no trabaja
    assertThat(Schedule.activeAt(luis, rules, List.of(), at(MON, 14, 0), true)).isPresent(); // 14:00 ya es de Luis
    assertThat(Schedule.activeAt(ana, rules, List.of(), at(MON, 14, 0), true)).isEmpty(); // fin exclusivo
    assertThat(Schedule.activeAt(pedro, rules, List.of(), at(MON.plusDays(5), 12, 0), true)).get().extracting(Occurrence::kind).isEqualTo(Kind.GUARD);
    assertThat(Schedule.occurrences(rules, MON, MON.plusDays(7))).hasSize(3 + 5 + 1);
  }

  @Test
  void splitShiftsOnTheSameDay() {
    Rule morning = weekly(ana, EnumSet.of(DayOfWeek.MONDAY), 9, 0, 13, 0);
    Rule evening = weekly(ana, EnumSet.of(DayOfWeek.MONDAY), 16, 0, 19, 0);
    List<Rule> rules = List.of(morning, evening);
    assertThat(Schedule.activeAt(ana, rules, List.of(), at(MON, 12, 0), true)).isPresent();
    assertThat(Schedule.activeAt(ana, rules, List.of(), at(MON, 14, 30), true)).isEmpty();
    assertThat(Schedule.activeAt(ana, rules, List.of(), at(MON, 17, 0), true)).isPresent();
    assertThat(Schedule.nextAvailable(ana, rules, List.of(), at(MON, 14, 30), 7, true)).contains(at(MON, 16, 0));
    assertThat(Schedule.firstOverlap(morning, List.of(evening))).isEmpty();
  }

  @Test
  void nightShiftCrossesMidnight() {
    Rule night = weekly(luis, EnumSet.of(DayOfWeek.FRIDAY), 22, 0, 6, 0);
    List<Rule> rules = List.of(night);
    LocalDate fri = MON.plusDays(4);
    assertThat(Schedule.activeAt(luis, rules, List.of(), at(fri, 23, 0), true)).isPresent();
    // Sábado 3:00: sigue el turno que empezó el viernes.
    assertThat(Schedule.activeAt(luis, rules, List.of(), at(fri.plusDays(1), 3, 0), true)).get().extracting(Occurrence::date).isEqualTo(fri);
    assertThat(Schedule.activeAt(luis, rules, List.of(), at(fri.plusDays(1), 7, 0), true)).isEmpty();
  }

  @Test
  void absencePrevailsOverRecurringShift() {
    Rule a = weekly(ana, EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), 9, 0, 14, 0);
    // Vacaciones lunes completo.
    Span vacation = new Span(ana, at(MON, 0, 0), at(MON.plusDays(1), 0, 0));
    assertThat(Schedule.activeAt(ana, List.of(a), List.of(vacation), at(MON, 10, 0), true)).isEmpty();
    // El próximo turno disponible es el miércoles.
    assertThat(Schedule.nextAvailable(ana, List.of(a), List.of(vacation), at(MON, 10, 0), 7, true)).contains(at(MON.plusDays(2), 9, 0));
    // Permiso de 11 a 12: el turno se parte en dos.
    Span permit = new Span(ana, at(MON, 11, 0), at(MON, 12, 0));
    Occurrence o = Schedule.occurrences(List.of(a), MON, MON.plusDays(1)).get(0);
    assertThat(Schedule.effective(o, List.of(permit))).hasSize(2);
  }

  @Test
  void skippedDateDoesNotGenerateTheShift() {
    Rule base = weekly(ana, EnumSet.of(DayOfWeek.MONDAY), 9, 0, 14, 0);
    Rule skipped = new Rule(base.id(), ana, Kind.REGULAR, Recurrence.WEEKLY, base.days(), base.startDate(), null, base.startTime(), base.endTime(), true, Set.of(MON));
    assertThat(Schedule.activeAt(ana, List.of(skipped), List.of(), at(MON, 10, 0), true)).isEmpty();
    assertThat(Schedule.activeAt(ana, List.of(skipped), List.of(), at(MON.plusDays(7), 10, 0), true)).isPresent();
  }

  @Test
  void detectsOverlapsOfTheSameAdvisorOnly() {
    Rule a = weekly(ana, EnumSet.of(DayOfWeek.MONDAY), 9, 0, 14, 0);
    Rule clash = once(ana, Kind.GUARD, MON.plusDays(7), 13, 15);
    Rule otherAdvisor = once(luis, Kind.REGULAR, MON.plusDays(7), 9, 14);
    Rule night = new Rule(UUID.randomUUID(), ana, Kind.REGULAR, Recurrence.ONCE, Set.of(), MON.minusDays(1), null, LocalTime.of(22, 0), LocalTime.of(10, 0), true, Set.of());
    assertThat(Schedule.firstOverlap(clash, List.of(a))).isPresent();
    assertThat(Schedule.firstOverlap(otherAdvisor, List.of(a))).isEmpty();
    // Turno nocturno del domingo que invade el lunes a las 9.
    assertThat(Schedule.firstOverlap(night, List.of(a))).isPresent();
  }

  @Test
  void findsCoverageGaps() {
    Instant from = at(MON, 8, 0);
    Instant to = at(MON, 20, 0);
    List<Span> covered = List.of(new Span(ana, at(MON, 9, 0), at(MON, 14, 0)), new Span(luis, at(MON, 13, 0), at(MON, 18, 0)));
    List<Span> gaps = Schedule.gaps(covered, from, to);
    assertThat(gaps).extracting(Span::start).containsExactly(at(MON, 8, 0), at(MON, 18, 0));
    assertThat(gaps).extracting(Span::end).containsExactly(at(MON, 9, 0), at(MON, 20, 0));
  }
}
