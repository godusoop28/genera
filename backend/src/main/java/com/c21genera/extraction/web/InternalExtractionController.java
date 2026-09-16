package com.c21genera.extraction.web;

import com.c21genera.extraction.application.DocumentFieldExtractionService;
import com.c21genera.extraction.web.ExtractionDtos.ConfirmFieldRequest;
import com.c21genera.extraction.web.ExtractionDtos.ConflictResponse;
import com.c21genera.extraction.web.ExtractionDtos.ObservationResponse;
import com.c21genera.extraction.web.ExtractionDtos.ResolveConflictRequest;
import com.c21genera.identity.CurrentUser;
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

  public InternalExtractionController(DocumentFieldExtractionService service) {
    this.service = service;
  }

  @GetMapping("/api/v1/internal/documents/{documentId}/extracted-fields")
  public List<ObservationResponse> observationsOf(@PathVariable UUID documentId) {
    return service.observationsOfDocument(documentId).stream().map(ObservationResponse::from).toList();
  }

  @PostMapping("/api/v1/internal/extracted-fields/{observationId}/confirm")
  @PreAuthorize("hasAuthority('EXTRACTED_DATA_EDIT')")
  public ObservationResponse confirm(@PathVariable UUID observationId, @Valid @RequestBody ConfirmFieldRequest request) {
    return ObservationResponse.from(service.confirm(observationId, request.confirmedValue()));
  }

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/data-conflicts")
  public List<ConflictResponse> conflictsOf(@PathVariable UUID expedienteId) {
    return service.conflictsOfExpediente(expedienteId).stream().map(ConflictResponse::from).toList();
  }

  @PostMapping("/api/v1/internal/data-conflicts/{conflictId}/resolve")
  @PreAuthorize("hasAuthority('EXTRACTED_DATA_EDIT')")
  public ConflictResponse resolve(
      @PathVariable UUID conflictId, @RequestBody ResolveConflictRequest request, @AuthenticationPrincipal Jwt jwt) {
    return ConflictResponse.from(service.resolveConflict(conflictId, CurrentUser.from(jwt).id(), request.note()));
  }
}
