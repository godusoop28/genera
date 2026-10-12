package com.c21genera.leads.application;

import com.c21genera.identity.CurrentUser;
import com.c21genera.leads.LeadSource;
import com.c21genera.leads.LeadStage;
import com.c21genera.leads.application.LeadService.LeadItem;
import com.c21genera.leads.domain.Lead;
import com.c21genera.leads.infrastructure.LeadRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Indicadores del CRM para un periodo: embudo, fuentes, asesores, propiedades con más interés y últimos leads. */
@Service
@Transactional(readOnly = true)
public class CrmDashboardService {

  public record AdvisorRow(UUID advisorId, String name, long leads, long won) {}

  public record PropertyRow(Long online21Id, String key, String title, long leads) {}

  public record Dashboard(
      Instant from,
      long total,
      long assigned,
      long unassigned,
      long pendingAssignment,
      long open,
      long won,
      long closed,
      long staleNew,
      Map<LeadStage, Long> byStage,
      Map<LeadSource, Long> bySource,
      List<AdvisorRow> byAdvisor,
      List<PropertyRow> topProperties,
      List<LeadItem> recent) {}

  private final LeadRepository leads;
  private final LeadService leadService;
  private final StaffNames staff;
  private final Clock clock;

  CrmDashboardService(LeadRepository leads, LeadService leadService, StaffNames staff, Clock clock) {
    this.leads = leads;
    this.leadService = leadService;
    this.staff = staff;
    this.clock = clock;
  }

  public Dashboard build(Instant from, CurrentUser user) {
    Instant since = from == null ? Instant.EPOCH : from;
    List<Lead> period =
        user.hasPermission("LEAD_VIEW_ALL") ? leads.findByCreatedAtGreaterThanEqual(since) : leads.findByCreatedAtGreaterThanEqualAndAdvisorUserId(since, user.id());
    Instant now = clock.instant();
    Map<UUID, String> names = staff.names();

    Map<LeadStage, Long> byStage = new EnumMap<>(LeadStage.class);
    for (LeadStage s : LeadStage.values()) {
      byStage.put(s, period.stream().filter(l -> l.getStage() == s).count());
    }
    Map<LeadSource, Long> bySource = new EnumMap<>(LeadSource.class);
    for (Lead l : period) {
      bySource.merge(l.getSource(), 1L, Long::sum);
    }
    List<AdvisorRow> byAdvisor =
        period.stream()
            .filter(l -> l.getAdvisorUserId() != null && l.getAssignmentStatus() == com.c21genera.leads.AssignmentStatus.ASSIGNED)
            .collect(Collectors.groupingBy(Lead::getAdvisorUserId, LinkedHashMap::new, Collectors.toList()))
            .entrySet()
            .stream()
            .map(e -> new AdvisorRow(e.getKey(), staff.nameOf(e.getKey(), names), e.getValue().size(), e.getValue().stream().filter(l -> l.getStage() == LeadStage.WON).count()))
            .sorted(Comparator.comparingLong(AdvisorRow::leads).reversed())
            .toList();
    List<PropertyRow> topProperties =
        period.stream()
            .filter(l -> l.getPropertyKey() != null || l.getPropertyOnline21Id() != null)
            .collect(Collectors.groupingBy(l -> Objects.requireNonNullElse(l.getPropertyOnline21Id() != null ? l.getPropertyOnline21Id().toString() : null, l.getPropertyKey())))
            .values()
            .stream()
            .map(list -> new PropertyRow(list.get(0).getPropertyOnline21Id(), list.get(0).getPropertyKey(), list.get(0).getPropertyTitle(), list.size()))
            .sorted(Comparator.comparingLong(PropertyRow::leads).reversed())
            .limit(6)
            .toList();
    List<LeadItem> recent =
        period.stream().sorted(Comparator.comparing(Lead::getCreatedAt).reversed()).limit(6).map(l -> leadService.item(l, names)).toList();

    long won = byStage.get(LeadStage.WON);
    return new Dashboard(
        from,
        period.size(),
        period.stream().filter(l -> l.getAssignmentStatus() == com.c21genera.leads.AssignmentStatus.ASSIGNED).count(),
        period.stream().filter(l -> l.getAssignmentStatus() == com.c21genera.leads.AssignmentStatus.UNASSIGNED).count(),
        period.stream().filter(l -> l.getAssignmentStatus().isPending()).count(),
        period.stream().filter(l -> l.getStage().isOpen()).count(),
        won,
        won + byStage.get(LeadStage.LOST),
        period.stream().filter(l -> l.getStage() == LeadStage.NEW && Duration.between(l.getCreatedAt(), now).toHours() >= 24).count(),
        byStage,
        bySource,
        byAdvisor,
        topProperties,
        recent);
  }
}
