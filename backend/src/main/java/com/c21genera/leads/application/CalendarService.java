package com.c21genera.leads.application;

import com.c21genera.identity.CurrentUser;
import com.c21genera.identity.UserDirectory.StaffMember;
import com.c21genera.leads.application.AvailabilityService.AdvisorState;
import com.c21genera.leads.application.AvailabilityService.Snapshot;
import com.c21genera.leads.domain.Absence;
import com.c21genera.leads.domain.AdvisorProfile;
import com.c21genera.leads.domain.CalendarAudit;
import com.c21genera.leads.domain.Schedule;
import com.c21genera.leads.domain.Schedule.Kind;
import com.c21genera.leads.domain.Schedule.Occurrence;
import com.c21genera.leads.domain.Schedule.Recurrence;
import com.c21genera.leads.domain.Schedule.Rule;
import com.c21genera.leads.domain.Schedule.Span;
import com.c21genera.leads.domain.Shift;
import com.c21genera.leads.domain.ShiftSkip;
import com.c21genera.leads.infrastructure.AbsenceRepository;
import com.c21genera.leads.infrastructure.AdvisorProfileRepository;
import com.c21genera.leads.infrastructure.CalendarAuditRepository;
import com.c21genera.leads.infrastructure.ShiftRepository;
import com.c21genera.leads.infrastructure.ShiftSkipRepository;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calendario de turnos, guardias y ausencias. Es la ÚNICA fuente de
 * disponibilidad para asignar leads. Todo se expresa en hora de Ciudad de
 * México (America/Mexico_City); el servidor hace los cálculos.
 *
 * <p>Editar o eliminar nunca altera el historial: un turno pasado no se
 * modifica; "toda la serie" corta la serie el día anterior y crea una nueva
 * desde la fecha elegida; "solo este turno" omite esa fecha de la serie y crea
 * un turno puntual que la reemplaza.
 */
