package com.c21genera.expedientes.web;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.documents.DocumentsApi.DocumentProgressView;
import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.expedientes.OperationType;
import com.c21genera.expedientes.PropertyCaseType;
import com.c21genera.expedientes.application.AssignableAdvisors;
import com.c21genera.expedientes.application.ExpedienteService;
import com.c21genera.expedientes.application.ExpedienteService.PortfolioFilter;
import com.c21genera.expedientes.domain.Expediente;
import com.c21genera.expedientes.web.ExpedienteDtos.ExpedienteResponse;
import com.c21genera.identity.CurrentUser;
import com.c21genera.shared.web.PageResponse;
import com.c21genera.shared.web.Pagination;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vista de trabajo del listado de expedientes (solo lectura): búsqueda,
 * filtro por grupo de estatus, totales por grupo sobre TODOS los expedientes
 * que el usuario puede ver (no solo la página cargada) y el avance documental
 * de cada expediente de la página. Misma visibilidad que GET /expedientes.
 *
 * <p>Control de Cambios #24: filtros por tipo de propiedad, operación y asesor
 * asignado, y GET /portfolio con la cartera agregada para reportes y dashboards.
 */
@RestController
@RequestMapping("/api/v1/internal/expedientes/overview")
public class ExpedienteOverviewController {

  /**
   * Agrupación explícita de los estatus para los filtros. Cada estatus está
   * en exactamente un grupo, así que pendientes + revisión + completos = total.
   */
  public enum StatusGroup {
    /** Falta algo del cliente: liga, aviso de privacidad, documentos o correcciones. */
    PENDING(
        EnumSet.of(
            ExpedienteStatus.DRAFT,
            ExpedienteStatus.WAITING_PRIVACY,
            ExpedienteStatus.WAITING_DOCUMENTS,
            ExpedienteStatus.CORRECTIONS_REQUESTED)),
    /** El cliente envió documentos y el revisor los está revisando. */
    IN_REVIEW(EnumSet.of(ExpedienteStatus.DOCUMENTS_RECEIVED, ExpedienteStatus.UNDER_REVIEW)),
    /** Documentación aprobada; de aquí en adelante es contrato, firma, decisión y cierre. */
    COMPLETE(
        EnumSet.of(
            ExpedienteStatus.DOCUMENTS_APPROVED,
            ExpedienteStatus.RECEPTION_SIGNED,
            ExpedienteStatus.CONTRACT_PREPARATION,
            ExpedienteStatus.READY_FOR_SIGNATURE,
            ExpedienteStatus.CONTRACT_SIGNED,
            ExpedienteStatus.PROPERTY_ACCEPTED,
            ExpedienteStatus.PROPERTY_REJECTED,
            ExpedienteStatus.CLOSED));

    private final Set<ExpedienteStatus> statuses;

    StatusGroup(Set<ExpedienteStatus> statuses) {
      this.statuses = statuses;
    }

    public Set<ExpedienteStatus> statuses() {
      return statuses;
    }
  }

  public record DocumentProgress(int required, int received, int accepted) {}

  public record OverviewItem(ExpedienteResponse expediente, DocumentProgress documents) {}

  public record Counts(long total, long pending, long inReview, long complete) {}

  public record OverviewResponse(PageResponse<OverviewItem> page, Counts counts) {}

  /** Nivel 1 del reporte: tipo de propiedad + operación. */
  public record TypeOperationRow(PropertyCaseType propertyCaseType, OperationType operationType, Counts counts) {}

  /** Nivel 2 del reporte: asesor asignado (advisorId null = sin asignar). */
  public record AdvisorRow(UUID advisorId, String name, Counts counts) {}

  /**
   * byTypeOperation respeta solo el filtro de asesor; byAdvisor, solo el de tipo y operación;
   * total, todos los filtros (así cada nivel muestra a dónde se puede ir).
   */
  public record PortfolioResponse(Counts total, List<TypeOperationRow> byTypeOperation, List<AdvisorRow> byAdvisor) {}

  private final ExpedienteService expedienteService;
  private final DocumentsApi documentsApi;
  private final AssignableAdvisors assignableAdvisors;

  public ExpedienteOverviewController(ExpedienteService expedienteService, DocumentsApi documentsApi, AssignableAdvisors assignableAdvisors) {
    this.expedienteService = expedienteService;
    this.documentsApi = documentsApi;
    this.assignableAdvisors = assignableAdvisors;
  }

