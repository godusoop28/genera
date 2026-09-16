package com.c21genera.closing.web;

import com.c21genera.closing.application.ClosingService;
import com.c21genera.closing.web.ClosingDtos.AddNoteRequest;
import com.c21genera.closing.web.ClosingDtos.ChangeStatusRequest;
import com.c21genera.closing.web.ClosingDtos.ClosingCaseResponse;
import com.c21genera.closing.web.ClosingDtos.NoteResponse;
import com.c21genera.identity.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
public class InternalClosingController {

  private final ClosingService closingService;

  public InternalClosingController(ClosingService closingService) {
    this.closingService = closingService;
  }

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/closing")
  public ClosingCaseResponse get(@PathVariable UUID expedienteId) {
    return ClosingCaseResponse.from(closingService.get(expedienteId));
  }

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/closing/notes")
  public List<NoteResponse> notes(@PathVariable UUID expedienteId) {
    return closingService.notesOf(expedienteId).stream().map(NoteResponse::from).toList();
  }

  @PostMapping("/api/v1/internal/expedientes/{expedienteId}/closing/notes")
  @ResponseStatus(HttpStatus.CREATED)
  public NoteResponse addNote(@PathVariable UUID expedienteId, @Valid @RequestBody AddNoteRequest request, @AuthenticationPrincipal Jwt jwt) {
    return NoteResponse.from(closingService.addNote(expedienteId, CurrentUser.from(jwt).id(), request.note()));
  }

  @PostMapping("/api/v1/internal/expedientes/{expedienteId}/closing/status")
  public ClosingCaseResponse changeStatus(@PathVariable UUID expedienteId, @Valid @RequestBody ChangeStatusRequest request) {
    return ClosingCaseResponse.from(closingService.changeStatus(expedienteId, request.status()));
  }

  @PostMapping("/api/v1/internal/expedientes/{expedienteId}/closing/mark-contract-delivered")
  public ClosingCaseResponse markContractDelivered(@PathVariable UUID expedienteId) {
    return ClosingCaseResponse.from(closingService.markContractDelivered(expedienteId));
  }
}
