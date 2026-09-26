package com.c21genera.closing.web;

import com.c21genera.closing.application.ClosingService;
import com.c21genera.closing.web.ClosingDtos.AddNoteRequest;
import com.c21genera.closing.web.ClosingDtos.ChangeStatusRequest;
import com.c21genera.closing.web.ClosingDtos.ClosingCaseResponse;
import com.c21genera.closing.web.ClosingDtos.CompleteTaskRequest;
import com.c21genera.closing.web.ClosingDtos.NoteResponse;
import com.c21genera.closing.web.ClosingDtos.TaskResponse;
import com.c21genera.identity.CurrentUser;
import com.c21genera.shared.security.ExpedienteAccessPolicy;
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
  private final ExpedienteAccessPolicy accessPolicy;

  public InternalClosingController(ClosingService closingService, ExpedienteAccessPolicy accessPolicy) {
    this.closingService = closingService;
    this.accessPolicy = accessPolicy;
  }

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/closing")
  public ClosingCaseResponse get(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    requireAccess(expedienteId, jwt);
    return ClosingCaseResponse.from(closingService.get(expedienteId));
  }

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/closing/tasks")
  public List<TaskResponse> tasks(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    requireAccess(expedienteId, jwt);
    return closingService.tasksOf(expedienteId).stream().map(TaskResponse::from).toList();
  }

  @PostMapping("/api/v1/internal/expedientes/{expedienteId}/closing/tasks/{taskId}/complete")
  @PreAuthorize("hasAuthority('EXPEDIENT_EDIT')")
  public TaskResponse completeTask(
      @PathVariable UUID expedienteId,
      @PathVariable UUID taskId,
      @RequestBody(required = false) CompleteTaskRequest request,
      @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(expedienteId, jwt);
    return TaskResponse.from(closingService.completeTask(expedienteId, taskId, user.id(), request == null ? null : request.note()));
  }

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/closing/notes")
  public List<NoteResponse> notes(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    requireAccess(expedienteId, jwt);
    return closingService.notesOf(expedienteId).stream().map(NoteResponse::from).toList();
  }

  @PostMapping("/api/v1/internal/expedientes/{expedienteId}/closing/notes")
  @PreAuthorize("hasAuthority('EXPEDIENT_EDIT')")
  @ResponseStatus(HttpStatus.CREATED)
  public NoteResponse addNote(@PathVariable UUID expedienteId, @Valid @RequestBody AddNoteRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(expedienteId, jwt);
    return NoteResponse.from(closingService.addNote(expedienteId, user.id(), request.note()));
  }

  @PostMapping("/api/v1/internal/expedientes/{expedienteId}/closing/status")
  @PreAuthorize("hasAuthority('EXPEDIENT_EDIT')")
  public ClosingCaseResponse changeStatus(
      @PathVariable UUID expedienteId, @Valid @RequestBody ChangeStatusRequest request, @AuthenticationPrincipal Jwt jwt) {
    requireAccess(expedienteId, jwt);
    return ClosingCaseResponse.from(closingService.changeStatus(expedienteId, request.status()));
  }

  private CurrentUser requireAccess(UUID expedienteId, Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    accessPolicy.requireAccess(expedienteId, user.id(), user.permissions());
    return user;
  }
}
