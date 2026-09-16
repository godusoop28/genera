package com.c21genera.audit.web;

import com.c21genera.audit.application.AuditQueryService;
import com.c21genera.audit.web.AuditDtos.ActivityResponse;
import com.c21genera.audit.web.AuditDtos.AuditEventResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InternalAuditController {

  private final AuditQueryService service;

  public InternalAuditController(AuditQueryService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/activity")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
  public List<ActivityResponse> activity(@PathVariable UUID expedienteId) {
    return service.activityOf(expedienteId).stream().map(ActivityResponse::from).toList();
  }

  /** Rastro técnico, no pensado para el usuario final: se restringe a USER_MANAGE (solo ADMINISTRATOR, ver V001__identity.sql). */
  @GetMapping("/api/v1/internal/audit-events/{aggregateId}")
  @PreAuthorize("hasAuthority('USER_MANAGE')")
  public List<AuditEventResponse> auditEvents(@PathVariable UUID aggregateId) {
    return service.auditEventsOf(aggregateId).stream().map(AuditEventResponse::from).toList();
  }
}
