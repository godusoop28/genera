package com.c21genera.surveys.web;

import com.c21genera.identity.CurrentUser;
import com.c21genera.shared.security.ExpedienteAccessPolicy;
import com.c21genera.surveys.application.NpsDashboardService;
import com.c21genera.surveys.application.NpsDashboardService.Dashboard;
import com.c21genera.surveys.application.NpsDashboardService.Filter;
import com.c21genera.surveys.application.SurveyService;
import com.c21genera.surveys.application.SurveyService.ManualRequest;
import com.c21genera.surveys.application.SurveyService.SurveyView;
import com.c21genera.surveys.domain.NpsCategory;
import com.c21genera.surveys.domain.SurveyChannel;
import com.c21genera.surveys.domain.SurveySettings;
import com.c21genera.surveys.domain.Touchpoint;
import com.c21genera.surveys.domain.TouchpointConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
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
 * Control de Cambios #28: encuestas NPS para el personal. Configuración por
 * punto de contacto (NPS_MANAGE), historial por expediente o lead, encuestas
 * generadas por el asesor y dashboard (NPS_VIEW, o el propio NPS del asesor si
 * la empresa lo permite).
 */
@RestController
public class InternalSurveyController {

  private final SurveyService surveys;
  private final NpsDashboardService dashboard;
  private final ExpedienteAccessPolicy accessPolicy;

  public InternalSurveyController(SurveyService surveys, NpsDashboardService dashboard, ExpedienteAccessPolicy accessPolicy) {
    this.surveys = surveys;
    this.dashboard = dashboard;
    this.accessPolicy = accessPolicy;
  }

  public record TouchpointView(
      Touchpoint touchpoint,
      int number,
      String label,
      boolean systemTriggered,
      boolean active,
      SurveyChannel channel,
      int delayMinutes,
      boolean askComment,
      String introMessage,
      String staticLink,
      String updatedByName,
      Instant updatedAt) {}

  public record SettingsView(
      int minDaysBetween, int maxPerPeriod, int periodDays, boolean reminderEnabled, int reminderAfterHours, int maxReminders, int expireDays, boolean advisorsSeeOwn, String updatedByName) {

    static SettingsView of(SurveySettings s) {
      return new SettingsView(
          s.getMinDaysBetween(), s.getMaxPerPeriod(), s.getPeriodDays(), s.isReminderEnabled(), s.getReminderAfterHours(), s.getMaxReminders(), s.getExpireDays(), s.isAdvisorsSeeOwn(), s.getUpdatedByName());
    }
  }

  public record ConfigView(List<TouchpointView> touchpoints, SettingsView settings) {}

  public record TouchpointRequest(boolean active, @NotNull SurveyChannel channel, @Min(0) @Max(86400) int delayMinutes, boolean askComment, @Size(max = 500) String introMessage) {}

  public record SettingsRequest(
      @Min(0) @Max(365) int minDaysBetween,
      @Min(1) @Max(50) int maxPerPeriod,
      @Min(1) @Max(365) int periodDays,
      boolean reminderEnabled,
      @Min(1) @Max(720) int reminderAfterHours,
      @Min(0) @Max(5) int maxReminders,
      @Min(1) @Max(365) int expireDays,
      boolean advisorsSeeOwn) {}

  public record CreateRequest(
      @NotNull Touchpoint touchpoint, SurveyChannel channel, UUID expedienteId, UUID leadId, @Size(max = 200) String clientName, @Size(max = 32) String clientPhone, @Size(max = 255) String clientEmail) {}

  public record History(boolean canSeeScores, List<SurveyView> surveys) {}

  @GetMapping("/api/v1/internal/surveys/config")
  public ConfigView config() {
    return new ConfigView(surveys.configs().stream().map(this::view).toList(), SettingsView.of(surveys.settings()));
  }

  @PutMapping("/api/v1/internal/surveys/config/touchpoints/{touchpoint}")
  @PreAuthorize("hasAuthority('NPS_MANAGE')")
  public TouchpointView updateTouchpoint(@PathVariable Touchpoint touchpoint, @Valid @RequestBody TouchpointRequest r, @AuthenticationPrincipal Jwt jwt) {
    return view(surveys.updateConfig(touchpoint, r.active(), r.channel(), r.delayMinutes(), r.askComment(), r.introMessage(), CurrentUser.from(jwt)));
  }

  @PutMapping("/api/v1/internal/surveys/config/settings")
  @PreAuthorize("hasAuthority('NPS_MANAGE')")
  public SettingsView updateSettings(@Valid @RequestBody SettingsRequest r, @AuthenticationPrincipal Jwt jwt) {
    return SettingsView.of(
        surveys.updateSettings(
            r.minDaysBetween(), r.maxPerPeriod(), r.periodDays(), r.reminderEnabled(), r.reminderAfterHours(), r.maxReminders(), r.expireDays(), r.advisorsSeeOwn(), CurrentUser.from(jwt)));
  }

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/surveys")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
  public History ofExpediente(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    accessPolicy.requireAccess(expedienteId, user.id(), user.permissions());
    boolean scores = canSeeScores(user);
    return new History(scores, surveys.ofExpediente(expedienteId, scores));
  }

  @GetMapping("/api/v1/internal/surveys")
  @PreAuthorize("hasAnyAuthority('LEAD_VIEW_ALL', 'LEAD_VIEW_OWN')")
  public History ofLead(@RequestParam UUID leadId, @AuthenticationPrincipal Jwt jwt) {
    boolean scores = canSeeScores(CurrentUser.from(jwt));
    return new History(scores, surveys.ofLead(leadId, scores));
  }

  /** El asesor genera la encuesta de una interacción (presencial o del sistema). */
  @PostMapping("/api/v1/internal/surveys")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN', 'LEAD_MANAGE')")
  public SurveyView create(@Valid @RequestBody CreateRequest r, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    if (r.expedienteId() != null) {
      accessPolicy.requireAccess(r.expedienteId(), user.id(), user.permissions());
    } else if (!user.hasPermission("LEAD_MANAGE")) {
      throw new AccessDeniedException("Tu usuario no puede generar encuestas de leads.");
    }
    return surveys.createManual(new ManualRequest(r.touchpoint(), r.channel(), r.expedienteId(), r.leadId(), r.clientName(), r.clientPhone(), r.clientEmail()), user);
  }

  @GetMapping("/api/v1/internal/surveys/dashboard")
  public Dashboard dashboard(
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(required = false) UUID advisorId,
      @RequestParam(required = false) Touchpoint touchpoint,
      @RequestParam(required = false) NpsCategory category,
      @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    UUID onlyAdvisor = null;
    if (!user.hasPermission("NPS_VIEW")) {
      if (!surveys.settings().isAdvisorsSeeOwn()) {
        throw new AccessDeniedException("Los resultados de satisfacción solo los consulta dirección.");
      }
      onlyAdvisor = user.id();
    }
    return dashboard.build(new Filter(from, to, advisorId, touchpoint, category), onlyAdvisor);
  }

  private boolean canSeeScores(CurrentUser user) {
    return user.hasPermission("NPS_VIEW") || surveys.settings().isAdvisorsSeeOwn();
  }

  private TouchpointView view(TouchpointConfig c) {
    Touchpoint t = c.getTouchpoint();
    return new TouchpointView(
        t, t.number(), t.label(), t.systemTriggered(), c.isActive(), c.getChannel(), c.getDelayMinutes(), c.isAskComment(), c.getIntroMessage(), surveys.staticLink(t), c.getUpdatedByName(), c.getUpdatedAt());
  }
}
