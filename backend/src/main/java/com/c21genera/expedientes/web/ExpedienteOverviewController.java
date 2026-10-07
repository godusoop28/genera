package com.c21genera.expedientes.web;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.documents.DocumentsApi.DocumentProgressView;
import com.c21genera.expedientes.ExpedienteStatus;
import com.c21genera.expedientes.application.ExpedienteService;
import com.c21genera.expedientes.domain.Expediente;
import com.c21genera.expedientes.web.ExpedienteDtos.ExpedienteResponse;
import com.c21genera.identity.CurrentUser;
import com.c21genera.shared.web.PageResponse;
import com.c21genera.shared.web.Pagination;
import java.util.EnumSet;
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

  private final ExpedienteService expedienteService;
  private final DocumentsApi documentsApi;

  public ExpedienteOverviewController(ExpedienteService expedienteService, DocumentsApi documentsApi) {
    this.expedienteService = expedienteService;
    this.documentsApi = documentsApi;
  }

  @GetMapping
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
  public OverviewResponse overview(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) StatusGroup group,
      @PageableDefault(size = 18, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable,
      @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    UUID owner = user.hasPermission("EXPEDIENT_VIEW_ALL") ? null : user.id();

    Page<Expediente> page = expedienteService.search(owner, q, group == null ? null : group.statuses(), Pagination.cap(pageable));
    Map<UUID, DocumentProgressView> progress = documentsApi.documentProgressOf(page.map(Expediente::getId).getContent());
    PageResponse<OverviewItem> items =
        PageResponse.of(
            page.map(
                e -> {
                  DocumentProgressView p = progress.get(e.getId());
                  return new OverviewItem(
                      ExpedienteResponse.from(e), p == null ? new DocumentProgress(0, 0, 0) : new DocumentProgress(p.required(), p.received(), p.accepted()));
                }));

    Map<ExpedienteStatus, Long> byStatus = expedienteService.countByStatus(owner);
    long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
    return new OverviewResponse(items, new Counts(total, sum(byStatus, StatusGroup.PENDING), sum(byStatus, StatusGroup.IN_REVIEW), sum(byStatus, StatusGroup.COMPLETE)));
  }

  private static long sum(Map<ExpedienteStatus, Long> byStatus, StatusGroup group) {
    return group.statuses().stream().mapToLong(s -> byStatus.getOrDefault(s, 0L)).sum();
  }
}
