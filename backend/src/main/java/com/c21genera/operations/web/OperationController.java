package com.c21genera.operations.web;

import com.c21genera.identity.CurrentUser;
import com.c21genera.operations.application.ClosingMatrixService;
import com.c21genera.operations.application.ClosingMatrixService.MatrixView;
import com.c21genera.operations.application.ClosingMatrixService.TemplateInput;
import com.c21genera.operations.application.OperationService;
import com.c21genera.operations.application.OperationService.CreateCommand;
import com.c21genera.operations.application.OperationService.ListFilter;
import com.c21genera.operations.application.OperationService.OperationDetail;
import com.c21genera.operations.application.OperationService.OperationItem;
import com.c21genera.operations.application.OperationService.OperationList;
import com.c21genera.operations.application.OperationService.UpdateCommand;
import com.c21genera.operations.domain.ClosingRequirement;
import com.c21genera.operations.domain.ClosingRequirement.RequestedFrom;
import com.c21genera.shared.security.ExpedienteAccessPolicy;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Control de Cambios #32: operaciones de cierre (propias y externas) y su revisión documental. Las
 * reglas de permiso finas (registrar, revisar, autorizar, reabrir) las aplica OperationService.
 */
@RestController
@Validated
@RequestMapping("/api/v1/internal/operations")
@PreAuthorize("hasAnyAuthority('OPERATION_VIEW_ALL', 'OPERATION_MANAGE', 'OPERATION_REVIEW', 'OPERATION_MATRIX_MANAGE')")
public class OperationController {

  private final OperationService operations;
  private final ClosingMatrixService matrix;
  private final ExpedienteAccessPolicy accessPolicy;

  public OperationController(OperationService operations, ClosingMatrixService matrix, ExpedienteAccessPolicy accessPolicy) {
    this.operations = operations;
    this.matrix = matrix;
    this.accessPolicy = accessPolicy;
  }

  public record CloseRequest(LocalDate closedOn, BigDecimal amount, @Size(max = 3) String currency, LocalDate docDueDate, @Size(max = 160) String docFollowUpOwner) {}

  public record ReasonRequest(@Size(max = 2000) String reason) {}

  public record FollowUpRequest(LocalDate dueDate, @Size(max = 160) String owner) {}

  public record RequirementFollowUpRequest(RequestedFrom requestedFrom, @Size(max = 160) String owner, LocalDate dueDate) {}

  public record ReviewRequest(@NotNull ClosingRequirement.Status status, @Size(max = 2000) String observation) {}

  public record NotApplicableRequest(@Size(max = 2000) String justification) {}

  public record DecideRequest(boolean approve, @Size(max = 2000) String observation) {}

  public record LinkRequest(@NotNull UUID documentId, LocalDate documentDate, @Size(max = 2000) String note) {}

  public record NoteRequest(@Size(max = 4000) String note, LocalDate documentDate) {}

  public record ApproveRequest(boolean approved) {}

  public record UrlResponse(URI url) {}

