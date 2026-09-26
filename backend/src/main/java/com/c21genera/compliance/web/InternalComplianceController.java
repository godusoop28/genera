package com.c21genera.compliance.web;

import com.c21genera.compliance.application.ComplianceChecklistService;
import com.c21genera.compliance.web.ComplianceDtos.ChecklistResponse;
import com.c21genera.identity.CurrentUser;
import com.c21genera.shared.security.ExpedienteAccessPolicy;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
public class InternalComplianceController {

  private final ComplianceChecklistService service;
  private final ExpedienteAccessPolicy accessPolicy;

  public InternalComplianceController(ComplianceChecklistService service, ExpedienteAccessPolicy accessPolicy) {
    this.service = service;
    this.accessPolicy = accessPolicy;
  }

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/compliance-checklist")
  public ChecklistResponse checklist(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    accessPolicy.requireAccess(expedienteId, user.id(), user.permissions());
    return ChecklistResponse.from(service.checklistOf(expedienteId));
  }
}