@Service
@Transactional
public class CalendarService {

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("es-MX"));
  private static final int MAX_RANGE_DAYS = 62;

  // --- Vistas ----------------------------------------------------------------

  public record LocalRange(LocalDateTime start, LocalDateTime end) {}

  public record OccurrenceView(
      UUID shiftId,
      UUID advisorId,
      String advisorName,
      Kind kind,
      boolean assignable,
      Recurrence recurrence,
      LocalDate date,
      LocalDateTime start,
      LocalDateTime end,
      String notes,
      List<LocalRange> absentParts,
      boolean fullyAbsent,
      List<Integer> days,
      LocalDate seriesStart,
      LocalDate seriesEnd,
      String createdByName,
      String updatedByName) {}

  public record AbsenceView(UUID id, UUID advisorId, String advisorName, Absence.Kind kind, LocalDateTime start, LocalDateTime end, String notes, String createdByName) {}

  public record PersonNow(UUID advisorId, String name, Kind kind, boolean assignable, LocalDateTime start, LocalDateTime end) {}

  public record AdvisorBrief(UUID userId, String name, String roleCode, String status, String statusDetail, LocalDateTime nextShift, boolean needsCalendar, String legacyShift) {}

  public record CalendarView(
      String timezone,
      LocalDate today,
      LocalDateTime now,
      LocalDate from,
      LocalDate to,
      boolean canManage,
      boolean canEditOwn,
      List<AdvisorBrief> advisors,
      List<OccurrenceView> occurrences,
      List<AbsenceView> absences,
      List<LocalRange> gaps,
      List<PersonNow> onShiftNow,
      List<PersonNow> nextUp,
      LocalDateTime nextCoverage) {}

  public record AuditView(UUID id, String entity, UUID entityId, String advisorName, String action, String detail, String actorName, Instant createdAt) {}

  // --- Entradas ----------------------------------------------------------------

  public enum Scope {
    OCCURRENCE,
    SERIES
  }

  public record ShiftInput(
      UUID advisorId,
      Kind kind,
      Recurrence recurrence,
      List<Integer> days,
      LocalDate startDate,
      LocalDate endDate,
      LocalTime startTime,
      LocalTime endTime,
      boolean assignable,
      String notes) {}

  public record AbsenceInput(UUID advisorId, Absence.Kind kind, LocalDateTime start, LocalDateTime end, String notes) {}

  public record CopyInput(LocalDate sourceFrom, int days, LocalDate targetFrom, UUID advisorId) {}

  public record CopyResult(int created, int skipped, List<String> skippedDetails) {}

  private final ShiftRepository shifts;
  private final ShiftSkipRepository skips;
  private final AbsenceRepository absences;
  private final AdvisorProfileRepository profiles;
  private final CalendarAuditRepository audits;
  private final AvailabilityService availability;
  private final StaffNames staff;
  private final Clock clock;

  CalendarService(
      ShiftRepository shifts,
      ShiftSkipRepository skips,
      AbsenceRepository absences,
      AdvisorProfileRepository profiles,
      CalendarAuditRepository audits,
      AvailabilityService availability,
      StaffNames staff,
      Clock clock) {
    this.shifts = shifts;
    this.skips = skips;
    this.absences = absences;
    this.profiles = profiles;
    this.audits = audits;
    this.availability = availability;
    this.staff = staff;
    this.clock = clock;
  }

  // ===========================================================================
  // Consulta
  // ===========================================================================

  @Transactional(readOnly = true)
  public CalendarView view(LocalDate from, LocalDate to, CurrentUser user) {
    if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
      throw new UnprocessableException("CALENDAR_RANGE_INVALID", "El rango del calendario no es válido (máximo " + MAX_RANGE_DAYS + " días).");
    }
    boolean all = user.hasPermission("CALENDAR_VIEW_ALL");
    if (!all && !user.hasPermission("CALENDAR_VIEW_OWN")) {
      throw new AccessDeniedException("No puedes consultar el calendario.");
    }
    Snapshot s = availability.snapshot(null);
    Map<UUID, StaffMember> staffById = s.staff();
    Map<UUID, Shift> shiftById = shifts.findByDeletedAtIsNull().stream().collect(Collectors.toMap(Shift::getId, Function.identity()));
    List<Span> absenceSpans = availability.absenceSpans(from.minusDays(1).atStartOfDay(Schedule.ZONE).toInstant());
    LocalDate toExclusive = to.plusDays(1);
    List<Occurrence> occ = Schedule.occurrences(s.rules(), from, toExclusive);

    Map<UUID, String> names = staffById.values().stream().collect(Collectors.toMap(StaffMember::id, StaffMember::name));
    java.util.function.Predicate<UUID> visible = id -> all || id.equals(user.id());

    List<OccurrenceView> occurrences = new ArrayList<>();
    for (Occurrence o : occ) {
      if (!visible.test(o.advisorId())) {
        continue;
      }
      Shift shift = shiftById.get(o.shiftId());
      List<Span> parts = Schedule.effective(o, absenceSpans);
      List<LocalRange> absent = absentParts(o, parts);
      occurrences.add(
          new OccurrenceView(
              o.shiftId(),
              o.advisorId(),
              staff.nameOf(o.advisorId(), names),
              o.kind(),
              o.assignable(),
              shift.getRecurrence(),
              o.date(),
              o.startLocal(),
              o.endLocal(),
              shift.getNotes(),
              absent,
              parts.isEmpty(),
              shift.days().stream().map(DayOfWeek::getValue).sorted().toList(),
              shift.getStartDate(),
              shift.getEndDate(),
              shift.getCreatedByName(),
              shift.getUpdatedByName()));
    }

    Instant windowStart = from.atStartOfDay(Schedule.ZONE).toInstant();
    Instant windowEnd = toExclusive.atStartOfDay(Schedule.ZONE).toInstant();
    List<AbsenceView> absenceViews =
        absences.findByDeletedAtIsNullAndEndsAtAfter(windowStart).stream()
            .filter(a -> a.getStartsAt().isBefore(windowEnd) && visible.test(a.getAdvisorUserId()))
            .sorted(Comparator.comparing(Absence::getStartsAt))
            .map(a -> new AbsenceView(a.getId(), a.getAdvisorUserId(), staff.nameOf(a.getAdvisorUserId(), names), a.getKind(), local(a.getStartsAt()), local(a.getEndsAt()), a.getNotes(), a.getCreatedByName()))
            .toList();

    // Cobertura del equipo: partes efectivas de turnos que participan en la asignación de quien puede recibir leads.
    List<LocalRange> gaps = List.of();
    if (all) {
      List<Span> covered = new ArrayList<>();
      for (Occurrence o : occ) {
        AdvisorProfile p = s.profiles().get(o.advisorId());
        if (o.assignable() && s.active(o.advisorId()) && p != null && p.isReceivesLeads()) {
          covered.addAll(Schedule.effective(o, absenceSpans));
        }
      }
      Instant gapFrom = windowStart.isAfter(s.now()) ? windowStart : s.now();
      gaps = gapFrom.isBefore(windowEnd) ? Schedule.gaps(covered, gapFrom, windowEnd).stream().map(g -> new LocalRange(local(g.start()), local(g.end()))).toList() : List.of();
    }

    List<PersonNow> now = new ArrayList<>();
    List<PersonNow> next = new ArrayList<>();
    LocalDate today = LocalDate.ofInstant(s.now(), Schedule.ZONE);
    List<Occurrence> around = Schedule.occurrences(s.rules(), today, today.plusDays(2));
    for (Occurrence o : around) {
      if (!visible.test(o.advisorId()) || !s.active(o.advisorId())) {
        continue;
      }
      for (Span part : Schedule.effective(o, s.absences())) {
        if (part.contains(s.now())) {
          now.add(new PersonNow(o.advisorId(), staff.nameOf(o.advisorId(), names), o.kind(), o.assignable(), local(part.start()), local(part.end())));
        } else if (part.start().isAfter(s.now())) {
          next.add(new PersonNow(o.advisorId(), staff.nameOf(o.advisorId(), names), o.kind(), o.assignable(), local(part.start()), local(part.end())));
        }
      }
    }
    // "Entra después": el siguiente bloque de cada asesor que no está de turno ahora, el más próximo primero.
    Set<UUID> onNow = now.stream().map(PersonNow::advisorId).collect(Collectors.toSet());
    List<PersonNow> nextUp =
        next.stream()
            .filter(p -> !onNow.contains(p.advisorId()))
            .sorted(Comparator.comparing(PersonNow::start))
            .collect(Collectors.toMap(PersonNow::advisorId, Function.identity(), (a, b) -> a, java.util.LinkedHashMap::new))
            .values()
            .stream()
            .limit(5)
            .toList();

    List<AdvisorBrief> advisors =
        staffById.values().stream()
            .filter(m -> visible.test(m.id()) && !"DOCUMENT_REVIEWER".equals(m.roleCode()))
            .sorted(Comparator.comparing((StaffMember m) -> !"ADVISOR".equals(m.roleCode())).thenComparing(StaffMember::name))
            .map(m -> brief(s, m))
            .toList();

    return new CalendarView(
        Schedule.ZONE.getId(),
        today,
        local(s.now()),
        from,
        to,
        user.hasPermission("CALENDAR_MANAGE"),
        user.hasPermission("CALENDAR_EDIT_OWN"),
        advisors,
        occurrences,
        absenceViews,
        gaps,
        now.stream().sorted(Comparator.comparing(PersonNow::name)).toList(),
        nextUp,
        all ? availability.nextCoverage(s).map(CalendarService::local).orElse(null) : null);
  }

  AdvisorBrief brief(Snapshot s, StaffMember m) {
    AdvisorState st = availability.state(s, m.id());
    AdvisorProfile p = s.profiles().get(m.id());
    return new AdvisorBrief(
        m.id(),
        m.name(),
        m.roleCode(),
        st.status().name(),
        st.detail(),
        st.nextShiftAt() == null ? null : local(st.nextShiftAt()),
        p != null && p.isCalendarNeedsSetup(),
        p != null ? p.getLegacyShift() : null);
  }

  @Transactional(readOnly = true)
  public List<AuditView> audit(CurrentUser user) {
    Map<UUID, String> names = staff.names();
    List<CalendarAudit> list = user.hasPermission("CALENDAR_VIEW_ALL") ? audits.findTop100ByOrderByCreatedAtDesc() : audits.findTop100ByAdvisorUserIdOrderByCreatedAtDesc(user.id());
    return list.stream()
        .map(a -> new AuditView(a.getId(), a.getEntity(), a.getEntityId(), a.getAdvisorUserId() == null ? null : staff.nameOf(a.getAdvisorUserId(), names), a.getAction(), a.getDetail(), a.getActorName(), a.getCreatedAt()))
        .toList();
  }

  // ===========================================================================
  // Turnos
  // ===========================================================================

  public UUID createShift(ShiftInput in, CurrentUser user) {
    requireEdit(user, in.advisorId());
    requireActiveAdvisor(in.advisorId());
    Shift shift = new Shift(in.advisorId(), user.id(), user.name());
    define(shift, in, true);
    validateOverlap(shift, Set.of());
    shift = shifts.save(shift);
    profiles.findById(in.advisorId()).ifPresent(AdvisorProfile::calendarConfigured);
    audit("SHIFT", shift.getId(), in.advisorId(), "CREATED", describe(shift), user);
    return shift.getId();
  }

  /**
   * @param scope        OCCURRENCE (solo ese día) o SERIES (desde ese día en adelante); en turnos de una fecha se ignora
   * @param occurrenceDate el día seleccionado en el calendario (obligatorio en series)
   */
  public UUID updateShift(UUID shiftId, ShiftInput in, Scope scope, LocalDate occurrenceDate, CurrentUser user) {
    Shift shift = loadShift(shiftId);
    requireEdit(user, shift.getAdvisorUserId());
    requireEdit(user, in.advisorId());
    requireActiveAdvisor(in.advisorId());
    LocalDate today = today();

    if (shift.getRecurrence() == Recurrence.ONCE) {
      if (shift.getStartDate().isBefore(today)) {
        throw past();
      }
      String before = describe(shift);
      shift.reassignAdvisor(in.advisorId());
      define(shift, in, true);
      validateOverlap(shift, Set.of());
      shift.updatedBy(user.name());
      audit("SHIFT", shift.getId(), shift.getAdvisorUserId(), "UPDATED", before + " → " + describe(shift), user);
      return shift.getId();
    }

    LocalDate date = occurrenceDate != null ? occurrenceDate : today;
    if (date.isBefore(today)) {
      throw past();
    }
    if (scope == Scope.OCCURRENCE) {
      if (!shift.toRule(Set.of()).occursOn(date)) {
        throw new UnprocessableException("SHIFT_OCCURRENCE_NOT_FOUND", "Esa serie no tiene turno el " + DATE.format(date) + ".");
      }
      skip(shift, date);
      Shift single = new Shift(in.advisorId(), user.id(), user.name());
      ShiftInput once = new ShiftInput(in.advisorId(), in.kind(), Recurrence.ONCE, List.of(), in.startDate() != null ? in.startDate() : date, null, in.startTime(), in.endTime(), in.assignable(), in.notes());
      define(single, once, true);
      single.originatedFrom(shift.getId());
      validateOverlap(single, Set.of());
      single = shifts.save(single);
      audit("SHIFT", single.getId(), in.advisorId(), "UPDATED", "Solo el turno del " + DATE.format(date) + ": " + describe(shift) + " → " + describe(single), user);
      return single.getId();
    }

    // Toda la serie, desde la fecha elegida (el pasado no se toca).
    String before = describe(shift);
    if (!shift.getStartDate().isBefore(date)) {
      shift.reassignAdvisor(in.advisorId());
      define(shift, withStart(in, in.startDate() != null && in.startDate().isAfter(date) ? in.startDate() : shift.getStartDate()), false);
      validateOverlap(shift, Set.of());
      shift.updatedBy(user.name());
      audit("SHIFT", shift.getId(), shift.getAdvisorUserId(), "UPDATED", "Toda la serie: " + before + " → " + describe(shift), user);
      return shift.getId();
    }
    shift.endSeriesOn(date.minusDays(1));
    shift.updatedBy(user.name());
    Shift continuation = new Shift(in.advisorId(), user.id(), user.name());
    define(continuation, withStart(in, in.startDate() != null && in.startDate().isAfter(date) ? in.startDate() : date), true);
    continuation.originatedFrom(shift.getId());
    validateOverlap(continuation, Set.of(shift.getId()));
    continuation = shifts.save(continuation);
    audit("SHIFT", continuation.getId(), in.advisorId(), "UPDATED", "Toda la serie desde el " + DATE.format(date) + ": " + before + " → " + describe(continuation), user);
    return continuation.getId();
  }

  public void deleteShift(UUID shiftId, Scope scope, LocalDate occurrenceDate, CurrentUser user) {
    Shift shift = loadShift(shiftId);
    requireEdit(user, shift.getAdvisorUserId());
    LocalDate today = today();
    Instant now = clock.instant();
    if (shift.getRecurrence() == Recurrence.ONCE) {
      if (shift.getStartDate().isBefore(today)) {
        throw past();
      }
      shift.delete(now, user.name());
      audit("SHIFT", shift.getId(), shift.getAdvisorUserId(), "DELETED", describe(shift), user);
      return;
    }
    LocalDate date = occurrenceDate != null ? occurrenceDate : today;
    if (date.isBefore(today)) {
      throw past();
    }
    if (scope == Scope.OCCURRENCE) {
      skip(shift, date);
      audit("SHIFT", shift.getId(), shift.getAdvisorUserId(), "DELETED", "Solo el turno del " + DATE.format(date) + " de: " + describe(shift), user);
      return;
    }
    if (!shift.getStartDate().isBefore(date)) {
      shift.delete(now, user.name());
    } else {
      shift.endSeriesOn(date.minusDays(1));
      shift.updatedBy(user.name());
    }
    audit("SHIFT", shift.getId(), shift.getAdvisorUserId(), "DELETED", "Toda la serie desde el " + DATE.format(date) + ": " + describe(shift), user);
  }

  /** Copia los turnos de un día o una semana a otra fecha (como turnos de una fecha); omite los que se traslapen. */
  public CopyResult copy(CopyInput in, CurrentUser user) {
    if (in.days() < 1 || in.days() > 7) {
      throw new UnprocessableException("COPY_RANGE_INVALID", "Solo se puede copiar un día o una semana.");
    }
    if (in.targetFrom().isBefore(today())) {
      throw past();
    }
    long offset = ChronoUnit.DAYS.between(in.sourceFrom(), in.targetFrom());
    if (offset == 0) {
      throw new UnprocessableException("COPY_SAME_DATES", "Elige una fecha destino distinta.");
    }
    List<Rule> rules = availability.rules();
    Map<UUID, Shift> byId = shifts.findByDeletedAtIsNull().stream().collect(Collectors.toMap(Shift::getId, Function.identity()));
    List<Occurrence> source =
        Schedule.occurrences(rules, in.sourceFrom(), in.sourceFrom().plusDays(in.days())).stream()
            .filter(o -> !o.date().isBefore(in.sourceFrom()))
            .filter(o -> in.advisorId() == null || o.advisorId().equals(in.advisorId()))
            .filter(o -> canEdit(user, o.advisorId()))
            .toList();
    int created = 0;
    List<String> skipped = new ArrayList<>();
    Map<UUID, String> names = staff.names();
    for (Occurrence o : source) {
      Shift original = byId.get(o.shiftId());
      LocalDate target = o.date().plusDays(offset);
      Shift copy = new Shift(o.advisorId(), user.id(), user.name());
      define(
          copy,
          new ShiftInput(o.advisorId(), original.getKind(), Recurrence.ONCE, List.of(), target, null, original.getStartTime(), original.getEndTime(), original.isAssignable(), original.getNotes()),
          true);
      try {
        validateOverlap(copy, Set.of());
      } catch (UnprocessableException e) {
        skipped.add(staff.nameOf(o.advisorId(), names) + " " + DATE.format(target) + ": ya tiene un turno que se cruza");
        continue;
      }
      shifts.save(copy);
      created++;
    }
    if (created > 0) {
      audit("SHIFT", null, in.advisorId(), "CREATED", "Copió " + created + " turno(s) del " + DATE.format(in.sourceFrom()) + (in.days() > 1 ? " (semana)" : "") + " al " + DATE.format(in.targetFrom()), user);
    }
    return new CopyResult(created, skipped.size(), skipped);
  }

  // ===========================================================================
  // Ausencias
  // ===========================================================================

  public UUID createAbsence(AbsenceInput in, CurrentUser user) {
    requireEdit(user, in.advisorId());
    requireActiveAdvisor(in.advisorId());
    Absence absence = new Absence(in.advisorId(), user.name());
    defineAbsence(absence, in);
    absence = absences.save(absence);
    audit("ABSENCE", absence.getId(), in.advisorId(), "CREATED", describe(absence), user);
    return absence.getId();
  }

  public void updateAbsence(UUID id, AbsenceInput in, CurrentUser user) {
    Absence absence = absences.findById(id).filter(a -> a.getDeletedAt() == null).orElseThrow(() -> new NotFoundException("Ausencia no encontrada"));
    requireEdit(user, absence.getAdvisorUserId());
    requireEdit(user, in.advisorId());
    if (absence.getEndsAt().isBefore(clock.instant())) {
      throw new UnprocessableException("ABSENCE_PAST", "Una ausencia que ya terminó no se modifica.");
    }
    String before = describe(absence);
    defineAbsence(absence, in);
    absence.updatedBy(user.name());
    audit("ABSENCE", absence.getId(), absence.getAdvisorUserId(), "UPDATED", before + " → " + describe(absence), user);
  }

  public void deleteAbsence(UUID id, CurrentUser user) {
    Absence absence = absences.findById(id).filter(a -> a.getDeletedAt() == null).orElseThrow(() -> new NotFoundException("Ausencia no encontrada"));
    requireEdit(user, absence.getAdvisorUserId());
    absence.delete(clock.instant(), user.name());
    audit("ABSENCE", absence.getId(), absence.getAdvisorUserId(), "DELETED", describe(absence), user);
  }

  // ===========================================================================
  // Apoyo
  // ===========================================================================

  private void define(Shift shift, ShiftInput in, boolean checkPast) {
    if (in.kind() == null || in.recurrence() == null || in.startDate() == null || in.startTime() == null || in.endTime() == null) {
      throw new UnprocessableException("SHIFT_INCOMPLETE", "Faltan datos del turno (tipo, repetición, fecha y horario).");
    }
    if (in.startTime().equals(in.endTime())) {
      throw new UnprocessableException("SHIFT_TIMES_INVALID", "La hora de inicio y de fin no pueden ser iguales.");
    }
    Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
    if (in.recurrence() == Recurrence.WEEKLY) {
      if (in.days() == null || in.days().isEmpty()) {
        throw new UnprocessableException("SHIFT_DAYS_REQUIRED", "Elige al menos un día de la semana.");
      }
      for (Integer d : in.days()) {
        if (d == null || d < 1 || d > 7) {
          throw new UnprocessableException("SHIFT_DAYS_INVALID", "Día de la semana inválido.");
        }
        days.add(DayOfWeek.of(d));
      }
      if (in.endDate() != null && in.endDate().isBefore(in.startDate())) {
        throw new UnprocessableException("SHIFT_DATES_INVALID", "La fecha final no puede ser anterior a la inicial.");
      }
    }
    if (checkPast && in.startDate().isBefore(today())) {
      throw past();
    }
    shift.define(in.kind(), in.recurrence(), days, in.startDate(), in.endDate(), in.startTime(), in.endTime(), in.assignable(), in.notes());
  }

  private static ShiftInput withStart(ShiftInput in, LocalDate start) {
    return new ShiftInput(in.advisorId(), in.kind(), Recurrence.WEEKLY, in.days(), start, in.endDate(), in.startTime(), in.endTime(), in.assignable(), in.notes());
  }

  /** Mismo asesor: sin turnos duplicados ni traslapados (varios asesores sí pueden coincidir). */
  private void validateOverlap(Shift shift, Set<UUID> ignore) {
    Map<UUID, Set<LocalDate>> skipped = new java.util.HashMap<>();
    for (ShiftSkip skip : skips.findAll()) {
      skipped.computeIfAbsent(skip.getShiftId(), k -> new HashSet<>()).add(skip.getOccurrenceDate());
    }
    List<Rule> existing =
        shifts.findByAdvisorUserIdAndDeletedAtIsNull(shift.getAdvisorUserId()).stream()
            .filter(s -> !s.getId().equals(shift.getId()) && !ignore.contains(s.getId()))
            .map(s -> s.toRule(skipped.getOrDefault(s.getId(), Set.of())))
            .toList();
    Optional<Occurrence> clash = Schedule.firstOverlap(shift.toRule(skipped.getOrDefault(shift.getId(), Set.of())), existing);
    if (clash.isPresent()) {
      Occurrence c = clash.get();
      throw new UnprocessableException(
          "SHIFT_OVERLAP",
          "Se cruza con otro turno del mismo asesor: " + DATE.format(c.date()) + " de " + c.startLocal().toLocalTime() + " a " + c.endLocal().toLocalTime() + ".");
    }
  }

  private void skip(Shift shift, LocalDate date) {
    if (!skips.existsByShiftIdAndOccurrenceDate(shift.getId(), date)) {
      skips.save(new ShiftSkip(shift.getId(), date, clock.instant()));
    }
  }

  private void defineAbsence(Absence absence, AbsenceInput in) {
    if (in.kind() == null || in.start() == null || in.end() == null) {
      throw new UnprocessableException("ABSENCE_INCOMPLETE", "Faltan datos de la ausencia (tipo, inicio y fin).");
    }
    Instant start = in.start().atZone(Schedule.ZONE).toInstant();
    Instant end = in.end().atZone(Schedule.ZONE).toInstant();
    if (!end.isAfter(start)) {
      throw new UnprocessableException("ABSENCE_DATES_INVALID", "El fin de la ausencia debe ser posterior al inicio.");
    }
    if (Duration.between(start, end).toDays() > 366) {
      throw new UnprocessableException("ABSENCE_TOO_LONG", "Una ausencia no puede durar más de un año.");
    }
    absence.define(in.advisorId(), in.kind(), start, end, in.notes());
  }

  private Shift loadShift(UUID id) {
    return shifts.findById(id).filter(s -> s.getDeletedAt() == null).orElseThrow(() -> new NotFoundException("Turno no encontrado"));
  }

  private void requireActiveAdvisor(UUID advisorId) {
    if (advisorId == null || staff.activeStaff().stream().noneMatch(m -> m.id().equals(advisorId))) {
      throw new UnprocessableException("ADVISOR_INACTIVE", "Elige un asesor activo.");
    }
  }

  private static boolean canEdit(CurrentUser user, UUID advisorId) {
    return user.hasPermission("CALENDAR_MANAGE") || (user.hasPermission("CALENDAR_EDIT_OWN") && user.id().equals(advisorId));
  }

  private static void requireEdit(CurrentUser user, UUID advisorId) {
    if (!canEdit(user, advisorId)) {
      throw new AccessDeniedException("No tienes permiso para modificar este horario.");
    }
  }

  private static UnprocessableException past() {
    return new UnprocessableException("SHIFT_PAST", "Los turnos pasados no se modifican: conservan el historial.");
  }

  private LocalDate today() {
    return LocalDate.ofInstant(clock.instant(), Schedule.ZONE);
  }

  static LocalDateTime local(Instant t) {
    return LocalDateTime.ofInstant(t, Schedule.ZONE);
  }

  private static List<LocalRange> absentParts(Occurrence o, List<Span> effective) {
    List<LocalRange> out = new ArrayList<>();
    Instant cursor = o.start();
    for (Span p : effective.stream().sorted(Comparator.comparing(Span::start)).toList()) {
      if (p.start().isAfter(cursor)) {
        out.add(new LocalRange(local(cursor), local(p.start())));
      }
      cursor = p.end();
    }
    if (cursor.isBefore(o.end())) {
      out.add(new LocalRange(local(cursor), local(o.end())));
    }
    return out;
  }

  private void audit(String entity, UUID entityId, UUID advisorId, String action, String detail, CurrentUser user) {
    audits.save(new CalendarAudit(entity, entityId, advisorId, action, detail, user.id(), user.name(), clock.instant()));
  }

  private String describe(Shift s) {
    String who = staff.nameOf(s.getAdvisorUserId(), new java.util.HashMap<>());
    String when =
        s.getRecurrence() == Recurrence.ONCE
            ? DATE.format(s.getStartDate())
            : "cada " + s.days().stream().sorted().map(d -> d.getDisplayName(java.time.format.TextStyle.SHORT, Locale.forLanguageTag("es-MX"))).collect(Collectors.joining(", "))
                + " desde " + DATE.format(s.getStartDate()) + (s.getEndDate() != null ? " hasta " + DATE.format(s.getEndDate()) : "");
    return who + ": " + (s.getKind() == Kind.GUARD ? "guardia" : "jornada") + " " + when + " " + s.getStartTime() + "–" + s.getEndTime() + (s.isAssignable() ? "" : " (sin asignación automática)");
  }

  private String describe(Absence a) {
    return staff.nameOf(a.getAdvisorUserId(), new java.util.HashMap<>()) + ": " + a.getKind().name().toLowerCase(Locale.ROOT) + " del " + local(a.getStartsAt()) + " al " + local(a.getEndsAt());
  }
}
