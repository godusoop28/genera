package com.c21genera.leads.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Cálculo puro del calendario de turnos (sin base de datos), siempre en la
 * zona de negocio America/Mexico_City, sin importar la del servidor o del
 * dispositivo.
 *
 * <ul>
 *   <li>Un turno es de una fecha (ONCE) o semanal (WEEKLY: días, desde y hasta
 *       opcional). Las fechas omitidas de una serie no generan turno.
 *   <li>Si la hora de fin es menor o igual a la de inicio, el turno cruza la
 *       medianoche y termina al día siguiente.
 *   <li>Las ausencias prevalecen: recortan cualquier turno que se les cruce.
 * </ul>
 */
public final class Schedule {

  public static final ZoneId ZONE = ZoneId.of("America/Mexico_City");

  private Schedule() {}

  public enum Kind {
    REGULAR,
    GUARD
  }

  public enum Recurrence {
    ONCE,
    WEEKLY
  }

  /** Regla de turno tal como está guardada. */
  public record Rule(
      UUID id,
      UUID advisorId,
      Kind kind,
      Recurrence recurrence,
      Set<DayOfWeek> days,
      LocalDate startDate,
      LocalDate endDate,
      LocalTime startTime,
      LocalTime endTime,
      boolean assignable,
      Set<LocalDate> skipped) {

    public boolean crossesMidnight() {
      return !endTime.isAfter(startTime);
    }

    /** ¿Genera turno que EMPIEZA en esta fecha? */
    public boolean occursOn(LocalDate date) {
      if (skipped.contains(date)) {
        return false;
      }
      if (recurrence == Recurrence.ONCE) {
        return date.equals(startDate);
      }
      return !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate)) && days.contains(date.getDayOfWeek());
    }
  }

  /** Un turno concreto en el tiempo. */
  public record Occurrence(UUID shiftId, UUID advisorId, Kind kind, boolean assignable, boolean recurring, LocalDate date, Instant start, Instant end) {

    public boolean contains(Instant t) {
      return !t.isBefore(start) && t.isBefore(end);
    }

    public LocalDateTime startLocal() {
      return LocalDateTime.ofInstant(start, ZONE);
    }

    public LocalDateTime endLocal() {
      return LocalDateTime.ofInstant(end, ZONE);
    }
  }

  public record Span(UUID advisorId, Instant start, Instant end) {

    public boolean contains(Instant t) {
      return !t.isBefore(start) && t.isBefore(end);
    }

    public boolean overlaps(Instant from, Instant to) {
      return start.isBefore(to) && end.isAfter(from);
    }
  }

  /** Turnos que empiezan en [from, toExclusive) — incluye los nocturnos del día anterior que siguen corriendo. */
  public static List<Occurrence> occurrences(List<Rule> rules, LocalDate from, LocalDate toExclusive) {
    List<Occurrence> out = new ArrayList<>();
    for (Rule r : rules) {
      for (LocalDate d = from.minusDays(1); d.isBefore(toExclusive); d = d.plusDays(1)) {
        if (!r.occursOn(d)) {
          continue;
        }
        Instant start = d.atTime(r.startTime()).atZone(ZONE).toInstant();
        Instant end = (r.crossesMidnight() ? d.plusDays(1) : d).atTime(r.endTime()).atZone(ZONE).toInstant();
        // El día anterior solo cuenta si su turno nocturno entra al rango.
        if (d.isBefore(from) && !end.isAfter(from.atStartOfDay(ZONE).toInstant())) {
          continue;
        }
        out.add(new Occurrence(r.id(), r.advisorId(), r.kind(), r.assignable(), r.recurrence() == Recurrence.WEEKLY, d, start, end));
      }
    }
    out.sort(Comparator.comparing(Occurrence::start));
    return out;
  }

  /** Partes de un turno que NO están cubiertas por ausencias del mismo asesor. */
  public static List<Span> effective(Occurrence o, List<Span> absences) {
    List<Span> parts = new ArrayList<>(List.of(new Span(o.advisorId(), o.start(), o.end())));
    for (Span a : absences) {
      if (!a.advisorId().equals(o.advisorId())) {
        continue;
      }
      List<Span> next = new ArrayList<>();
      for (Span p : parts) {
        if (!a.overlaps(p.start(), p.end())) {
          next.add(p);
          continue;
        }
        if (a.start().isAfter(p.start())) {
          next.add(new Span(p.advisorId(), p.start(), a.start()));
        }
        if (a.end().isBefore(p.end())) {
          next.add(new Span(p.advisorId(), a.end(), p.end()));
        }
      }
      parts = next;
    }
    return parts;
  }

  public static boolean absentAt(UUID advisorId, List<Span> absences, Instant t) {
    return absences.stream().anyMatch(a -> a.advisorId().equals(advisorId) && a.contains(t));
  }

  /** Turno (no ausente) del asesor que cubre el instante; prefiere los que participan en la asignación. */
  public static Optional<Occurrence> activeAt(UUID advisorId, List<Rule> rules, List<Span> absences, Instant t, boolean onlyAssignable) {
    if (absentAt(advisorId, absences, t)) {
      return Optional.empty();
    }
    LocalDate day = LocalDate.ofInstant(t, ZONE);
    return occurrences(rules.stream().filter(r -> r.advisorId().equals(advisorId)).toList(), day, day.plusDays(1)).stream()
        .filter(o -> o.contains(t))
        .filter(o -> !onlyAssignable || o.assignable())
        .min(Comparator.comparing((Occurrence o) -> !o.assignable()).thenComparing(Occurrence::start));
  }

  /**
   * Próximo momento (después de {@code after}) en que el asesor estará en un
   * turno sin ausencia, buscando hasta {@code horizonDays} días.
   */
  public static Optional<Instant> nextAvailable(UUID advisorId, List<Rule> rules, List<Span> absences, Instant after, int horizonDays, boolean onlyAssignable) {
    List<Rule> mine = rules.stream().filter(r -> r.advisorId().equals(advisorId) && (!onlyAssignable || r.assignable())).toList();
    if (mine.isEmpty()) {
      return Optional.empty();
    }
    LocalDate day = LocalDate.ofInstant(after, ZONE);
    for (Occurrence o : occurrences(mine, day, day.plusDays(horizonDays))) {
      if (!o.end().isAfter(after)) {
        continue;
      }
      for (Span part : effective(o, absences)) {
        Instant candidate = part.start().isAfter(after) ? part.start() : after;
        if (candidate.isBefore(part.end()) && part.start().isAfter(after)) {
          return Optional.of(candidate);
        }
      }
    }
    return Optional.empty();
  }

  /** Intervalos de [from, to) sin nadie cubriendo (a partir de las partes efectivas que participan en la asignación). */
  public static List<Span> gaps(List<Span> covered, Instant from, Instant to) {
    List<Span> sorted = covered.stream().filter(s -> s.overlaps(from, to)).sorted(Comparator.comparing(Span::start)).toList();
    List<Span> gaps = new ArrayList<>();
    Instant cursor = from;
    for (Span s : sorted) {
      if (s.start().isAfter(cursor)) {
        gaps.add(new Span(null, cursor, s.start()));
      }
      if (s.end().isAfter(cursor)) {
        cursor = s.end();
      }
      if (!cursor.isBefore(to)) {
        break;
      }
    }
    if (cursor.isBefore(to)) {
      gaps.add(new Span(null, cursor, to));
    }
    return gaps;
  }

  /**
   * ¿La regla nueva se traslapa con alguna existente del mismo asesor? Revisa
   * las fechas en común (hasta un año), así que detecta también series que se
   * cruzan solo algunos días o turnos nocturnos que invaden el día siguiente.
   */
  public static Optional<Occurrence> firstOverlap(Rule candidate, List<Rule> existing) {
    List<Rule> same = existing.stream().filter(r -> r.advisorId().equals(candidate.advisorId()) && !r.id().equals(candidate.id())).toList();
    if (same.isEmpty()) {
      return Optional.empty();
    }
    LocalDate from = candidate.startDate();
    LocalDate to = candidate.recurrence() == Recurrence.ONCE ? from.plusDays(1) : (candidate.endDate() != null ? candidate.endDate().plusDays(1) : from.plusDays(371));
    if (to.isAfter(from.plusDays(371))) {
      to = from.plusDays(371);
    }
    List<Occurrence> mine = occurrences(List.of(candidate), from, to);
    List<Occurrence> others = occurrences(same, from.minusDays(1), to.plusDays(1));
    for (Occurrence a : mine) {
      for (Occurrence b : others) {
        if (a.start().isBefore(b.end()) && b.start().isBefore(a.end())) {
          return Optional.of(b);
        }
      }
    }
    return Optional.empty();
  }
}
