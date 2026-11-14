package com.c21genera.resolvedproperty.web;

import com.c21genera.identity.CurrentUser;
import com.c21genera.resolvedproperty.application.ResolvedPropertyService;
import com.c21genera.resolvedproperty.application.ResolvedPropertyService.Access;
import com.c21genera.resolvedproperty.application.ResolvedPropertyService.FileContent;
import com.c21genera.resolvedproperty.application.ResolvedPropertyService.ProjectInput;
import com.c21genera.resolvedproperty.application.ResolvedPropertyService.ProjectSummary;
import com.c21genera.resolvedproperty.domain.ResolvedAccessGrant;
import com.c21genera.resolvedproperty.domain.ResolvedActivity;
import com.c21genera.resolvedproperty.domain.ResolvedDocument;
import com.c21genera.resolvedproperty.domain.ResolvedProject;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

/**
 * Control de Cambios #42: apartado «Propiedad Resuelta». Las acciones de dirección (crear,
 * aprobar, incorporar, dar y quitar accesos) piden RESOLVED_PROPERTY_ADMIN; consultar y abrir
 * archivos se valida por proyecto en el servicio (acceso expreso vigente).
 */
@RestController
@RequestMapping("/api/v1/internal/propiedad-resuelta")
public class ResolvedPropertyController {

  private static final String ADMIN = "hasAuthority('RESOLVED_PROPERTY_ADMIN')";

  private final ResolvedPropertyService service;

  public ResolvedPropertyController(ResolvedPropertyService service) {
    this.service = service;
  }

  public record AccessView(boolean admin, boolean view, boolean download, boolean upload) {
    static AccessView of(Access a) {
      return new AccessView(a.admin(), a.view(), a.download(), a.upload());
    }
  }

  public record ProjectView(
      UUID id,
      String name,
      String description,
      String location,
      BigDecimal estimatedInvestment,
      BigDecimal estimatedValue,
      String approvalStatus,
      String approvedByName,
      Instant approvedAt,
      String approvalNote,
      String commercializationStatus,
      String incorporatedByName,
      Instant incorporatedAt,
      String incorporationNote,
      String linkedReference,
      String createdByName,
      Instant createdAt,
      Instant updatedAt,
      int documentCount,
      AccessView access) {

    static ProjectView of(ResolvedProject p, int documentCount, Access access) {
      return new ProjectView(
          p.getId(), p.getName(), p.getDescription(), p.getLocation(), p.getEstimatedInvestment(), p.getEstimatedValue(), p.getApprovalStatus(),
          p.getApprovedByName(), p.getApprovedAt(), p.getApprovalNote(), p.getCommercializationStatus(), p.getIncorporatedByName(), p.getIncorporatedAt(),
          p.getIncorporationNote(), p.getLinkedReference(), p.getCreatedByName(), p.getCreatedAt(), p.getUpdatedAt(), documentCount, AccessView.of(access));
    }
  }

  /** Métricas de Propiedad Resuelta, separadas de las de CENTURY 21 (solo de los proyectos visibles). */
  public record Metrics(
      int projects, int pendingApproval, int approved, int incorporated, int documents, BigDecimal estimatedInvestment, BigDecimal estimatedValue) {}

  public record ProjectsResponse(boolean admin, Metrics metrics, List<ProjectView> projects) {}

  public record DocumentView(UUID id, String name, String category, String contentType, long sizeBytes, String originalFilename, String uploadedByName, Instant uploadedAt) {
    static DocumentView of(ResolvedDocument d) {
      return new DocumentView(d.getId(), d.getName(), d.getCategory(), d.getContentType(), d.getSizeBytes(), d.getOriginalFilename(), d.getUploadedByName(), d.getUploadedAt());
    }
  }

  public record GrantView(UUID id, UUID userId, String userName, boolean canDownload, boolean canUpload, String grantedByName, Instant grantedAt, String revokedByName, Instant revokedAt) {
    static GrantView of(ResolvedAccessGrant g) {
      return new GrantView(g.getId(), g.getUserId(), g.getUserName(), g.isCanDownload(), g.isCanUpload(), g.getGrantedByName(), g.getGrantedAt(), g.getRevokedByName(), g.getRevokedAt());
    }
  }

  public record ActivityView(String action, String detail, String actorName, Instant occurredAt) {
    static ActivityView of(ResolvedActivity a) {
      return new ActivityView(a.getAction(), a.getDetail(), a.getActorName(), a.getOccurredAt());
    }
  }

  /** grants y activity solo para el superusuario (null para los demás). */
  public record ProjectDetail(ProjectView project, List<DocumentView> documents, List<GrantView> grants, List<ActivityView> activity) {}

  public record ProjectRequest(String name, String description, String location, BigDecimal estimatedInvestment, BigDecimal estimatedValue) {
    ProjectInput toInput() {
      return new ProjectInput(name, description, location, estimatedInvestment, estimatedValue);
    }
  }

  public record NoteRequest(String note) {}

  public record IncorporateRequest(String note, String linkedReference) {}

  public record GrantRequest(UUID userId, Boolean canDownload, Boolean canUpload) {}

  public record StaffView(UUID id, String name, String roleCode) {}

  /** Si el menú debe mostrar el apartado. */
  @GetMapping("/access")
  public AccessView access(@AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    boolean admin = ResolvedPropertyService.isAdmin(user);
    return new AccessView(admin, service.hasAnyAccess(user), admin, admin);
  }

