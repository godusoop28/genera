package com.c21genera.leads.web;

import com.c21genera.identity.CurrentUser;
import com.c21genera.leads.LeadInterest;
import com.c21genera.leads.LeadSource;
import com.c21genera.leads.LeadStage;
import com.c21genera.leads.application.AdvisorRoutingService;
import com.c21genera.leads.application.AdvisorRoutingService.AdvisorRouting;
import com.c21genera.leads.application.AdvisorRoutingService.UpdateRouting;
import com.c21genera.leads.application.CrmDashboardService;
import com.c21genera.leads.application.CrmDashboardService.Dashboard;
import com.c21genera.leads.application.LeadService;
import com.c21genera.leads.application.LeadService.CreateLead;
import com.c21genera.leads.application.LeadService.Filter;
import com.c21genera.leads.application.LeadService.Group;
import com.c21genera.leads.application.LeadService.LeadDetail;
import com.c21genera.leads.application.LeadService.LeadPage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
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

/** CRM (Módulo 3): leads, dashboard y ruteo de asesores. Quien solo tiene LEAD_VIEW_OWN ve únicamente sus leads. */
@RestController
@PreAuthorize("hasAnyAuthority('LEAD_VIEW_ALL', 'LEAD_VIEW_OWN')")
public class InternalLeadController {

  private final LeadService leads;
  private final CrmDashboardService dashboard;
  private final AdvisorRoutingService routing;

  public InternalLeadController(LeadService leads, CrmDashboardService dashboard, AdvisorRoutingService routing) {
    this.leads = leads;
    this.dashboard = dashboard;
    this.routing = routing;
  }

  public record StageRequest(@NotNull LeadStage stage, @Size(max = 500) String lostReason) {}

  public record ReassignRequest(@NotNull UUID advisorId) {}

  public record NoteRequest(@NotBlank @Size(max = 4000) String text) {}

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
      boolean autoAssign) {}

  public record RoutingRequest(
      boolean receivesLeads,
      @Min(0) @Max(100) int weight,
      @Min(1) @Max(500) Integer dailyLimit,
      LocalTime shiftStart,
      LocalTime shiftEnd,
      Long online21UserId,
      @Size(max = 20) String whatsappPhone) {}

  @GetMapping("/api/v1/internal/leads")
  public LeadPage list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Group group,
      @RequestParam(required = false) UUID advisorId,
      @RequestParam(required = false) LeadSource source,
      @RequestParam(required = false) Instant from,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size,
      @AuthenticationPrincipal Jwt jwt) {
    return leads.list(new Filter(q, group, advisorId, source, from), CurrentUser.from(jwt), Math.max(0, page), size);
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
        new CreateLead(r.name(), r.phone(), r.email(), r.source(), r.sourceDetail(), r.interest(), r.propertyReference(), r.summary(), r.budget(), r.advisorId(), r.autoAssign()),
        CurrentUser.from(jwt));
  }

  @PostMapping("/api/v1/internal/leads/{id}/stage")
  @PreAuthorize("hasAuthority('LEAD_MANAGE')")
  public LeadDetail stage(@PathVariable UUID id, @Valid @RequestBody StageRequest r, @AuthenticationPrincipal Jwt jwt) {
    return leads.changeStage(id, r.stage(), r.lostReason(), CurrentUser.from(jwt));
  }

  @PostMapping("/api/v1/internal/leads/{id}/assign")
  @PreAuthorize("hasAuthority('LEAD_ASSIGN')")
  public LeadDetail assign(@PathVariable UUID id, @Valid @RequestBody ReassignRequest r, @AuthenticationPrincipal Jwt jwt) {
    return leads.reassign(id, r.advisorId(), CurrentUser.from(jwt));
  }

  @PostMapping("/api/v1/internal/leads/{id}/notes")
  @PreAuthorize("hasAuthority('LEAD_MANAGE')")
  public LeadDetail note(@PathVariable UUID id, @Valid @RequestBody NoteRequest r, @AuthenticationPrincipal Jwt jwt) {
    return leads.addNote(id, r.text(), CurrentUser.from(jwt));
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
    return routing.update(userId, new UpdateRouting(r.receivesLeads(), r.weight(), r.dailyLimit(), r.shiftStart(), r.shiftEnd(), r.online21UserId(), r.whatsappPhone()));
  }
}
