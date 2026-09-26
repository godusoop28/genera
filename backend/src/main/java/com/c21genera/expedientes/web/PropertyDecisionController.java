package com.c21genera.expedientes.web;

import com.c21genera.expedientes.application.ExpedienteService;
import com.c21genera.expedientes.web.ExpedienteDtos.ExpedienteResponse;
import com.c21genera.expedientes.web.ExpedienteDtos.RejectPropertyRequest;
import com.c21genera.identity.CurrentUser;
import com.c21genera.shared.security.ExpedienteAccessPolicy;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aceptar/rechazar el inmueble (ver AGENTS §87/§88). Aceptar exige el
 * contrato de intermediación firmado por todas las partes; no se crea
 * Property, solo se cierra el ciclo del expediente (Módulo 2 consumirá el
 * evento PropertyAccepted más adelante).
 */
@RestController
@RequestMapping("/api/v1/internal/expedientes/{id}")
@PreAuthorize("hasAuthority('PROPERTY_DECIDE')")
public class PropertyDecisionController {

  private final ExpedienteService expedienteService;
  private final ExpedienteAccessPolicy accessPolicy;

  public PropertyDecisionController(ExpedienteService expedienteService, ExpedienteAccessPolicy accessPolicy) {
    this.expedienteService = expedienteService;
    this.accessPolicy = accessPolicy;
  }

  @PostMapping("/accept-property")
  public ExpedienteResponse accept(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser currentUser = CurrentUser.from(jwt);
    accessPolicy.requireAccess(id, currentUser.id(), currentUser.permissions());
    return ExpedienteResponse.from(expedienteService.acceptProperty(id, currentUser.toActor()));
  }

  @PostMapping("/reject-property")
  public ExpedienteResponse reject(
      @PathVariable UUID id, @Valid @RequestBody RejectPropertyRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser currentUser = CurrentUser.from(jwt);
    accessPolicy.requireAccess(id, currentUser.id(), currentUser.permissions());
    return ExpedienteResponse.from(expedienteService.rejectProperty(id, currentUser.toActor(), request.reason()));
  }
}