  @GetMapping
  public OperationList list(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String docStatus,
      @RequestParam(required = false) String origin,
      @RequestParam(required = false) UUID advisorId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate closedFrom,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate closedTo,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Boolean pendingDocs,
      @AuthenticationPrincipal Jwt jwt) {
    return operations.list(new ListFilter(status, docStatus, origin, advisorId, closedFrom, closedTo, q, pendingDocs), CurrentUser.from(jwt));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public OperationDetail create(@RequestBody CreateCommand command, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    if (command.expedienteId() != null) {
      accessPolicy.requireAccess(command.expedienteId(), user.id(), user.permissions());
    }
    return operations.create(command, user);
  }

  @GetMapping("/by-expediente/{expedienteId}")
  public List<OperationItem> ofExpediente(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    accessPolicy.requireAccess(expedienteId, user.id(), user.permissions());
    return operations.ofExpediente(expedienteId, user);
  }

  @GetMapping("/{id}")
  public OperationDetail detail(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return operations.detail(id, CurrentUser.from(jwt));
  }

  @PutMapping("/{id}")
  public OperationDetail update(@PathVariable UUID id, @RequestBody UpdateCommand command, @AuthenticationPrincipal Jwt jwt) {
    return operations.update(id, command, CurrentUser.from(jwt));
  }

  @PostMapping("/{id}/closing-review")
  public OperationDetail toReview(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return operations.moveToClosingReview(id, CurrentUser.from(jwt));
  }

  @PostMapping("/{id}/close")
  public OperationDetail close(@PathVariable UUID id, @RequestBody CloseRequest r, @AuthenticationPrincipal Jwt jwt) {
    return operations.close(id, r.closedOn(), r.amount(), r.currency(), r.docDueDate(), r.docFollowUpOwner(), CurrentUser.from(jwt));
  }

  @PostMapping("/{id}/cancel")
  public OperationDetail cancel(@PathVariable UUID id, @RequestBody ReasonRequest r, @AuthenticationPrincipal Jwt jwt) {
    return operations.cancel(id, r.reason(), CurrentUser.from(jwt));
  }

  @PostMapping("/{id}/reopen")
  public OperationDetail reopen(@PathVariable UUID id, @RequestBody ReasonRequest r, @AuthenticationPrincipal Jwt jwt) {
    return operations.reopen(id, r.reason(), CurrentUser.from(jwt));
  }

  @PostMapping("/{id}/follow-up")
  public OperationDetail followUp(@PathVariable UUID id, @RequestBody FollowUpRequest r, @AuthenticationPrincipal Jwt jwt) {
    return operations.followUp(id, r.dueDate(), r.owner(), CurrentUser.from(jwt));
  }

  @PostMapping("/{id}/apply-matrix")
  public OperationDetail applyMatrix(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return operations.applyCurrentMatrix(id, CurrentUser.from(jwt));
  }

  @PostMapping("/{id}/authorize")
  public OperationDetail authorize(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return operations.authorize(id, CurrentUser.from(jwt));
  }

  @PostMapping("/{id}/reopen-review")
  public OperationDetail reopenReview(@PathVariable UUID id, @RequestBody ReasonRequest r, @AuthenticationPrincipal Jwt jwt) {
    return operations.reopenDocReview(id, r.reason(), CurrentUser.from(jwt));
  }

  @PostMapping("/{id}/requirements/{requirementId}/evidence/file")
  public OperationDetail attach(
      @PathVariable UUID id,
      @PathVariable UUID requirementId,
      @RequestParam("file") MultipartFile file,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate documentDate,
      @RequestParam(required = false) @Size(max = 2000) String note,
      @AuthenticationPrincipal Jwt jwt) {
    return operations.attachFile(id, requirementId, file, documentDate, note, CurrentUser.from(jwt));
  }

  @PostMapping("/{id}/requirements/{requirementId}/evidence/document")
  public OperationDetail link(@PathVariable UUID id, @PathVariable UUID requirementId, @RequestBody LinkRequest r, @AuthenticationPrincipal Jwt jwt) {
    return operations.linkDocument(id, requirementId, r.documentId(), r.documentDate(), r.note(), CurrentUser.from(jwt));
  }

  @PostMapping("/{id}/requirements/{requirementId}/evidence/note")
  public OperationDetail note(@PathVariable UUID id, @PathVariable UUID requirementId, @RequestBody NoteRequest r, @AuthenticationPrincipal Jwt jwt) {
    return operations.addNoteEvidence(id, requirementId, r.note(), r.documentDate(), CurrentUser.from(jwt));
  }

  @PostMapping("/{id}/requirements/{requirementId}/follow-up")
  public OperationDetail requirementFollowUp(@PathVariable UUID id, @PathVariable UUID requirementId, @RequestBody RequirementFollowUpRequest r, @AuthenticationPrincipal Jwt jwt) {
    return operations.requirementFollowUp(id, requirementId, r.requestedFrom(), r.owner(), r.dueDate(), CurrentUser.from(jwt));
  }

  @PostMapping("/{id}/requirements/{requirementId}/review")
  public OperationDetail review(@PathVariable UUID id, @PathVariable UUID requirementId, @RequestBody ReviewRequest r, @AuthenticationPrincipal Jwt jwt) {
    return operations.review(id, requirementId, r.status(), r.observation(), CurrentUser.from(jwt));
  }

  @PostMapping("/{id}/requirements/{requirementId}/not-applicable")
  public OperationDetail notApplicable(@PathVariable UUID id, @PathVariable UUID requirementId, @RequestBody NotApplicableRequest r, @AuthenticationPrincipal Jwt jwt) {
    return operations.requestNotApplicable(id, requirementId, r.justification(), CurrentUser.from(jwt));
  }

  @PostMapping("/{id}/requirements/{requirementId}/not-applicable/decision")
  public OperationDetail decide(@PathVariable UUID id, @PathVariable UUID requirementId, @RequestBody DecideRequest r, @AuthenticationPrincipal Jwt jwt) {
    return operations.decideNotApplicable(id, requirementId, r.approve(), r.observation(), CurrentUser.from(jwt));
  }

  @GetMapping("/{id}/evidence/{evidenceId}/download")
  public UrlResponse download(@PathVariable UUID id, @PathVariable UUID evidenceId, @AuthenticationPrincipal Jwt jwt) {
    return new UrlResponse(operations.evidenceUrl(id, evidenceId, CurrentUser.from(jwt)));
  }

  // --- Matriz documental ------------------------------------------------------

  @GetMapping("/matrix")
  public MatrixView matrix() {
    return matrix.view();
  }

  @PostMapping("/matrix/items")
  public MatrixView addItem(@RequestBody TemplateInput input, @AuthenticationPrincipal Jwt jwt) {
    return matrix.save(null, input, CurrentUser.from(jwt));
  }

  @PutMapping("/matrix/items/{itemId}")
  public MatrixView updateItem(@PathVariable UUID itemId, @RequestBody TemplateInput input, @AuthenticationPrincipal Jwt jwt) {
    return matrix.save(itemId, input, CurrentUser.from(jwt));
  }

  @PostMapping("/matrix/approval")
  public MatrixView approve(@RequestBody ApproveRequest r, @AuthenticationPrincipal Jwt jwt) {
    return matrix.approve(r.approved(), CurrentUser.from(jwt));
  }
}
