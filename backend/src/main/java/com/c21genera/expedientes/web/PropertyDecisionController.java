package com.c21genera.expedientes.web;

import com.c21genera.expedientes.application.ExpedienteService;
import com.c21genera.expedientes.web.ExpedienteDtos.ExpedienteResponse;
import com.c21genera.expedientes.web.ExpedienteDtos.RejectPropertyRequest;
import com.c21genera.identity.CurrentUser;
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
 * Aceptar/rechazar el inmueble (ver AGENTS §87/§88). La máquina de estados
 * ya garantiza que no se puede llegar aquí sin recepción firmada y
 * documentos aprobados: no se crea Property, solo se cierra el ciclo del
 * expediente (Módulo 2 consumirá el evento PropertyAccepted más adelante).
 */
@RestController
@RequestMapping("/api/v1/internal/expedientes/{id}")
@PreAuthorize("hasAuthority('PROPERTY_DECIDE')")
public class PropertyDecisionController {

  private final ExpedienteService expedienteService;

  public PropertyDecisionController(ExpedienteService expedienteService) {
    this.expedienteService = expedienteService;
  }

  @PostMapping("/accept-property")
  public ExpedienteResponse accept(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser currentUser = CurrentUser.from(jwt);
    return ExpedienteResponse.from(expedienteService.acceptProperty(id, currentUser.id()));
  }

  @PostMapping("/reject-property")
  public ExpedienteResponse reject(
      @PathVariable UUID id, @Valid @RequestBody RejectPropertyRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser currentUser = CurrentUser.from(jwt);
    return ExpedienteResponse.from(expedienteService.rejectProperty(id, currentUser.id(), request.reason()));
  }
}
