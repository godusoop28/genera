package com.c21genera.expedientes.web;

import com.c21genera.expedientes.application.ExpedienteService;
import com.c21genera.expedientes.application.ExpedienteService.CreateExpedienteCommand;
import com.c21genera.expedientes.application.ExpedienteService.CreateParticipant;
import com.c21genera.expedientes.domain.Expediente;
import com.c21genera.expedientes.web.ExpedienteDtos.CreateExpedienteRequest;
import com.c21genera.expedientes.web.ExpedienteDtos.ExpedienteResponse;
import com.c21genera.expedientes.web.ExpedienteDtos.ParticipantResponse;
import com.c21genera.expedientes.web.ExpedienteDtos.RequirementResponse;
import com.c21genera.identity.CurrentUser;
import com.c21genera.shared.web.PageResponse;
import com.c21genera.shared.web.Pagination;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/expedientes")
public class InternalExpedienteController {

  private final ExpedienteService expedienteService;

  public InternalExpedienteController(ExpedienteService expedienteService) {
    this.expedienteService = expedienteService;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('EXPEDIENT_CREATE')")
  @ResponseStatus(HttpStatus.CREATED)
  public ExpedienteResponse create(@Valid @RequestBody CreateExpedienteRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser currentUser = CurrentUser.from(jwt);
    Expediente expediente =
        expedienteService.create(
            new CreateExpedienteCommand(
                request.ownerDisplayName(),
                request.personType(),
                request.signerCharacter(),
                request.accreditationType(),
                request.condominiumRegime(),
                request.propertyCaseType(),
                request.declaredLegalStatus(),
                request.propertyAddress(),
                request.participants().stream().map(p -> new CreateParticipant(p.role(), p.fullName())).toList(),
                currentUser.id()));
    return ExpedienteResponse.from(expediente);
  }

  @GetMapping
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
  public PageResponse<ExpedienteResponse> list(
      @PageableDefault(size = 20) Pageable pageable, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser currentUser = CurrentUser.from(jwt);
    Pageable capped = Pagination.cap(pageable);
    var page =
        currentUser.hasPermission("EXPEDIENT_VIEW_ALL")
            ? expedienteService.listAll(capped)
            : expedienteService.listOwn(currentUser.id(), capped);
    return PageResponse.of(page.map(ExpedienteResponse::from));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
  public ExpedienteResponse get(@PathVariable UUID id) {
    return ExpedienteResponse.from(expedienteService.get(id));
  }

  @GetMapping("/{id}/participants")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
  public java.util.List<ParticipantResponse> participants(@PathVariable UUID id) {
    return expedienteService.participantsOf(id).stream().map(ParticipantResponse::from).toList();
  }

  @GetMapping("/{id}/requirements")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
  public java.util.List<RequirementResponse> requirements(@PathVariable UUID id) {
    return expedienteService.requirementsOf(id).stream().map(RequirementResponse::from).toList();
  }
}
