package com.c21genera.leads.application;

import com.c21genera.identity.UserDirectory.StaffMember;
import com.c21genera.leads.LeadStage;
import com.c21genera.leads.domain.AdvisorProfile;
import com.c21genera.leads.infrastructure.AdvisorProfileRepository;
import com.c21genera.leads.infrastructure.LeadRepository;
import com.c21genera.shared.domain.NotFoundException;
import java.time.Clock;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quién recibe leads: peso en la ruleta, límite diario, horario, usuario de 21Online y WhatsApp del aviso. */
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
      LocalTime shiftStart,
      LocalTime shiftEnd,
      Long online21UserId,
      String whatsappPhone,
      int assignedToday,
      long openLeads,
      long wonLeads) {}

  public record UpdateRouting(
      boolean receivesLeads, int weight, Integer dailyLimit, LocalTime shiftStart, LocalTime shiftEnd, Long online21UserId, String whatsappPhone) {}

  /** Roles que pueden atender leads (el revisor documental no). */
  private static final List<String> ROUTABLE_ROLES = List.of("ADVISOR", "DIRECTOR", "ADMINISTRATOR");

  private final AdvisorProfileRepository profiles;
  private final LeadRepository leads;
  private final AssignmentService assignment;
  private final StaffNames staff;
  private final Clock clock;

  AdvisorRoutingService(AdvisorProfileRepository profiles, LeadRepository leads, AssignmentService assignment, StaffNames staff, Clock clock) {
    this.profiles = profiles;
    this.leads = leads;
    this.assignment = assignment;
    this.staff = staff;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public List<AdvisorRouting> list() {
    Map<UUID, AdvisorProfile> byUser = profiles.findAll().stream().collect(Collectors.toMap(AdvisorProfile::getUserId, Function.identity()));
    Map<UUID, Integer> today = assignment.todayCounts(clock.instant().atZone(StaffNames.MEXICO_CITY));
    Map<UUID, List<LeadStage>> stages =
        leads.findAll().stream()
            .filter(l -> l.getAdvisorUserId() != null)
            .collect(Collectors.groupingBy(l -> l.getAdvisorUserId(), Collectors.mapping(l -> l.getStage(), Collectors.toList())));
    return staff.activeStaff().stream()
        .filter(m -> ROUTABLE_ROLES.contains(m.roleCode()))
        .sorted(Comparator.comparing((StaffMember m) -> !"ADVISOR".equals(m.roleCode())).thenComparing(StaffMember::name))
        .map(m -> row(m, byUser.get(m.id()), today, stages.getOrDefault(m.id(), List.of())))
        .toList();
  }

  public AdvisorRouting update(UUID userId, UpdateRouting req) {
    StaffMember member =
        staff.activeStaff().stream().filter(m -> m.id().equals(userId)).findFirst().orElseThrow(() -> new NotFoundException("Usuario no encontrado o inactivo"));
    AdvisorProfile profile = profiles.findById(userId).orElseGet(() -> new AdvisorProfile(userId));
    profile.update(req.receivesLeads(), req.weight(), req.dailyLimit(), req.shiftStart(), req.shiftEnd(), req.online21UserId(), req.whatsappPhone());
    profile = profiles.save(profile);
    return row(member, profile, assignment.todayCounts(clock.instant().atZone(StaffNames.MEXICO_CITY)), List.of());
  }

  private static AdvisorRouting row(StaffMember m, AdvisorProfile p, Map<UUID, Integer> today, List<LeadStage> stages) {
    return new AdvisorRouting(
        m.id(),
        m.name(),
        m.email(),
        m.roleCode(),
        p != null && p.isReceivesLeads(),
        p != null ? p.getWeight() : 5,
        p != null ? p.getDailyLimit() : null,
        p != null ? p.getShiftStart() : null,
        p != null ? p.getShiftEnd() : null,
        p != null ? p.getOnline21UserId() : null,
        p != null ? p.getWhatsappPhone() : null,
        today.getOrDefault(m.id(), 0),
        stages.stream().filter(LeadStage::isOpen).count(),
        stages.stream().filter(s -> s == LeadStage.WON).count());
  }
}
