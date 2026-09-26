package com.c21genera.expedientes.web;

import com.c21genera.expedientes.LegalDetails;
import com.c21genera.expedientes.application.ExpedienteService;
import com.c21genera.expedientes.application.ExpedienteService.ConfigurationCorrection;
import com.c21genera.expedientes.application.ExpedienteService.CreateExpedienteCommand;
import com.c21genera.expedientes.domain.Expediente;
import com.c21genera.expedientes.web.ExpedienteDtos.ChangeResponse;
import com.c21genera.expedientes.web.ExpedienteDtos.CorrectConfigurationRequest;
import com.c21genera.expedientes.web.ExpedienteDtos.CreateExpedienteRequest;
import com.c21genera.expedientes.web.ExpedienteDtos.ExpedienteResponse;
import com.c21genera.expedientes.web.ExpedienteDtos.LegalDetailsRequest;
import com.c21genera.expedientes.web.ExpedienteDtos.ParticipantChangeRequest;
import com.c21genera.expedientes.web.ExpedienteDtos.ParticipantResponse;
import com.c21genera.expedientes.web.ExpedienteDtos.RequirementResponse;
import com.c21genera.identity.CurrentUser;
import com.c21genera.shared.security.ExpedienteAccessPolicy;
import com.c21genera.shared.web.PageResponse;
import com.c21genera.shared.web.Pagination;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/internal/expedientes")
public class InternalExpedienteController {

  private final ExpedienteService expedienteService;
  private final ExpedienteAccessPolicy accessPolicy;

  public InternalExpedienteController(ExpedienteService expedienteService, ExpedienteAccessPolicy accessPolicy) {
    this.expedienteService = expedienteService;
    this.accessPolicy = accessPolicy;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('EXPEDIENT_CREATE')")
  @ResponseStatus(HttpStatus.CREATED)
  public ExpedienteResponse create(@Valid @RequestBody CreateExpedienteRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser currentUser = CurrentUser.from(jwt);
    Expediente expediente =
        expedienteService.create(
            new CreateExpedienteCommand(
                request.personType(),
                request.signedByAttorney(),
                request.accreditationType(),
                request.condominiumRegime(),
                request.propertyCaseType(),
                request.declaredLegalStatus(),
                request.propertyAddress(),
                request.participants().stream().map(ExpedienteDtos.ParticipantRequest::toInput).toList(),
                request.legalDetails(),
                currentUser.toActor()));
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
  public ExpedienteResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    requireAccess(id, jwt);
    return ExpedienteResponse.from(expedienteService.get(id));
  }

  /** Corrección controlada de los datos principales; queda en el historial y genera nueva versión de contrato. */
  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('EXPEDIENT_EDIT')")
  public ExpedienteResponse correct(
      @PathVariable UUID id, @Valid @RequestBody CorrectConfigurationRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(id, jwt);
    return ExpedienteResponse.from(
        expedienteService.correctConfiguration(
            id,
            new ConfigurationCorrection(
                request.personType(),
                request.signedByAttorney(),
                request.accreditationType(),
                request.condominiumRegime(),
                request.propertyCaseType(),
                request.declaredLegalStatus(),
                request.propertyAddress()),
            user.toActor(),
            request.reason()));
  }

  @GetMapping("/{id}/participants")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
  public List<ParticipantResponse> participants(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    requireAccess(id, jwt);
    return expedienteService.participantsOf(id).stream().map(ParticipantResponse::from).toList();
  }

  @PostMapping("/{id}/participants")
  @PreAuthorize("hasAuthority('EXPEDIENT_EDIT')")
  @ResponseStatus(HttpStatus.CREATED)
  public ParticipantResponse addParticipant(
      @PathVariable UUID id, @Valid @RequestBody ParticipantChangeRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(id, jwt);
    return ParticipantResponse.from(
        expedienteService.addParticipant(id, request.participant().toInput(), user.toActor(), request.reason()));
  }

  @PutMapping("/{id}/participants/{participantId}")
  @PreAuthorize("hasAuthority('EXPEDIENT_EDIT')")
  public ParticipantResponse updateParticipant(
      @PathVariable UUID id,
      @PathVariable UUID participantId,
      @Valid @RequestBody ParticipantChangeRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(id, jwt);
    return ParticipantResponse.from(
        expedienteService.updateParticipant(id, participantId, request.participant().toInput(), user.toActor(), request.reason()));
  }

  @DeleteMapping("/{id}/participants/{participantId}")
  @PreAuthorize("hasAuthority('EXPEDIENT_EDIT')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeParticipant(
      @PathVariable UUID id,
      @PathVariable UUID participantId,
      @RequestParam(required = false) String reason,
      @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(id, jwt);
    expedienteService.removeParticipant(id, participantId, user.toActor(), reason);
  }

  @GetMapping("/{id}/legal-details")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
  public LegalDetails legalDetails(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    requireAccess(id, jwt);
    return expedienteService.legalDetailsOf(id);
  }

  @PutMapping("/{id}/legal-details")
  @PreAuthorize("hasAuthority('EXPEDIENT_EDIT')")
  public LegalDetails updateLegalDetails(
      @PathVariable UUID id, @Valid @RequestBody LegalDetailsRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(id, jwt);
    return expedienteService.updateLegalDetails(id, request.legalDetails(), user.toActor(), request.reason());
  }

  @GetMapping("/{id}/requirements")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
  public List<RequirementResponse> requirements(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    requireAccess(id, jwt);
    return expedienteService.requirementsOf(id).stream().map(RequirementResponse::from).toList();
  }

  /** Historial de correcciones: quién cambió qué, cuándo, antes/después y por qué. */
  @GetMapping("/{id}/changes")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
  public List<ChangeResponse> changes(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    requireAccess(id, jwt);
    return expedienteService.changesOf(id).stream().map(ChangeResponse::from).toList();
  }

  private CurrentUser requireAccess(UUID expedienteId, Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    accessPolicy.requireAccess(expedienteId, user.id(), user.permissions());
    return user;
  }
}