  @GetMapping
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
  public OverviewResponse overview(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) StatusGroup group,
      @RequestParam(required = false) PropertyCaseType propertyCaseType,
      @RequestParam(required = false) OperationType operationType,
      @RequestParam(required = false) UUID advisorId,
      @RequestParam(defaultValue = "false") boolean unassigned,
      @PageableDefault(size = 18, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable,
      @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    UUID owner = user.hasPermission("EXPEDIENT_VIEW_ALL") ? null : user.id();
    PortfolioFilter filter = new PortfolioFilter(propertyCaseType, operationType, advisorId, unassigned);

    Page<Expediente> page = expedienteService.search(owner, q, group == null ? null : group.statuses(), filter, Pagination.cap(pageable));
    Map<UUID, DocumentProgressView> progress = documentsApi.documentProgressOf(page.map(Expediente::getId).getContent());
    PageResponse<OverviewItem> items =
        PageResponse.of(
            page.map(
                e -> {
                  DocumentProgressView p = progress.get(e.getId());
                  return new OverviewItem(
                      ExpedienteResponse.from(e), p == null ? new DocumentProgress(0, 0, 0) : new DocumentProgress(p.required(), p.received(), p.accepted()));
                }));

    return new OverviewResponse(items, countsOf(expedienteService.countByStatus(owner, filter)));
  }

  @GetMapping("/portfolio")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
  public PortfolioResponse portfolio(
      @RequestParam(required = false) PropertyCaseType propertyCaseType,
      @RequestParam(required = false) OperationType operationType,
      @RequestParam(required = false) UUID advisorId,
      @RequestParam(defaultValue = "false") boolean unassigned,
      @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    boolean viewAll = user.hasPermission("EXPEDIENT_VIEW_ALL");
    List<Object[]> rows = expedienteService.portfolioRows(viewAll ? null : user.id());
    PortfolioFilter all = new PortfolioFilter(propertyCaseType, operationType, advisorId, unassigned);
    PortfolioFilter byAdvisorOnly = new PortfolioFilter(null, null, advisorId, unassigned);
    PortfolioFilter byTypeOnly = new PortfolioFilter(propertyCaseType, operationType, null, false);

    List<TypeOperationRow> byTypeOperation = new ArrayList<>();
    for (PropertyCaseType type : PropertyCaseType.values()) {
      for (OperationType operation : OperationType.values()) {
        PortfolioFilter cell = new PortfolioFilter(type, operation, null, false);
        byTypeOperation.add(
            new TypeOperationRow(
                type, operation, countsOf(statusTotals(rows, r -> ExpedienteService.matches(r, byAdvisorOnly) && ExpedienteService.matches(r, cell)))));
      }
    }

    // Todos los asesores asignables aparecen (aunque tengan cero) para quien ve toda la cartera.
    Map<UUID, String> names = new LinkedHashMap<>();
    if (viewAll) {
      assignableAdvisors.list().forEach(m -> names.put(m.id(), m.name()));
    }
    for (Object[] row : rows) {
      if (ExpedienteService.matches(row, byTypeOnly)) {
        names.putIfAbsent((UUID) row[2], row[3] == null ? null : (String) row[3]);
      }
    }
    List<AdvisorRow> byAdvisor = new ArrayList<>();
    names.forEach(
        (id, name) -> {
          PortfolioFilter advisor = new PortfolioFilter(null, null, id, id == null);
          Counts counts = countsOf(statusTotals(rows, r -> ExpedienteService.matches(r, byTypeOnly) && ExpedienteService.matches(r, advisor)));
          byAdvisor.add(new AdvisorRow(id, id == null ? "Sin asesor asignado" : name, counts));
        });
    byAdvisor.sort(java.util.Comparator.comparing((AdvisorRow a) -> -a.counts().total()).thenComparing(a -> a.name() == null ? "" : a.name()));

    return new PortfolioResponse(countsOf(statusTotals(rows, r -> ExpedienteService.matches(r, all))), byTypeOperation, byAdvisor);
  }

  private static Map<ExpedienteStatus, Long> statusTotals(List<Object[]> rows, java.util.function.Predicate<Object[]> include) {
    Map<ExpedienteStatus, Long> totals = new java.util.EnumMap<>(ExpedienteStatus.class);
    for (Object[] row : rows) {
      if (include.test(row)) {
        totals.merge((ExpedienteStatus) row[4], ((Number) row[5]).longValue(), Long::sum);
      }
    }
    return totals;
  }

  private static Counts countsOf(Map<ExpedienteStatus, Long> byStatus) {
    long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
    return new Counts(total, sum(byStatus, StatusGroup.PENDING), sum(byStatus, StatusGroup.IN_REVIEW), sum(byStatus, StatusGroup.COMPLETE));
  }

  private static long sum(Map<ExpedienteStatus, Long> byStatus, StatusGroup group) {
    return group.statuses().stream().mapToLong(s -> byStatus.getOrDefault(s, 0L)).sum();
  }
}
