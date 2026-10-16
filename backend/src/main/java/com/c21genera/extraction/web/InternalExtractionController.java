package com.c21genera.extraction.web;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.extraction.application.DocumentFieldExtractionService;
import com.c21genera.extraction.application.ExpedienteAutoFill;
import com.c21genera.extraction.web.ExtractionDtos.ConfirmFieldRequest;
import com.c21genera.extraction.web.ExtractionDtos.ConflictResponse;
import com.c21genera.extraction.web.ExtractionDtos.ExpedienteObservationResponse;
import com.c21genera.extraction.web.ExtractionDtos.ObservationResponse;
import com.c21genera.extraction.web.ExtractionDtos.ResolveConflictRequest;
import com.c21genera.identity.CurrentUser;
import com.c21genera.identity.UserDirectory;
import com.c21genera.shared.domain.DocumentTypeCode;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.security.ExpedienteAccessPolicy;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
public class InternalExtractionController {

  private final DocumentFieldExtractionService service;
  private final ExpedienteAutoFill autoFill;
  private final DocumentsApi documentsApi;
  private final ExpedienteAccessPolicy accessPolicy;
  private final UserDirectory userDirectory;

  public InternalExtractionController(
      DocumentFieldExtractionService service,
      ExpedienteAutoFill autoFill,
      DocumentsApi documentsApi,
      ExpedienteAccessPolicy accessPolicy,
      UserDirectory userDirectory) {
    this.userDirectory = userDirectory;
    this.service = service;
    this.autoFill = autoFill;
    this.documentsApi = documentsApi;
    this.accessPolicy = accessPolicy;
  }

  public record AutoFillResponse(int filled) {}

  /**
   * Control de Cambios #7: lleva al expediente (contrato y participantes) lo ya validado en sus
   * documentos, sin pisar nada capturado. La pantalla del contrato lo pide al abrirse.
   */
  @PostMapping("/api/v1/internal/expedientes/{expedienteId}/autofill")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_EDIT', 'CONTRACT_PREPARE', 'EXTRACTED_DATA_EDIT')")
  public AutoFillResponse autofill(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    requireAccess(expedienteId, jwt);
    return new AutoFillResponse(autoFill.fill(expedienteId));
  }

  /** Una persona confirmó o corrigió un dato: ya está validado. Nunca impide guardar la confirmación. */
  private void fillAfterReview(UUID expedienteId) {
    try {
      autoFill.fill(expedienteId);
    } catch (RuntimeException ignored) {
      // Se reintenta al abrir la pantalla del contrato.
    }
  }

  @GetMapping("/api/v1/internal/documents/{documentId}/extracted-fields")
  public List<ObservationResponse> observationsOf(@PathVariable UUID documentId, @AuthenticationPrincipal Jwt jwt) {
    UUID expedienteId = documentsApi.expedienteIdOfDocument(documentId).orElseThrow(() -> new NotFoundException("Documento", documentId));
    requireAccess(expedienteId, jwt);
    return service.observationsOfDocument(documentId).stream().map(ObservationResponse::from).toList();
  }

  @PostMapping("/api/v1/internal/extracted-fields/{observationId}/confirm")
  @PreAuthorize("hasAuthority('EXTRACTED_DATA_EDIT')")
  public ObservationResponse confirm(
      @PathVariable UUID observationId, @Valid @RequestBody ConfirmFieldRequest request, @AuthenticationPrincipal Jwt jwt) {
    UUID expedienteId = service.getObservation(observationId).getExpedienteId();
    CurrentUser user = requireAccess(expedienteId, jwt);
    ObservationResponse response = ObservationResponse.from(service.confirm(observationId, request.confirmedValue(), user.id(), user.name()));
    fillAfterReview(expedienteId);
    return response;
  }

  /** Todo lo detectado en los documentos vigentes del expediente, para prellenar los datos del contrato. */
  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/extracted-fields")
  public List<ExpedienteObservationResponse> observationsOfExpediente(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    requireAccess(expedienteId, jwt);
    return service.observationsOfExpediente(expedienteId).stream().map(ExpedienteObservationResponse::from).toList();
  }

  public record AreaComparisonResponse(
      String key, String field, String label, String condominiumRegime, String deed, String propertyTax, boolean consistent) {}

  /** Control de Cambios #15: cotejo de m² de terreno e indivisos (Régimen de condominio, Escritura y Predial). */
  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/condominium-areas")
  public List<AreaComparisonResponse> condominiumAreas(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    requireAccess(expedienteId, jwt);
    return service.condominiumAreas(expedienteId).stream()
        .map(
            r ->
                new AreaComparisonResponse(
                    r.key(),
                    r.field(),
                    r.label(),
                    r.values().get(DocumentTypeCode.CONDOMINIUM_REGIME),
                    r.values().get(DocumentTypeCode.DEED),
                    r.values().get(DocumentTypeCode.PROPERTY_TAX),
                    r.consistent()))
        .toList();
  }

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/data-conflicts")
  public List<ConflictResponse> conflictsOf(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    requireAccess(expedienteId, jwt);
    return service.conflictsOfExpediente(expedienteId).stream().map(ConflictResponse::from).toList();
  }

  /** Vuelve a comparar todos los documentos del expediente entre sí y contra los datos capturados. */
  @PostMapping("/api/v1/internal/expedientes/{expedienteId}/consistency-check")
  public List<ConflictResponse> runConsistencyCheck(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    requireAccess(expedienteId, jwt);
    return service.runConsistencyCheck(expedienteId).stream().map(ConflictResponse::from).toList();
  }

  /**
   * Control de Cambios #17: autorizar una inconsistencia es decisión de un directivo o administrador
   * (INCONSISTENCY_AUTHORIZE) y se confirma con su contraseña.
   */
  @PostMapping("/api/v1/internal/data-conflicts/{conflictId}/resolve")
  @PreAuthorize("hasAuthority('INCONSISTENCY_AUTHORIZE')")
  public ConflictResponse resolve(
      @PathVariable UUID conflictId, @Valid @RequestBody ResolveConflictRequest request, @AuthenticationPrincipal Jwt jwt) {
    UUID expedienteId = service.getConflict(conflictId).getExpedienteId();
    CurrentUser user = requireAccess(expedienteId, jwt);
    if (!userDirectory.passwordMatches(user.id(), request.password())) {
      throw new UnprocessableException("INVALID_PASSWORD", "La contraseña no es correcta. Escribe tu contraseña para autorizar.");
    }
    ConflictResponse response = ConflictResponse.from(service.authorizeConflict(conflictId, user.toActor(), request.note()));
    fillAfterReview(expedienteId);
    return response;
  }

  private CurrentUser requireAccess(UUID expedienteId, Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    accessPolicy.requireAccess(expedienteId, user.id(), user.permissions());
    return user;
  }
}
