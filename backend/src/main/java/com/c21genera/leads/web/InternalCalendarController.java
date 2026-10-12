package com.c21genera.leads.web;

import com.c21genera.identity.CurrentUser;
import com.c21genera.leads.application.CalendarService;
import com.c21genera.leads.application.CalendarService.AbsenceInput;
import com.c21genera.leads.application.CalendarService.AuditView;
import com.c21genera.leads.application.CalendarService.CalendarView;
import com.c21genera.leads.application.CalendarService.CopyInput;
import com.c21genera.leads.application.CalendarService.CopyResult;
import com.c21genera.leads.application.CalendarService.Scope;
import com.c21genera.leads.application.CalendarService.ShiftInput;
import com.c21genera.leads.domain.Absence;
import com.c21genera.leads.domain.Schedule.Kind;
import com.c21genera.leads.domain.Schedule.Recurrence;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Calendario de turnos, guardias y ausencias (hora de Ciudad de México).
 * Consultar: CALENDAR_VIEW_ALL o CALENDAR_VIEW_OWN (solo el propio). Editar:
 * CALENDAR_MANAGE (cualquiera) o CALENDAR_EDIT_OWN (solo el propio); el
 * servicio vuelve a validar a qué asesor afecta cada cambio.
 */
@RestController
@PreAuthorize("hasAnyAuthority('CALENDAR_VIEW_ALL', 'CALENDAR_VIEW_OWN')")
public class InternalCalendarController {

  private final CalendarService calendar;

  public InternalCalendarController(CalendarService calendar) {
    this.calendar = calendar;
  }

  public record ShiftRequest(
      @NotNull UUID advisorId,
      @NotNull Kind kind,
      @NotNull Recurrence recurrence,
      List<@Min(1) @Max(7) Integer> days,
      @NotNull LocalDate startDate,
      LocalDate endDate,
      @NotNull LocalTime startTime,
      @NotNull LocalTime endTime,
      boolean assignable,
      @Size(max = 500) String notes) {

    ShiftInput toInput() {
      return new ShiftInput(advisorId, kind, recurrence, days, startDate, endDate, startTime, endTime, assignable, notes);
    }
  }

  public record AbsenceRequest(@NotNull UUID advisorId, @NotNull Absence.Kind kind, @NotNull LocalDateTime start, @NotNull LocalDateTime end, @Size(max = 500) String notes) {

    AbsenceInput toInput() {
      return new AbsenceInput(advisorId, kind, start, end, notes);
    }
  }

  public record CopyRequest(@NotNull LocalDate sourceFrom, @Min(1) @Max(7) int days, @NotNull LocalDate targetFrom, UUID advisorId) {}

  @GetMapping("/api/v1/internal/crm/calendar")
  public CalendarView view(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @AuthenticationPrincipal Jwt jwt) {
    return calendar.view(from, to, CurrentUser.from(jwt));
  }

  @GetMapping("/api/v1/internal/crm/calendar/audit")
  public List<AuditView> audit(@AuthenticationPrincipal Jwt jwt) {
    return calendar.audit(CurrentUser.from(jwt));
  }

  @PostMapping("/api/v1/internal/crm/calendar/shifts")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyAuthority('CALENDAR_MANAGE', 'CALENDAR_EDIT_OWN')")
  public Map<String, UUID> createShift(@Valid @RequestBody ShiftRequest r, @AuthenticationPrincipal Jwt jwt) {
    return Map.of("id", calendar.createShift(r.toInput(), CurrentUser.from(jwt)));
  }

  @PutMapping("/api/v1/internal/crm/calendar/shifts/{id}")
  @PreAuthorize("hasAnyAuthority('CALENDAR_MANAGE', 'CALENDAR_EDIT_OWN')")
  public Map<String, UUID> updateShift(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "SERIES") Scope scope,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @Valid @RequestBody ShiftRequest r,
      @AuthenticationPrincipal Jwt jwt) {
    return Map.of("id", calendar.updateShift(id, r.toInput(), scope, date, CurrentUser.from(jwt)));
  }

  @DeleteMapping("/api/v1/internal/crm/calendar/shifts/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyAuthority('CALENDAR_MANAGE', 'CALENDAR_EDIT_OWN')")
  public void deleteShift(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "SERIES") Scope scope,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @AuthenticationPrincipal Jwt jwt) {
    calendar.deleteShift(id, scope, date, CurrentUser.from(jwt));
  }

  @PostMapping("/api/v1/internal/crm/calendar/copy")
  @PreAuthorize("hasAnyAuthority('CALENDAR_MANAGE', 'CALENDAR_EDIT_OWN')")
  public CopyResult copy(@Valid @RequestBody CopyRequest r, @AuthenticationPrincipal Jwt jwt) {
    return calendar.copy(new CopyInput(r.sourceFrom(), r.days(), r.targetFrom(), r.advisorId()), CurrentUser.from(jwt));
  }

  @PostMapping("/api/v1/internal/crm/calendar/absences")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyAuthority('CALENDAR_MANAGE', 'CALENDAR_EDIT_OWN')")
  public Map<String, UUID> createAbsence(@Valid @RequestBody AbsenceRequest r, @AuthenticationPrincipal Jwt jwt) {
    return Map.of("id", calendar.createAbsence(r.toInput(), CurrentUser.from(jwt)));
  }

  @PutMapping("/api/v1/internal/crm/calendar/absences/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyAuthority('CALENDAR_MANAGE', 'CALENDAR_EDIT_OWN')")
  public void updateAbsence(@PathVariable UUID id, @Valid @RequestBody AbsenceRequest r, @AuthenticationPrincipal Jwt jwt) {
    calendar.updateAbsence(id, r.toInput(), CurrentUser.from(jwt));
  }

  @DeleteMapping("/api/v1/internal/crm/calendar/absences/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAnyAuthority('CALENDAR_MANAGE', 'CALENDAR_EDIT_OWN')")
  public void deleteAbsence(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    calendar.deleteAbsence(id, CurrentUser.from(jwt));
  }
}
