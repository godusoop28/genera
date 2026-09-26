package com.c21genera.expedientes.web;

import com.c21genera.expedientes.application.ExpedienteService;
import com.c21genera.expedientes.web.ManualClientDataDtos.ManualClientDataRequest;
import com.c21genera.expedientes.web.ManualClientDataDtos.ManualClientDataResponse;
import com.c21genera.identity.CurrentUser;
import com.c21genera.shared.security.ExpedienteAccessPolicy;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/expedientes/{id}/client-data")
@PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
public class ManualClientDataController {

  private final ExpedienteService expedienteService;
  private final ExpedienteAccessPolicy accessPolicy;

  public ManualClientDataController(ExpedienteService expedienteService, ExpedienteAccessPolicy accessPolicy) {
    this.expedienteService = expedienteService;
    this.accessPolicy = accessPolicy;
  }

  @GetMapping
  public ManualClientDataResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    accessPolicy.requireAccess(id, user.id(), user.permissions());
    return ManualClientDataResponse.from(expedienteService.manualDataOf(id));
  }

  @PutMapping
  @PreAuthorize("hasAuthority('EXPEDIENT_EDIT')")
  public ManualClientDataResponse update(
      @PathVariable UUID id, @Valid @RequestBody ManualClientDataRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    accessPolicy.requireAccess(id, user.id(), user.permissions());
    return ManualClientDataResponse.from(expedienteService.updateManualData(id, request.toUpdate(), user.toActor(), request.reason()));
  }
}
