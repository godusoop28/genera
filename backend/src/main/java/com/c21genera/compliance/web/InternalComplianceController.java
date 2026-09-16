package com.c21genera.compliance.web;

import com.c21genera.compliance.application.ComplianceChecklistService;
import com.c21genera.compliance.web.ComplianceDtos.ChecklistResponse;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
public class InternalComplianceController {

  private final ComplianceChecklistService service;

  public InternalComplianceController(ComplianceChecklistService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/compliance-checklist")
  public ChecklistResponse checklist(@PathVariable UUID expedienteId) {
    return ChecklistResponse.from(service.checklistOf(expedienteId));
  }
}
