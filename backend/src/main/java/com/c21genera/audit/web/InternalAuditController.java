package com.c21genera.audit.web;

import com.c21genera.audit.application.AuditQueryService;
import com.c21genera.audit.web.AuditDtos.ActivityResponse;
import com.c21genera.audit.web.AuditDtos.AuditEventResponse;
import com.c21genera.identity.CurrentUser;
import com.c21genera.shared.security.ExpedienteAccessPolicy;
import com.c21genera.shared.web.PageResponse;
import com.c21genera.shared.web.Pagination;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalAuditController {

  private final AuditQueryService service;
  private final ExpedienteAccessPolicy accessPolicy;

  public InternalAuditController(AuditQueryService service, ExpedienteAccessPolicy accessPolicy) {
    this.service = service;
    this.accessPolicy = accessPolicy;
  }

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/activity")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
  public List<ActivityResponse> activity(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    accessPolicy.requireAccess(expedienteId, user.id(), user.permissions());
    return service.activityOf(expedienteId).stream().map(ActivityResponse::from).toList();
  }

  /** Control de Cambios #25: bitácora de cuentas (más reciente primero). Requiere AUDIT_VIEW. */
  @GetMapping("/api/v1/internal/audit-events/accounts")
  @PreAuthorize("hasAuthority('AUDIT_VIEW')")
  public PageResponse<AuditEventResponse> accountEvents(@PageableDefault(size = 50) Pageable pageable) {
    // Sin el orden que mande el cliente: siempre del más reciente al más antiguo.
    return PageResponse.of(service.accountEvents(Pagination.cap(PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()))).map(AuditEventResponse::from));
  }

  /** Rastro técnico, no pensado para el usuario final: requiere AUDIT_VIEW (administrador y director). */
  @GetMapping("/api/v1/internal/audit-events/{aggregateId}")
  @PreAuthorize("hasAuthority('AUDIT_VIEW')")
  public List<AuditEventResponse> auditEvents(@PathVariable UUID aggregateId) {
    return service.auditEventsOf(aggregateId).stream().map(AuditEventResponse::from).toList();
  }
}
