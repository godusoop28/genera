package com.c21genera.extraction.web;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.extraction.application.DocumentFieldExtractionService;
import com.c21genera.extraction.web.ExtractionDtos.ConfirmFieldRequest;
import com.c21genera.extraction.web.ExtractionDtos.ConflictResponse;
import com.c21genera.extraction.web.ExtractionDtos.ExpedienteObservationResponse;
import com.c21genera.extraction.web.ExtractionDtos.ObservationResponse;
import com.c21genera.extraction.web.ExtractionDtos.ResolveConflictRequest;
import com.c21genera.identity.CurrentUser;
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
  private final DocumentsApi documentsApi;
  private final ExpedienteAccessPolicy accessPolicy;

  public InternalExtractionController(
      DocumentFieldExtractionService service, DocumentsApi documentsApi, ExpedienteAccessPolicy accessPolicy) {
    this.service = service;
    this.documentsApi = documentsApi;
    this.accessPolicy = accessPolicy;
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
    requireAccess(service.getObservation(observationId).getExpedienteId(), jwt);
    return ObservationResponse.from(service.confirm(observationId, request.confirmedValue()));
  }

  /** Todo lo detectado en los documentos vigentes del expediente, para prellenar los datos del contrato. */
  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/extracted-fields")
  public List<ExpedienteObservationResponse> observationsOfExpediente(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    requireAccess(expedienteId, jwt);
    return service.observationsOfExpediente(expedienteId).stream().map(ExpedienteObservationResponse::from).toList();
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

  @PostMapping("/api/v1/internal/data-conflicts/{conflictId}/resolve")
  @PreAuthorize("hasAuthority('EXTRACTED_DATA_EDIT')")
  public ConflictResponse resolve(
      @PathVariable UUID conflictId, @Valid @RequestBody ResolveConflictRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(service.getConflict(conflictId).getExpedienteId(), jwt);
    return ConflictResponse.from(service.resolveConflict(conflictId, user.id(), request.note()));
  }

  private CurrentUser requireAccess(UUID expedienteId, Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    accessPolicy.requireAccess(expedienteId, user.id(), user.permissions());
    return user;
  }
}
