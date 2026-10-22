package com.c21genera.expedientes.web;

import com.c21genera.expedientes.LegalDetails;
import com.c21genera.expedientes.OperationType;
import com.c21genera.expedientes.application.AssignableAdvisors;
import com.c21genera.expedientes.application.ExpedienteService;
import com.c21genera.expedientes.application.ExpedienteService.ConfigurationCorrection;
import com.c21genera.expedientes.application.ExpedienteService.CreateExpedienteCommand;
import com.c21genera.expedientes.application.PropertyKeyService;
import com.c21genera.expedientes.domain.Expediente;
import com.c21genera.expedientes.web.ExpedienteDtos.AdvisorOptionResponse;
import com.c21genera.expedientes.web.ExpedienteDtos.AssignAdvisorRequest;
import com.c21genera.expedientes.web.ExpedienteDtos.ChangeResponse;
import com.c21genera.expedientes.web.ExpedienteDtos.CorrectConfigurationRequest;
import com.c21genera.expedientes.web.ExpedienteDtos.CreateExpedienteRequest;
import com.c21genera.expedientes.web.ExpedienteDtos.DuplicateCheckRequest;
import com.c21genera.expedientes.web.ExpedienteDtos.DuplicateMatchResponse;
import com.c21genera.expedientes.web.ExpedienteDtos.ExpedienteResponse;
import com.c21genera.expedientes.web.ExpedienteDtos.LegalDetailsRequest;
import com.c21genera.expedientes.web.ExpedienteDtos.ParticipantChangeRequest;
import com.c21genera.expedientes.web.ExpedienteDtos.ParticipantResponse;
import com.c21genera.expedientes.web.ExpedienteDtos.RequirementResponse;
import com.c21genera.identity.CurrentUser;
import com.c21genera.identity.UserDirectory;
import com.c21genera.shared.security.ExpedienteAccessPolicy;
import com.c21genera.shared.web.PageResponse;
import com.c21genera.shared.web.Pagination;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
  private final UserDirectory userDirectory;
  private final AssignableAdvisors assignableAdvisors;
  private final PropertyKeyService propertyKeys;

  public InternalExpedienteController(
      ExpedienteService expedienteService,
      ExpedienteAccessPolicy accessPolicy,
      UserDirectory userDirectory,
      AssignableAdvisors assignableAdvisors,
      PropertyKeyService propertyKeys) {
    this.expedienteService = expedienteService;
    this.accessPolicy = accessPolicy;
    this.userDirectory = userDirectory;
    this.assignableAdvisors = assignableAdvisors;
    this.propertyKeys = propertyKeys;
  }

  @PostMapping
  @PreAuthorize("hasAuthority('EXPEDIENT_CREATE')")
  @ResponseStatus(HttpStatus.CREATED)
  public ExpedienteResponse create(@Valid @RequestBody CreateExpedienteRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser currentUser = CurrentUser.from(jwt);
    UserDirectory.StaffMember advisor = assignableAdvisors.require(request.assignedAdvisorId());
    Expediente expediente =
        expedienteService.create(
            new CreateExpedienteCommand(
                request.personType(),
                request.signedByAttorney(),
                request.accreditationType(),
                request.condominiumRegime(),
                request.propertySubtypeId(),
                request.propertyTypeDetail(),
                request.operationType(),
                request.declaredLegalStatus(),
                request.propertyAddress(),
                request.propertyReference(),
                advisor.id(),
                advisor.name(),
                request.participants().stream().map(ExpedienteDtos.ParticipantRequest::toInput).toList(),
                request.legalDetails(),
                currentUser.toActor(),
                request.duplicateAuthorizationReason(),
                currentUser.hasPermission("EXPEDIENT_DUPLICATE_AUTHORIZE")));
    return ExpedienteResponse.from(expediente);
  }

  /** Antes de crear: ¿alguno de estos titulares ya tiene un expediente en curso? */
  @PostMapping("/duplicate-check")
  @PreAuthorize("hasAuthority('EXPEDIENT_CREATE')")
  public List<DuplicateMatchResponse> duplicateCheck(@Valid @RequestBody DuplicateCheckRequest request) {
    return expedienteService.findClientDuplicates(request.holderNames()).stream()
        .map(
            m -> {
              Expediente e = m.expediente();
              String property = e.getPropertyAddress() != null ? e.getPropertyAddress() : e.getPropertyReference();
              String advisor =
                  e.getAssignedAdvisorName() != null
                      ? e.getAssignedAdvisorName()
                      : userDirectory.contactOf(e.getCreatedByUserId()).map(UserDirectory.UserContact::name).orElse(null);
              return new DuplicateMatchResponse(
                  e.getId(), e.getFolio(), e.getOwnerDisplayName(), m.matchedName(), e.getStatus(), property, advisor, e.getCreatedAt());
            })
        .toList();
  }

  /** Control de Cambios #24: asesores activos para el campo "Asesor asignado" y los filtros. */
  @GetMapping("/advisors")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_CREATE', 'EXPEDIENT_VIEW_ALL', 'EXPEDIENT_ADVISOR_ASSIGN')")
  public List<AdvisorOptionResponse> advisors() {
    return assignableAdvisors.list().stream().map(m -> new AdvisorOptionResponse(m.id(), m.name(), m.roleCode())).toList();
  }

  /** Control de Cambios #24: cambiar el asesor asignado; queda en el historial del expediente. */
  @PutMapping("/{id}/advisor")
  @PreAuthorize("hasAuthority('EXPEDIENT_ADVISOR_ASSIGN')")
  public ExpedienteResponse assignAdvisor(
      @PathVariable UUID id, @Valid @RequestBody AssignAdvisorRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(id, jwt);
    UserDirectory.StaffMember advisor = assignableAdvisors.require(request.advisorId());
    return ExpedienteResponse.from(expedienteService.reassignAdvisor(id, advisor.id(), advisor.name(), user.toActor(), request.reason()));
  }

  /**
   * Control de Cambios #27 (adenda): claves asignadas (filtrables por tipo, operación y asesor), los
   * consecutivos en uso y disponibles de cada combinación, y los expedientes que aún no tienen clave
   * con la que les tocaría. prefix: consultar una combinación, p. ej. "CVEA".
   */
  @GetMapping("/property-keys")
  @PreAuthorize("hasAuthority('EXPEDIENT_KEY_ASSIGN')")
  public PropertyKeyService.KeyOverview propertyKeys(
      @RequestParam(required = false) UUID categoryId,
      @RequestParam(required = false) UUID subtypeId,
      @RequestParam(defaultValue = "false") boolean unclassified,
      @RequestParam(required = false) OperationType operationType,
      @RequestParam(required = false) UUID advisorId,
      @RequestParam(required = false) String prefix) {
    return propertyKeys.overview(new PropertyKeyService.KeyFilter(categoryId, subtypeId, unclassified, operationType, advisorId, prefix));
  }

  /** Validación en tiempo real de una clave capturada a mano: ¿tiene el formato y está libre? */
  @GetMapping("/property-keys/check")
  @PreAuthorize("hasAuthority('EXPEDIENT_KEY_ASSIGN')")
  public PropertyKeyService.KeyCheck checkPropertyKey(@RequestParam String key, @RequestParam(required = false) UUID expedienteId) {
    return propertyKeys.check(key, expedienteId);
  }

  /** Asignación manual de la clave (migración de propiedades existentes o corrección); queda en el historial. */
  @PutMapping("/{id}/property-key")
  @PreAuthorize("hasAuthority('EXPEDIENT_KEY_ASSIGN')")
  public ExpedienteResponse assignPropertyKey(
      @PathVariable UUID id, @Valid @RequestBody ExpedienteDtos.AssignPropertyKeyRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(id, jwt);
    return ExpedienteResponse.from(propertyKeys.assign(id, request.key(), user.toActor(), request.reason()));
  }

  /** Saca el expediente del listado de trabajo (no cambia su estatus ni borra nada); queda en el historial. */
  @PostMapping("/{id}/archive")
  @PreAuthorize("hasAuthority('EXPEDIENT_ARCHIVE')")
  public ExpedienteResponse archive(
      @PathVariable UUID id, @Valid @RequestBody(required = false) ExpedienteDtos.ArchiveRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(id, jwt);
    return ExpedienteResponse.from(expedienteService.archive(id, user.toActor(), request == null ? null : request.reason()));
  }

  @PostMapping("/{id}/unarchive")
  @PreAuthorize("hasAuthority('EXPEDIENT_ARCHIVE')")
  public ExpedienteResponse unarchive(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(id, jwt);
    return ExpedienteResponse.from(expedienteService.unarchive(id, user.toActor()));
  }

  @GetMapping
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
  public PageResponse<ExpedienteResponse> list(
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
      @AuthenticationPrincipal Jwt jwt) {
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
                request.propertySubtypeId(),
                request.propertyTypeDetail(),
                request.operationType(),
                request.declaredLegalStatus(),
                request.propertyAddress()),
            user.toActor(),
            request.reason()));
  }

  /**
   * Control de Cambios #26: clasificar el tipo de inmueble (p. ej. un terreno anterior al catálogo que
   * quedó pendiente) sin la corrección completa, siempre que no cambie documentos ni contrato.
   */
  @PutMapping("/{id}/property-type")
  @PreAuthorize("hasAuthority('EXPEDIENT_EDIT')")
  public ExpedienteResponse classifyProperty(
      @PathVariable UUID id, @Valid @RequestBody ExpedienteDtos.ClassifyPropertyRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(id, jwt);
    return ExpedienteResponse.from(
        expedienteService.classifyProperty(id, request.propertySubtypeId(), request.propertyTypeDetail(), user.toActor(), request.reason()));
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
