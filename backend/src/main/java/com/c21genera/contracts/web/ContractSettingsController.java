package com.c21genera.contracts.web;

import com.c21genera.contracts.application.ContractSettingsService;
import com.c21genera.contracts.domain.ContractSetting;
import com.c21genera.identity.CurrentUser;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Control de Cambios #21: texto estándar de "Instrucciones para visitas", editable por el administrador. */
@RestController
@PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
public class ContractSettingsController {

  private final ContractSettingsService service;

  public ContractSettingsController(ContractSettingsService service) {
    this.service = service;
  }

  public record VisitInstructionsResponse(String text, Instant updatedAt, String updatedByName) {

    static VisitInstructionsResponse from(ContractSetting s) {
      return new VisitInstructionsResponse(s.getValue(), s.getUpdatedAt(), s.getUpdatedByName());
    }
  }

  public record VisitInstructionsRequest(String text) {}

  @GetMapping("/api/v1/internal/settings/visit-instructions")
  public VisitInstructionsResponse visitInstructions() {
    return VisitInstructionsResponse.from(service.visitInstructions());
  }

  @PutMapping("/api/v1/internal/settings/visit-instructions")
  @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
  public VisitInstructionsResponse changeVisitInstructions(@RequestBody VisitInstructionsRequest request, @AuthenticationPrincipal Jwt jwt) {
    return VisitInstructionsResponse.from(service.changeVisitInstructions(request == null ? null : request.text(), CurrentUser.from(jwt).toActor()));
  }
}
