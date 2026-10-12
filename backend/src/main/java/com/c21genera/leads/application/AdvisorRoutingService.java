package com.c21genera.leads.application;

import com.c21genera.identity.CurrentUser;
import com.c21genera.identity.UserDirectory.StaffMember;
import com.c21genera.leads.AssignmentStatus;
import com.c21genera.leads.LeadStage;
import com.c21genera.leads.application.AvailabilityService.AdvisorState;
import com.c21genera.leads.application.AvailabilityService.Snapshot;
import com.c21genera.leads.application.AvailabilityService.Status;
import com.c21genera.leads.domain.AdvisorProfile;
import com.c21genera.leads.domain.AssignmentEngine.Mode;
import com.c21genera.leads.domain.AssignmentSettings;
import com.c21genera.leads.domain.AssignmentSettings.OwnerOffShiftPolicy;
import com.c21genera.leads.domain.CalendarAudit;
import com.c21genera.leads.domain.Schedule.Kind;
import com.c21genera.leads.infrastructure.AdvisorProfileRepository;
import com.c21genera.leads.infrastructure.CalendarAuditRepository;
import com.c21genera.leads.infrastructure.LeadRepository;
import com.c21genera.shared.domain.NotFoundException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quién recibe leads (peso, límite, vínculo con 21Online, WhatsApp), su estado según el calendario y la política de asignación. */
@Service
@Transactional
public class AdvisorRoutingService {

  public record AdvisorRouting(
      UUID userId,
      String name,
      String email,
      String roleCode,
      boolean receivesLeads,
      int weight,
      Integer dailyLimit,
      Long online21UserId,
      String whatsappPhone,
      String status,
      String statusDetail,
      LocalDateTime currentShiftEnd,
      String currentShiftKind,
      LocalDateTime nextShift,
      boolean needsCalendar,
      String legacyShift,
      int assignedToday,
      long openLeads,
      long wonLeads) {}

  public record UpdateRouting(boolean receivesLeads, int weight, Integer dailyLimit, Long online21UserId, String whatsappPhone) {}

  public record SettingsView(Mode roundMode, OwnerOffShiftPolicy ownerOffShiftPolicy, String updatedByName, long pendingLeads, LocalDateTime nextCoverage, boolean noUpcomingCoverage, String timezone) {}

  /** Roles que pueden atender leads (el revisor documental no). */
  private static final List<String> ROUTABLE_ROLES = List.of("ADVISOR", "DIRECTOR", "ADMINISTRATOR");

  private final AdvisorProfileRepository profiles;
  private final LeadRepository leads;
  private final AvailabilityService availability;
  private final AssignmentService assignment;
  private final CalendarAuditRepository audits;
  private final StaffNames staff;
  private final Clock clock;

  AdvisorRoutingService(
      AdvisorProfileRepository profiles,
      LeadRepository leads,
      AvailabilityService availability,
      AssignmentService assignment,
      CalendarAuditRepository audits,
      StaffNames staff,
      Clock clock) {
    this.profiles = profiles;
    this.leads = leads;
    this.availability = availability;
    this.assignment = assignment;
    this.audits = audits;
    this.staff = staff;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<AdvisorRouting> list() {
    Snapshot s = availability.snapshot(null);
    Map<UUID, List<LeadStage>> stages =
        leads.findAll().stream()
            .filter(l -> l.getAdvisorUserId() != null && l.getAssignmentStatus() == AssignmentStatus.ASSIGNED)
            .collect(Collectors.groupingBy(l -> l.getAdvisorUserId(), Collectors.mapping(l -> l.getStage(), Collectors.toList())));
    return s.staff().values().stream()
        .filter(m -> ROUTABLE_ROLES.contains(m.roleCode()))
        .sorted(Comparator.comparing((StaffMember m) -> !"ADVISOR".equals(m.roleCode())).thenComparing(StaffMember::name))
        .map(m -> row(s, m, stages.getOrDefault(m.id(), List.of())))
        .toList();
  }

  public AdvisorRouting update(UUID userId, UpdateRouting req) {
    StaffMember member =
        staff.activeStaff().stream().filter(m -> m.id().equals(userId)).findFirst().orElseThrow(() -> new NotFoundException("Usuario no encontrado o inactivo"));
    AdvisorProfile profile = profiles.findById(userId).orElseGet(() -> new AdvisorProfile(userId));
    profile.update(req.receivesLeads(), req.weight(), req.dailyLimit(), req.online21UserId(), req.whatsappPhone());
    profiles.save(profile);
    return row(availability.snapshot(null), member, List.of());
  }

  @Transactional(readOnly = true)
  public SettingsView settings() {
    AssignmentSettings settings = assignment.settings();
    Snapshot s = availability.snapshot(null);
    long pending = leads.countByAssignmentStatusIn(List.of(AssignmentStatus.PENDING_OWNER, AssignmentStatus.PENDING_SCHEDULE));
    LocalDateTime next = availability.nextCoverage(s).map(CalendarService::local).orElse(null);
    boolean anyoneNow = s.profiles().keySet().stream().filter(s::active).anyMatch(id -> availability.state(s, id).status() == Status.AVAILABLE);
    return new SettingsView(settings.getRoundMode(), settings.getOwnerOffShiftPolicy(), settings.getUpdatedByName(), pending, next, !anyoneNow && next == null, "America/Mexico_City");
  }

  public SettingsView updateSettings(Mode mode, OwnerOffShiftPolicy policy, CurrentUser user) {
    AssignmentSettings settings = assignment.settings();
    String detail = "Ronda " + settings.getRoundMode() + " → " + mode + "; fuera de horario " + settings.getOwnerOffShiftPolicy() + " → " + policy;
    settings.update(mode, policy, user.name());
    audits.save(new CalendarAudit("SETTINGS", null, null, "UPDATED", detail, user.id(), user.name(), clock.instant()));
    return settings();
  }

  private AdvisorRouting row(Snapshot s, StaffMember m, List<LeadStage> stages) {
    AdvisorProfile p = s.profiles().get(m.id());
    AdvisorState st = availability.state(s, m.id());
    return new AdvisorRouting(
        m.id(),
        m.name(),
        m.email(),
        m.roleCode(),
        p != null && p.isReceivesLeads(),
        p != null ? p.getWeight() : 5,
        p != null ? p.getDailyLimit() : null,
        p != null ? p.getOnline21UserId() : null,
        p != null ? p.getWhatsappPhone() : null,
        st.status().name(),
        st.detail(),
        st.currentShift() == null ? null : CalendarService.local(st.currentShift().end()),
        st.currentShift() == null ? null : (st.currentShift().kind() == Kind.GUARD ? "GUARD" : "REGULAR"),
        st.nextShiftAt() == null ? null : CalendarService.local(st.nextShiftAt()),
        p != null && p.isCalendarNeedsSetup(),
        p != null ? p.getLegacyShift() : null,
        st.assignedToday(),
        stages.stream().filter(LeadStage::isOpen).count(),
        stages.stream().filter(x -> x == LeadStage.WON).count());
  }
}
