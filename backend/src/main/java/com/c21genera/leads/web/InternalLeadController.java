package com.c21genera.leads.web;

import com.c21genera.identity.CurrentUser;
import com.c21genera.leads.LeadInterest;
import com.c21genera.leads.LeadSource;
import com.c21genera.leads.LeadStage;
import com.c21genera.leads.LeadTemperature;
import com.c21genera.leads.LostReason;
import com.c21genera.leads.application.AdvisorRoutingService;
import com.c21genera.leads.application.AdvisorRoutingService.AdvisorRouting;
import com.c21genera.leads.application.AdvisorRoutingService.SettingsView;
import com.c21genera.leads.application.AdvisorRoutingService.UpdateRouting;
import com.c21genera.leads.application.CrmDashboardService;
import com.c21genera.leads.application.CrmDashboardService.Dashboard;
import com.c21genera.leads.application.LeadService;
import com.c21genera.leads.application.LeadService.CreateLead;
import com.c21genera.leads.application.LeadService.Filter;
import com.c21genera.leads.application.LeadService.Group;
import com.c21genera.leads.application.LeadService.LeadDetail;
import com.c21genera.leads.application.LeadService.LeadPage;
import com.c21genera.leads.application.LeadService.Profile;
import com.c21genera.leads.application.LeadService.UpdateLead;
import com.c21genera.leads.application.LeadSourceCatalog;
import com.c21genera.leads.application.LeadSourceCatalog.SourceOption;
import com.c21genera.leads.application.LeadTaskService;
import com.c21genera.leads.application.LeadTaskService.AgendaFilter;
import com.c21genera.leads.application.LeadTaskService.CompleteInput;
import com.c21genera.leads.application.LeadTaskService.TaskInput;
import com.c21genera.leads.application.LeadTaskService.TaskView;
import com.c21genera.leads.domain.AssignmentEngine.Mode;
import com.c21genera.leads.domain.AssignmentSettings.OwnerOffShiftPolicy;
import com.c21genera.leads.domain.LeadSourceOption;
import com.c21genera.leads.domain.LeadTask;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRM (Módulo 3): leads, actividades y agenda (CC#30), catálogo de fuentes
 * (CC#29), dashboard y ruteo de asesores. Quien solo tiene LEAD_VIEW_OWN ve
 * únicamente sus leads.
 */
@RestController
@PreAuthorize("hasAnyAuthority('LEAD_VIEW_ALL', 'LEAD_VIEW_OWN')")
public class InternalLeadController {

  private final LeadService leads;
  private final CrmDashboardService dashboard;
  private final AdvisorRoutingService routing;
  private final LeadTaskService tasks;
  private final LeadSourceCatalog sources;

  public InternalLeadController(LeadService leads, CrmDashboardService dashboard, AdvisorRoutingService routing, LeadTaskService tasks, LeadSourceCatalog sources) {
    this.leads = leads;
    this.dashboard = dashboard;
    this.routing = routing;
    this.tasks = tasks;
    this.sources = sources;
  }

  public record StageRequest(@NotNull LeadStage stage, LostReason lostReasonCode, @Size(max = 500) String lostReason) {}

  public record ReassignRequest(@NotNull UUID advisorId, @Size(max = 500) String exceptionReason) {}

  public record NoteRequest(@NotBlank @Size(max = 4000) String text) {}

  /** Control de Cambios #30: interés y estatus de la ficha. */
  public record ProfileRequest(
      LocalDate firstContactOn,
      UUID propertySubtypeId,
      @Size(max = 200) String zone,
      @Min(0) BigDecimal budgetMin,
      @Min(0) BigDecimal budgetMax,
      @Size(max = 2000) String desiredFeatures,
      LeadTemperature temperature,
      @Min(0) @Max(100) Integer closeProbability,
      LocalDate expectedCloseOn) {

    Profile toProfile() {
      return new Profile(firstContactOn, propertySubtypeId, zone, budgetMin, budgetMax, desiredFeatures, temperature, closeProbability, expectedCloseOn);
    }
  }

  public record CreateLeadRequest(
      @NotBlank @Size(max = 200) String name,
      @Size(max = 32) String phone,
      @Size(max = 255) String email,
      LeadSource source,
      @Size(max = 200) String sourceDetail,
      LeadInterest interest,
      @Size(max = 64) String propertyReference,
      @Size(max = 2000) String summary,
      BigDecimal budget,
      UUID advisorId,
      @Size(max = 500) String exceptionReason,
      boolean autoAssign,
      @Size(max = 48) String sourceCode,
      @Valid ProfileRequest profile) {}

  public record UpdateLeadRequest(
      @NotBlank @Size(max = 200) String name,
      @Size(max = 32) String phone,
      @Size(max = 255) String email,
      LeadInterest interest,
      @Size(max = 48) String sourceCode,
      @Size(max = 200) String sourceDetail,
      @Size(max = 2000) String summary,
      @Size(max = 64) String propertyReference,
      @Valid ProfileRequest profile) {}

  public record SharePropertyRequest(@NotBlank @Size(max = 64) String reference, @Size(max = 500) String note) {}

  public record ExpedienteLinkRequest(@Size(max = 40) String folio) {}

  public record TaskRequest(
      @NotNull LeadTask.Type type,
      @Size(max = 200) String title,
      Instant scheduledAt,
      @Min(0) @Max(1440) Integer durationMinutes,
      UUID advisorId,
      @Size(max = 64) String propertyKey,
      @Size(max = 300) String propertyTitle,
      @Size(max = 4000) String notesBefore,
      Integer reminderMinutes,
      boolean done,
      LeadTask.Result result,
      @Min(0) @Max(1440) Integer actualMinutes,
      @Size(max = 4000) String outcomeNotes,
      @Size(max = 1000) String nextAction,
      Instant nextContactAt,
      LeadTask.Type nextType) {

    TaskInput toInput() {
      return new TaskInput(
          type, title, scheduledAt, durationMinutes, advisorId, propertyKey, propertyTitle, notesBefore, reminderMinutes, done, result, actualMinutes, outcomeNotes, nextAction, nextContactAt, nextType);
    }
  }

  public record CompleteTaskRequest(
      @NotNull LeadTask.Result result,
      @Min(0) @Max(1440) Integer actualMinutes,
      @Size(max = 4000) String outcomeNotes,
      @Size(max = 1000) String nextAction,
      Instant nextContactAt,
      LeadTask.Type nextType) {}

  public record CancelTaskRequest(@Size(max = 500) String reason) {}

  public record SourceUpdateRequest(@Size(max = 80) String label, @Size(max = 300) String description, boolean active, boolean requiresDetail) {}

  public record SourceCreateRequest(
      @NotNull LeadSourceOption.Category category, @NotBlank @Size(max = 80) String label, @Size(max = 300) String description, LeadSource channel, boolean requiresDetail) {}

  public record FollowUpSettingsRequest(@Min(1) @Max(90) int staleDays, boolean reminderEmail) {}

  public record RoutingRequest(
      boolean receivesLeads,
      @Min(0) @Max(100) int weight,
      @Min(1) @Max(500) Integer dailyLimit,
      Long online21UserId,
      @Size(max = 20) String whatsappPhone) {}

  public record SettingsRequest(@NotNull Mode roundMode, @NotNull OwnerOffShiftPolicy ownerOffShiftPolicy) {}

  @GetMapping("/api/v1/internal/leads")
  public LeadPage list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Group group,
      @RequestParam(required = false) UUID advisorId,
      @RequestParam(required = false) LeadSource source,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Long propertyId,
      @RequestParam(required = false) String sourceCode,
      @RequestParam(required = false) LeadTemperature temperature,
      @RequestParam(required = false) LeadStage stage,
      @RequestParam(required = false) UUID expedienteId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @AuthenticationPrincipal Jwt jwt) {
    return leads.list(
        new Filter(q, group, advisorId, source, from, propertyId, sourceCode, temperature, stage, expedienteId), CurrentUser.from(jwt), Math.max(0, page), size);
  }

  @GetMapping("/api/v1/internal/leads/{id}")
  public LeadDetail get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return leads.get(id, CurrentUser.from(jwt));
  }

  @PostMapping("/api/v1/internal/leads")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('LEAD_MANAGE')")
  public LeadDetail create(@Valid @RequestBody CreateLeadRequest r, @AuthenticationPrincipal Jwt jwt) {
    return leads.create(
        new CreateLead(
            r.name(),
            r.phone(),
            r.email(),
            r.source(),
            r.sourceDetail(),
            r.interest(),
            r.propertyReference(),
            r.summary(),
            r.budget(),
            r.advisorId(),
            r.exceptionReason(),
            r.autoAssign(),
            r.sourceCode(),
            r.profile() == null ? null : r.profile().toProfile()),
        CurrentUser.from(jwt));
  }

  @PutMapping("/api/v1/internal/leads/{id}")
  @PreAuthorize("hasAuthority('LEAD_MANAGE')")
  public LeadDetail update(@PathVariable UUID id, @Valid @RequestBody UpdateLeadRequest r, @AuthenticationPrincipal Jwt jwt) {
    return leads.update(
        id,
        new UpdateLead(
            r.name(), r.phone(), r.email(), r.interest(), r.sourceCode(), r.sourceDetail(), r.summary(), r.propertyReference(), r.profile() == null ? null : r.profile().toProfile()),
        CurrentUser.from(jwt));
  }

  @PostMapping("/api/v1/internal/leads/{id}/stage")
  @PreAuthorize("hasAuthority('LEAD_MANAGE')")
  public LeadDetail stage(@PathVariable UUID id, @Valid @RequestBody StageRequest r, @AuthenticationPrincipal Jwt jwt) {
    return leads.changeStage(id, r.stage(), r.lostReasonCode(), r.lostReason(), CurrentUser.from(jwt));
  }

  @PostMapping("/api/v1/internal/leads/{id}/assign")
  @PreAuthorize("hasAuthority('LEAD_ASSIGN')")
  public LeadDetail assign(@PathVariable UUID id, @Valid @RequestBody ReassignRequest r, @AuthenticationPrincipal Jwt jwt) {
    return leads.reassign(id, r.advisorId(), r.exceptionReason(), CurrentUser.from(jwt));
  }

  @PostMapping("/api/v1/internal/leads/{id}/resend-notice")
  @PreAuthorize("hasAuthority('LEAD_MANAGE')")
  public LeadDetail resendNotice(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return leads.resendNotice(id, CurrentUser.from(jwt));
  }

  @PostMapping("/api/v1/internal/leads/{id}/notes")
  @PreAuthorize("hasAuthority('LEAD_MANAGE')")
  public LeadDetail note(@PathVariable UUID id, @Valid @RequestBody NoteRequest r, @AuthenticationPrincipal Jwt jwt) {
    return leads.addNote(id, r.text(), CurrentUser.from(jwt));
  }

  @PostMapping("/api/v1/internal/leads/{id}/shared-properties")
  @PreAuthorize("hasAuthority('LEAD_MANAGE')")
  public LeadDetail shareProperty(@PathVariable UUID id, @Valid @RequestBody SharePropertyRequest r, @AuthenticationPrincipal Jwt jwt) {
    return leads.shareProperty(id, r.reference(), r.note(), CurrentUser.from(jwt));
  }

  @PostMapping("/api/v1/internal/leads/{id}/expediente")
  @PreAuthorize("hasAuthority('LEAD_MANAGE')")
  public LeadDetail linkExpediente(@PathVariable UUID id, @Valid @RequestBody ExpedienteLinkRequest r, @AuthenticationPrincipal Jwt jwt) {
    return leads.linkExpediente(id, r.folio(), CurrentUser.from(jwt));
  }

  // --- Control de Cambios #30: actividades y agenda --------------------------

  @PostMapping("/api/v1/internal/leads/{id}/tasks")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('LEAD_MANAGE')")
  public TaskView createTask(@PathVariable UUID id, @Valid @RequestBody TaskRequest r, @AuthenticationPrincipal Jwt jwt) {
    return tasks.create(id, r.toInput(), CurrentUser.from(jwt));
  }

  @PutMapping("/api/v1/internal/lead-tasks/{taskId}")
  @PreAuthorize("hasAuthority('LEAD_MANAGE')")
  public TaskView rescheduleTask(@PathVariable UUID taskId, @Valid @RequestBody TaskRequest r, @AuthenticationPrincipal Jwt jwt) {
    return tasks.reschedule(taskId, r.toInput(), CurrentUser.from(jwt));
  }

  @PostMapping("/api/v1/internal/lead-tasks/{taskId}/complete")
  @PreAuthorize("hasAuthority('LEAD_MANAGE')")
  public TaskView completeTask(@PathVariable UUID taskId, @Valid @RequestBody CompleteTaskRequest r, @AuthenticationPrincipal Jwt jwt) {
    return tasks.complete(taskId, new CompleteInput(r.result(), r.actualMinutes(), r.outcomeNotes(), r.nextAction(), r.nextContactAt(), r.nextType()), CurrentUser.from(jwt));
  }

  @PostMapping("/api/v1/internal/lead-tasks/{taskId}/cancel")
  @PreAuthorize("hasAuthority('LEAD_MANAGE')")
  public TaskView cancelTask(@PathVariable UUID taskId, @Valid @RequestBody CancelTaskRequest r, @AuthenticationPrincipal Jwt jwt) {
    return tasks.cancel(taskId, r.reason(), CurrentUser.from(jwt));
  }

  @GetMapping("/api/v1/internal/crm/agenda")
  public List<TaskView> agenda(
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) UUID advisorId,
      @RequestParam(required = false) LeadTask.Type type,
      @RequestParam(required = false) UUID leadId,
      @RequestParam(required = false) String property,
      @AuthenticationPrincipal Jwt jwt) {
    return tasks.agenda(new AgendaFilter(from, to, advisorId, type, leadId, property), CurrentUser.from(jwt));
  }

  // --- Control de Cambios #29: catálogo de fuentes ---------------------------

  @GetMapping("/api/v1/internal/crm/lead-sources")
  public List<SourceOption> leadSources() {
    return sources.all();
  }

  @PutMapping("/api/v1/internal/crm/lead-sources/{code}")
  @PreAuthorize("hasAuthority('LEAD_SOURCE_MANAGE')")
  public SourceOption updateLeadSource(@PathVariable String code, @Valid @RequestBody SourceUpdateRequest r) {
    return sources.update(code, r.label(), r.description(), r.active(), r.requiresDetail());
  }

  @PostMapping("/api/v1/internal/crm/lead-sources")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('LEAD_SOURCE_MANAGE')")
  public SourceOption createLeadSource(@Valid @RequestBody SourceCreateRequest r) {
    return sources.create(r.category(), r.label(), r.description(), r.channel(), r.requiresDetail());
  }

  @GetMapping("/api/v1/internal/crm/dashboard")
  public Dashboard dashboard(@RequestParam(required = false) Instant from, @AuthenticationPrincipal Jwt jwt) {
    return dashboard.build(from, CurrentUser.from(jwt));
  }

  /** Lista para asignar/reasignar y para la pantalla de asesores. */
  @GetMapping("/api/v1/internal/crm/advisors")
  public List<AdvisorRouting> advisors() {
    return routing.list();
  }

  @PutMapping("/api/v1/internal/crm/advisors/{userId}")
  @PreAuthorize("hasAuthority('ADVISOR_ROUTING_MANAGE')")
  public AdvisorRouting updateAdvisor(@PathVariable UUID userId, @Valid @RequestBody RoutingRequest r) {
    return routing.update(userId, new UpdateRouting(r.receivesLeads(), r.weight(), r.dailyLimit(), r.online21UserId(), r.whatsappPhone()));
  }

  /** Política de asignación (ronda y qué hacer si el asesor de la propiedad está fuera de horario) y alerta de cobertura. */
  @GetMapping("/api/v1/internal/crm/assignment-settings")
  public SettingsView settings() {
    return routing.settings();
  }

  @PutMapping("/api/v1/internal/crm/assignment-settings")
  @PreAuthorize("hasAuthority('ADVISOR_ROUTING_MANAGE')")
  public SettingsView updateSettings(@Valid @RequestBody SettingsRequest r, @AuthenticationPrincipal Jwt jwt) {
    return routing.updateSettings(r.roundMode(), r.ownerOffShiftPolicy(), CurrentUser.from(jwt));
  }

  /** Control de Cambios #30: días sin actividad para la alerta y recordatorios por correo. */
  @PutMapping("/api/v1/internal/crm/follow-up-settings")
  @PreAuthorize("hasAuthority('ADVISOR_ROUTING_MANAGE')")
  public SettingsView updateFollowUp(@Valid @RequestBody FollowUpSettingsRequest r, @AuthenticationPrincipal Jwt jwt) {
    return routing.updateFollowUp(r.staleDays(), r.reminderEmail(), CurrentUser.from(jwt));
  }
}
