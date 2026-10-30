package com.c21genera.expedientes.web;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.documents.DocumentsApi.DocumentProgressView;
import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.expedientes.OperationType;
import com.c21genera.expedientes.application.AssignableAdvisors;
import com.c21genera.expedientes.application.ExpedienteService;
import com.c21genera.expedientes.application.ExpedienteService.PortfolioFilter;
import com.c21genera.expedientes.domain.Expediente;
import com.c21genera.expedientes.web.ExpedienteDtos.ExpedienteResponse;
import com.c21genera.identity.CurrentUser;
import com.c21genera.propertycatalog.PropertyCatalog;
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

  /** counts: solo los activos; archived: cuántos archivados puede ver el usuario; closed (CC#32): activos con cierre completado. */
  public record OverviewResponse(PageResponse<OverviewItem> page, Counts counts, long archived, long closed) {}

  /**
   * Nivel 1 del reporte (Control de Cambios #24/#26): categoría → subcategoría + operación. subtypeId
   * null = pendientes de clasificación de esa categoría. Solo filas con expedientes.
   */
  public record TypeOperationRow(
      UUID categoryId, String categoryName, UUID subtypeId, String subtypeName, OperationType operationType, Counts counts) {}

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
  private final PropertyCatalog propertyCatalog;

  public ExpedienteOverviewController(
      ExpedienteService expedienteService, DocumentsApi documentsApi, AssignableAdvisors assignableAdvisors, PropertyCatalog propertyCatalog) {
    this.expedienteService = expedienteService;
    this.documentsApi = documentsApi;
    this.assignableAdvisors = assignableAdvisors;
    this.propertyCatalog = propertyCatalog;
  }

  @GetMapping
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
  public OverviewResponse overview(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) StatusGroup group,
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) UUID subtypeId,
      @RequestParam(defaultValue = "false") boolean unclassified,
      @RequestParam(required = false) OperationType operationType,
      @RequestParam(required = false) UUID advisorId,
      @RequestParam(defaultValue = "false") boolean unassigned,
      @RequestParam(defaultValue = "false") boolean archived,
      // CC#32: solo los que tienen el cierre completado.
      @RequestParam(defaultValue = "false") boolean closed,
      @PageableDefault(size = 18, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable,
      @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    UUID owner = user.hasPermission("EXPEDIENT_VIEW_ALL") ? null : user.id();
    PortfolioFilter filter = new PortfolioFilter(categoryId, subtypeId, unclassified, operationType, advisorId, unassigned);

    Page<Expediente> page = expedienteService.search(owner, q, group == null ? null : group.statuses(), filter, archived, closed, Pagination.cap(pageable));
    Map<UUID, DocumentProgressView> progress = documentsApi.documentProgressOf(page.map(Expediente::getId).getContent());
    PageResponse<OverviewItem> items =
        PageResponse.of(
            page.map(
                e -> {
                  DocumentProgressView p = progress.get(e.getId());
                  return new OverviewItem(
                      ExpedienteResponse.from(e), p == null ? new DocumentProgress(0, 0, 0) : new DocumentProgress(p.required(), p.received(), p.accepted()));
                }));

    return new OverviewResponse(items, countsOf(expedienteService.countByStatus(owner, filter)), expedienteService.countArchived(owner), expedienteService.countClosed(owner));
  }

  @GetMapping("/portfolio")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
  public PortfolioResponse portfolio(
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) UUID subtypeId,
      @RequestParam(defaultValue = "false") boolean unclassified,
      @RequestParam(required = false) OperationType operationType,
      @RequestParam(required = false) UUID advisorId,
      @RequestParam(defaultValue = "false") boolean unassigned,
      @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    boolean viewAll = user.hasPermission("EXPEDIENT_VIEW_ALL");
    List<Object[]> rows = expedienteService.portfolioRows(viewAll ? null : user.id());
    PortfolioFilter all = new PortfolioFilter(categoryId, subtypeId, unclassified, operationType, advisorId, unassigned);
    PortfolioFilter byAdvisorOnly = new PortfolioFilter(null, null, false, null, advisorId, unassigned);
    PortfolioFilter byTypeOnly = new PortfolioFilter(categoryId, subtypeId, unclassified, operationType, null, false);

    // Control de Cambios #26: una fila por categoría → subcategoría (o pendientes) y operación, en el orden del catálogo.
    List<TypeOperationRow> byTypeOperation = new ArrayList<>();
    List<PropertyCatalog.Category> catalog = propertyCatalog.all();
    Map<UUID, Integer> order = new java.util.HashMap<>();
    Map<UUID, String> labels = new java.util.HashMap<>();
    int position = 0;
    for (PropertyCatalog.Category category : catalog) {
      order.put(category.id(), position++);
      labels.put(category.id(), category.name());
      for (PropertyCatalog.Subtype subtype : category.subtypes()) {
        order.put(subtype.id(), position++);
        labels.put(subtype.id(), subtype.name());
      }
    }
    Map<java.util.List<Object>, Map<ExpedienteStatus, Long>> cells = new LinkedHashMap<>();
    for (Object[] row : rows) {
      if (ExpedienteService.matches(row, byAdvisorOnly)) {
        cells.computeIfAbsent(java.util.Arrays.asList(row[6], row[7], row[1]), k -> new java.util.EnumMap<>(ExpedienteStatus.class))
            .merge((ExpedienteStatus) row[4], ((Number) row[5]).longValue(), Long::sum);
      }
    }
    cells.forEach(
        (key, totals) -> {
          UUID category = (UUID) key.get(0);
          UUID subtype = (UUID) key.get(1);
          byTypeOperation.add(
              new TypeOperationRow(
                  category,
                  category == null ? "Sin clasificar" : labels.getOrDefault(category, "Sin clasificar"),
                  subtype,
                  subtype == null ? "Pendiente de clasificación" : labels.getOrDefault(subtype, "Subcategoría"),
                  (OperationType) key.get(2),
                  countsOf(totals)));
        });
    byTypeOperation.sort(
        java.util.Comparator.comparingInt((TypeOperationRow r) -> r.categoryId() == null ? Integer.MAX_VALUE : order.getOrDefault(r.categoryId(), Integer.MAX_VALUE - 1))
            .thenComparingInt(r -> r.subtypeId() == null ? Integer.MAX_VALUE : order.getOrDefault(r.subtypeId(), Integer.MAX_VALUE - 1))
            .thenComparing(TypeOperationRow::operationType));

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
          PortfolioFilter advisor = new PortfolioFilter(null, null, false, null, id, id == null);
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
