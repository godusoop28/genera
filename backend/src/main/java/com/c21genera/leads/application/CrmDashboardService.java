package com.c21genera.leads.application;

import com.c21genera.identity.CurrentUser;
import com.c21genera.leads.AssignmentStatus;
import com.c21genera.leads.LeadSource;
import com.c21genera.leads.LeadStage;
import com.c21genera.leads.application.LeadService.LeadItem;
import com.c21genera.leads.application.LeadSourceCatalog.SourceOption;
import com.c21genera.leads.domain.Lead;
import com.c21genera.leads.domain.LeadSourceOption;
import com.c21genera.leads.domain.LeadStageHistory;
import com.c21genera.leads.domain.LeadTask;
import com.c21genera.leads.infrastructure.LeadRepository;
import com.c21genera.leads.infrastructure.LeadStageHistoryRepository;
import com.c21genera.leads.infrastructure.LeadTaskRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Indicadores del CRM para un periodo: embudo, fuentes (CC#29), productividad
 * por asesor, pipeline y leads en riesgo (CC#30), propiedades con más interés
 * y últimos leads.
 */
@Service
@Transactional(readOnly = true)
public class CrmDashboardService {

  /** Etapas que cuentan como conversión (CC#30: "Oferta aceptada" o "Cierre"). */
  private static final Set<LeadStage> CONVERTED = Set.of(LeadStage.OFFER_ACCEPTED, LeadStage.WON);

  /**
   * Productividad del asesor. activeLeads/pendingTasks/overdueTasks/staleLeads
   * son el estado actual; leads/won/converted/tasksDone son del periodo.
   */
  public record AdvisorRow(
      UUID advisorId, String name, long leads, long won, long converted, double conversionRate, long activeLeads, long tasksDone, long pendingTasks, long overdueTasks, long staleLeads) {

    public AdvisorRow(UUID advisorId, String name, long leads, long won) {
      this(advisorId, name, leads, won, won, 0, 0, 0, 0, 0, 0);
    }
  }

  public record PropertyRow(Long online21Id, String key, String title, long leads) {}

  /** CC#29: participación y conversión por fuente del catálogo. */
  public record SourceRow(String code, String label, LeadSourceOption.Category category, long leads, double share, long converted, double conversionRate) {}

  /** Leads por fuente en cada mes (últimos 6 meses), para la tendencia. */
  public record SourceTrend(List<String> months, List<SourceSeries> series) {}

  public record SourceSeries(String code, String label, List<Long> counts) {}

  public record StageTime(LeadStage stage, double averageDays, long samples) {}

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
      List<LeadItem> recent,
      // Control de Cambios #29 y #30.
      long converted,
      double conversionRate,
      BigDecimal pipelineValue,
      long staleLeads,
      int staleDays,
      List<LeadItem> atRisk,
      List<SourceRow> sources,
      SourceTrend sourceTrend,
      List<StageTime> stageTimes,
      long pendingTasks,
      long overdueTasks) {}

  private final LeadRepository leads;
  private final LeadTaskRepository tasks;
  private final LeadStageHistoryRepository history;
  private final LeadService leadService;
  private final LeadSourceCatalog sourceCatalog;
  private final AdvisorRoutingService routing;
  private final StaffNames staff;
  private final Clock clock;

  CrmDashboardService(
      LeadRepository leads,
      LeadTaskRepository tasks,
      LeadStageHistoryRepository history,
      LeadService leadService,
      LeadSourceCatalog sourceCatalog,
      AdvisorRoutingService routing,
      StaffNames staff,
      Clock clock) {
    this.leads = leads;
    this.tasks = tasks;
    this.history = history;
    this.leadService = leadService;
    this.sourceCatalog = sourceCatalog;
    this.routing = routing;
    this.staff = staff;
    this.clock = clock;
  }

  public Dashboard build(Instant from, CurrentUser user) {
    Instant since = from == null ? Instant.EPOCH : from;
    boolean all = user.hasPermission("LEAD_VIEW_ALL");
    List<Lead> period = all ? leads.findByCreatedAtGreaterThanEqual(since) : leads.findByCreatedAtGreaterThanEqualAndAdvisorUserId(since, user.id());
    List<Lead> everyone = all ? leads.findAll() : leads.findByCreatedAtGreaterThanEqualAndAdvisorUserId(Instant.EPOCH, user.id());
    Instant now = clock.instant();
    int staleDays = routing.staleDays();
    Instant staleBefore = now.minus(Duration.ofDays(staleDays));
    Map<UUID, String> names = staff.names();

    Map<LeadStage, Long> byStage = new EnumMap<>(LeadStage.class);
    for (LeadStage s : LeadStage.values()) {
      byStage.put(s, period.stream().filter(l -> l.getStage() == s).count());
    }
    Map<LeadSource, Long> bySource = new EnumMap<>(LeadSource.class);
    for (Lead l : period) {
      bySource.merge(l.getSource(), 1L, Long::sum);
    }

    // Estado actual (no depende del periodo): actividades pendientes y leads sin movimiento.
    Set<UUID> visibleIds = everyone.stream().map(Lead::getId).collect(Collectors.toSet());
    List<LeadTask> scheduled = tasks.findByStatus(LeadTask.Status.SCHEDULED).stream().filter(t -> visibleIds.contains(t.getLeadId())).toList();
    List<LeadTask> donePeriod = tasks.findByCompletedAtGreaterThanEqual(since).stream().filter(t -> t.getStatus() == LeadTask.Status.DONE && visibleIds.contains(t.getLeadId())).toList();
    List<Lead> openLeads = everyone.stream().filter(l -> l.getStage().isOpen()).toList();
    List<Lead> stale = openLeads.stream().filter(l -> l.getLastActivityAt().isBefore(staleBefore)).toList();

    List<AdvisorRow> byAdvisor = advisorRows(period, openLeads, scheduled, donePeriod, staleBefore, names, now);
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
    List<LeadItem> recent = leadService.items(period.stream().sorted(Comparator.comparing(Lead::getCreatedAt).reversed()).limit(6).toList());
    List<LeadItem> atRisk =
        leadService.items(stale.stream().filter(l -> l.getStage().isAdvanced() || l.getStage() == LeadStage.PROPOSAL).sorted(Comparator.comparing(Lead::getLastActivityAt)).limit(8).toList());

    long won = byStage.get(LeadStage.WON);
    long converted = period.stream().filter(l -> CONVERTED.contains(l.getStage())).count();
    BigDecimal pipelineValue =
        openLeads.stream().filter(l -> l.getStage().isAdvanced()).map(Lead::getBudget).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new Dashboard(
        from,
        period.size(),
        period.stream().filter(l -> l.getAssignmentStatus() == AssignmentStatus.ASSIGNED).count(),
        period.stream().filter(l -> l.getAssignmentStatus() == AssignmentStatus.UNASSIGNED).count(),
        period.stream().filter(l -> l.getAssignmentStatus().isPending()).count(),
        period.stream().filter(l -> l.getStage().isOpen()).count(),
        won,
        won + byStage.get(LeadStage.LOST),
        period.stream().filter(l -> l.getStage() == LeadStage.NEW && Duration.between(l.getCreatedAt(), now).toHours() >= 24).count(),
        byStage,
        bySource,
        byAdvisor,
        topProperties,
        recent,
        converted,
        rate(converted, period.size()),
        pipelineValue,
        stale.size(),
        staleDays,
        atRisk,
        sourceRows(period),
        sourceTrend(everyone, now),
        stageTimes(period, now),
        scheduled.size(),
        scheduled.stream().filter(t -> t.isOverdue(now)).count());
  }

  private List<AdvisorRow> advisorRows(List<Lead> period, List<Lead> openLeads, List<LeadTask> scheduled, List<LeadTask> donePeriod, Instant staleBefore, Map<UUID, String> names, Instant now) {
    Map<UUID, List<Lead>> periodByAdvisor =
        period.stream().filter(l -> l.getAdvisorUserId() != null && l.getAssignmentStatus() == AssignmentStatus.ASSIGNED).collect(Collectors.groupingBy(Lead::getAdvisorUserId, LinkedHashMap::new, Collectors.toList()));
    Map<UUID, List<Lead>> openByAdvisor = openLeads.stream().filter(l -> l.getAdvisorUserId() != null).collect(Collectors.groupingBy(Lead::getAdvisorUserId));
    Map<UUID, UUID> leadAdvisor = new HashMap<>();
    openLeads.forEach(l -> leadAdvisor.put(l.getId(), l.getAdvisorUserId()));
    period.forEach(l -> leadAdvisor.putIfAbsent(l.getId(), l.getAdvisorUserId()));
    Map<UUID, List<LeadTask>> pendingByAdvisor = scheduled.stream().filter(t -> owner(t, leadAdvisor) != null).collect(Collectors.groupingBy(t -> owner(t, leadAdvisor)));
    Map<UUID, Long> doneByAdvisor = donePeriod.stream().filter(t -> owner(t, leadAdvisor) != null).collect(Collectors.groupingBy(t -> owner(t, leadAdvisor), Collectors.counting()));

    Set<UUID> advisors = new java.util.LinkedHashSet<>(periodByAdvisor.keySet());
    advisors.addAll(openByAdvisor.keySet());
    advisors.addAll(pendingByAdvisor.keySet());
    advisors.addAll(doneByAdvisor.keySet());
    List<AdvisorRow> rows = new ArrayList<>();
    for (UUID id : advisors) {
      List<Lead> mine = periodByAdvisor.getOrDefault(id, List.of());
      long won = mine.stream().filter(l -> l.getStage() == LeadStage.WON).count();
      long conv = mine.stream().filter(l -> CONVERTED.contains(l.getStage())).count();
      List<Lead> open = openByAdvisor.getOrDefault(id, List.of());
      List<LeadTask> pending = pendingByAdvisor.getOrDefault(id, List.of());
      rows.add(
          new AdvisorRow(
              id,
              staff.nameOf(id, names),
              mine.size(),
              won,
              conv,
              rate(conv, mine.size()),
              open.size(),
              doneByAdvisor.getOrDefault(id, 0L),
              pending.size(),
              pending.stream().filter(t -> t.isOverdue(now)).count(),
              open.stream().filter(l -> l.getLastActivityAt().isBefore(staleBefore)).count()));
    }
    rows.sort(Comparator.comparingLong(AdvisorRow::leads).thenComparingLong(AdvisorRow::activeLeads).reversed());
    return rows;
  }

  private static UUID owner(LeadTask t, Map<UUID, UUID> leadAdvisor) {
    return t.getAdvisorUserId() != null ? t.getAdvisorUserId() : leadAdvisor.get(t.getLeadId());
  }

  private List<SourceRow> sourceRows(List<Lead> period) {
    Map<String, SourceOption> catalog = sourceCatalog.byCode();
    Map<String, List<Lead>> byCode = period.stream().collect(Collectors.groupingBy(l -> l.getSourceCode() == null ? l.getSource().defaultCode() : l.getSourceCode()));
    return byCode.entrySet().stream()
        .map(
            e -> {
              SourceOption o = catalog.get(e.getKey());
              long conv = e.getValue().stream().filter(l -> CONVERTED.contains(l.getStage())).count();
              return new SourceRow(
                  e.getKey(), o == null ? e.getKey() : o.label(), o == null ? LeadSourceOption.Category.UNKNOWN : o.category(), e.getValue().size(), rate(e.getValue().size(), period.size()), conv, rate(conv, e.getValue().size()));
            })
        .sorted(Comparator.comparingLong(SourceRow::leads).reversed())
        .toList();
  }

  /** Últimos 6 meses (incluido el actual), con las 6 fuentes que más leads trajeron en ese lapso. */
  private SourceTrend sourceTrend(List<Lead> everyone, Instant now) {
    YearMonth current = YearMonth.from(LocalDate.ofInstant(now, StaffNames.MEXICO_CITY));
    List<YearMonth> months = new ArrayList<>();
    for (int i = 5; i >= 0; i--) {
      months.add(current.minusMonths(i));
    }
    YearMonth first = months.get(0);
    Map<String, Map<YearMonth, Long>> counts = new HashMap<>();
    for (Lead l : everyone) {
      YearMonth m = YearMonth.from(LocalDate.ofInstant(l.getCreatedAt(), StaffNames.MEXICO_CITY));
      if (!m.isBefore(first)) {
        String code = l.getSourceCode() == null ? l.getSource().defaultCode() : l.getSourceCode();
        counts.computeIfAbsent(code, k -> new HashMap<>()).merge(m, 1L, Long::sum);
      }
    }
    Map<String, SourceOption> catalog = sourceCatalog.byCode();
    List<SourceSeries> series =
        counts.entrySet().stream()
            .sorted(Comparator.comparingLong((Map.Entry<String, Map<YearMonth, Long>> e) -> e.getValue().values().stream().mapToLong(Long::longValue).sum()).reversed())
            .limit(6)
            .map(e -> new SourceSeries(e.getKey(), catalog.containsKey(e.getKey()) ? catalog.get(e.getKey()).label() : e.getKey(), months.stream().map(m -> e.getValue().getOrDefault(m, 0L)).toList()))
            .toList();
    return new SourceTrend(months.stream().map(YearMonth::toString).toList(), series);
  }

  /** Tiempo promedio en cada etapa, con las etapas ya concluidas de los leads del periodo. */
  private List<StageTime> stageTimes(List<Lead> period, Instant now) {
    if (period.isEmpty()) {
      return List.of();
    }
    Map<LeadStage, List<Double>> samples = new EnumMap<>(LeadStage.class);
    for (LeadStageHistory h : history.findByLeadIdIn(period.stream().map(Lead::getId).toList())) {
      if (h.getExitedAt() != null && h.getStage().isOpen()) {
        samples.computeIfAbsent(h.getStage(), k -> new ArrayList<>()).add(LeadService.days(h.getEnteredAt(), h.getExitedAt()));
      }
    }
    return samples.entrySet().stream()
        .map(e -> new StageTime(e.getKey(), Math.round(e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0) * 10) / 10.0, e.getValue().size()))
        .toList();
  }

  private static double rate(long part, long total) {
    return total == 0 ? 0 : Math.round(part * 1000.0 / total) / 10.0;
  }
}