  @GetMapping("/projects")
  public ProjectsResponse projects(@AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    List<ProjectSummary> visible = service.visibleProjects(user);
    Metrics metrics =
        new Metrics(
            visible.size(),
            (int) visible.stream().filter(s -> ResolvedProject.PENDING.equals(s.project().getApprovalStatus())).count(),
            (int) visible.stream().filter(s -> ResolvedProject.APPROVED.equals(s.project().getApprovalStatus())).count(),
            (int) visible.stream().filter(s -> ResolvedProject.INCORPORATED.equals(s.project().getCommercializationStatus())).count(),
            visible.stream().mapToInt(ProjectSummary::documentCount).sum(),
            visible.stream().map(s -> s.project().getEstimatedInvestment()).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add),
            visible.stream().map(s -> s.project().getEstimatedValue()).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
    return new ProjectsResponse(
        ResolvedPropertyService.isAdmin(user), metrics, visible.stream().map(s -> ProjectView.of(s.project(), s.documentCount(), s.access())).toList());
  }

  @PostMapping("/projects")
  @PreAuthorize(ADMIN)
  @ResponseStatus(HttpStatus.CREATED)
  public ProjectDetail create(@RequestBody ProjectRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    return detail(service.create(request.toInput(), user).getId(), user);
  }

  @GetMapping("/projects/{id}")
  public ProjectDetail get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return detail(id, CurrentUser.from(jwt));
  }

  @PutMapping("/projects/{id}")
  @PreAuthorize(ADMIN)
  public ProjectDetail update(@PathVariable UUID id, @RequestBody ProjectRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    service.update(id, request.toInput(), user);
    return detail(id, user);
  }

  @PostMapping("/projects/{id}/approve")
  @PreAuthorize(ADMIN)
  public ProjectDetail approve(@PathVariable UUID id, @RequestBody(required = false) NoteRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    service.approve(id, request == null ? null : request.note(), user);
    return detail(id, user);
  }

  @PostMapping("/projects/{id}/withdraw-approval")
  @PreAuthorize(ADMIN)
  public ProjectDetail withdrawApproval(@PathVariable UUID id, @RequestBody NoteRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    service.withdrawApproval(id, request == null ? null : request.note(), user);
    return detail(id, user);
  }

  @PostMapping("/projects/{id}/incorporate")
  @PreAuthorize(ADMIN)
  public ProjectDetail incorporate(@PathVariable UUID id, @RequestBody(required = false) IncorporateRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    service.incorporate(id, request == null ? null : request.note(), request == null ? null : request.linkedReference(), user);
    return detail(id, user);
  }

  @DeleteMapping("/projects/{id}")
  @PreAuthorize(ADMIN)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void archive(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    service.archive(id, CurrentUser.from(jwt));
  }

  @PostMapping("/projects/{id}/documents")
  @ResponseStatus(HttpStatus.CREATED)
  public DocumentView upload(
      @PathVariable UUID id,
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "name", required = false) String name,
      @RequestParam(value = "category", required = false) String category,
      @AuthenticationPrincipal Jwt jwt) {
    return DocumentView.of(service.upload(id, file, name, category, CurrentUser.from(jwt)));
  }

  /** Abre (inline) o descarga el archivo; ambas cosas se validan contra el acceso vigente. */
  @GetMapping("/documents/{documentId}/file")
  public ResponseEntity<InputStreamResource> file(
      @PathVariable UUID documentId, @RequestParam(value = "download", defaultValue = "false") boolean download, @AuthenticationPrincipal Jwt jwt) {
    FileContent content = service.open(documentId, download, CurrentUser.from(jwt));
    ResolvedDocument d = content.document();
    String filename = d.getOriginalFilename() == null ? d.getName() : d.getOriginalFilename();
    ContentDisposition disposition =
        (download ? ContentDisposition.attachment() : ContentDisposition.inline()).filename(filename, StandardCharsets.UTF_8).build();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
        .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
        .contentType(MediaType.parseMediaType(d.getContentType()))
        .contentLength(d.getSizeBytes())
        .body(new InputStreamResource(content.content()));
  }

  @DeleteMapping("/documents/{documentId}")
  @PreAuthorize(ADMIN)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeDocument(@PathVariable UUID documentId, @AuthenticationPrincipal Jwt jwt) {
    service.removeDocument(documentId, CurrentUser.from(jwt));
  }

  @PostMapping("/projects/{id}/grants")
  @PreAuthorize(ADMIN)
  public ProjectDetail grant(@PathVariable UUID id, @RequestBody GrantRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    service.grant(id, request.userId(), Boolean.TRUE.equals(request.canDownload()), Boolean.TRUE.equals(request.canUpload()), user);
    return detail(id, user);
  }

  @DeleteMapping("/projects/{id}/grants/{grantId}")
  @PreAuthorize(ADMIN)
  public ProjectDetail revoke(@PathVariable UUID id, @PathVariable UUID grantId, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    service.revoke(id, grantId, user);
    return detail(id, user);
  }

  /** Personal al que se le puede dar acceso (sin el superusuario, que ya ve todo). */
  @GetMapping("/staff")
  @PreAuthorize(ADMIN)
  public List<StaffView> staff() {
    return service.staff().stream().map(s -> new StaffView(s.id(), s.name(), s.roleCode())).toList();
  }

  private ProjectDetail detail(UUID id, CurrentUser user) {
    ResolvedProject project = service.requireVisible(id, user);
    Access access = service.accessTo(id, user);
    List<DocumentView> docs = service.documentsOf(id, user).stream().map(DocumentView::of).toList();
    boolean admin = access.admin();
    return new ProjectDetail(
        ProjectView.of(project, docs.size(), access),
        docs,
        admin ? service.grantsOf(id).stream().map(GrantView::of).toList() : null,
        admin ? service.activityOf(id).stream().map(ActivityView::of).toList() : null);
  }
}
