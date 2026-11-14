package com.c21genera.resolvedproperty.application;

import com.c21genera.identity.CurrentUser;
import com.c21genera.identity.UserDirectory;
import com.c21genera.resolvedproperty.domain.ResolvedAccessGrant;
import com.c21genera.resolvedproperty.domain.ResolvedActivity;
import com.c21genera.resolvedproperty.domain.ResolvedDocument;
import com.c21genera.resolvedproperty.domain.ResolvedProject;
import com.c21genera.resolvedproperty.infrastructure.ResolvedAccessGrantRepository;
import com.c21genera.resolvedproperty.infrastructure.ResolvedActivityRepository;
import com.c21genera.resolvedproperty.infrastructure.ResolvedDocumentRepository;
import com.c21genera.resolvedproperty.infrastructure.ResolvedProjectRepository;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.storage.FileStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Control de Cambios #42: apartado «Propiedad Resuelta». Reglas de acceso (se comprueban en cada
 * petición, también al abrir o descargar un archivo por enlace directo):
 *
 * <ul>
 *   <li>Con RESOLVED_PROPERTY_ADMIN (director general / superusuario): todo el apartado.
 *   <li>Cualquier otro usuario: solo los proyectos con un acceso vigente otorgado expresamente;
 *       descargar y subir son permisos aparte. Sin acceso, el proyecto «no existe» (404).
 *   <li>Aprobar o incorporar un proyecto nunca abre el acceso a nadie más.
 * </ul>
 */
@Service
@Transactional
public class ResolvedPropertyService {

  public static final String ADMIN_PERMISSION = "RESOLVED_PROPERTY_ADMIN";
  static final long MAX_FILE_BYTES = 50L * 1024 * 1024;
  private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "jpg", "jpeg", "png", "webp", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "dwg", "zip");

  private final ResolvedProjectRepository projects;
  private final ResolvedDocumentRepository documents;
  private final ResolvedAccessGrantRepository grants;
  private final ResolvedActivityRepository activity;
  private final FileStorage storage;
  private final UserDirectory users;
  private final Clock clock;

  public ResolvedPropertyService(
      ResolvedProjectRepository projects,
      ResolvedDocumentRepository documents,
      ResolvedAccessGrantRepository grants,
      ResolvedActivityRepository activity,
      FileStorage storage,
      UserDirectory users,
      Clock clock) {
    this.projects = projects;
    this.documents = documents;
    this.grants = grants;
    this.activity = activity;
    this.storage = storage;
    this.users = users;
    this.clock = clock;
  }

  /** Lo que el usuario puede hacer en un proyecto. */
  public record Access(boolean admin, boolean view, boolean download, boolean upload) {

    static final Access NONE = new Access(false, false, false, false);
    static final Access FULL = new Access(true, true, true, true);
  }

  public static boolean isAdmin(CurrentUser user) {
    return user.hasPermission(ADMIN_PERMISSION);
  }

  @Transactional(readOnly = true)
  public Access accessTo(UUID projectId, CurrentUser user) {
    if (isAdmin(user)) {
      return Access.FULL;
    }
    return grants
        .findFirstByProjectIdAndUserIdAndRevokedAtIsNull(projectId, user.id())
        .map(g -> new Access(false, true, g.isCanDownload(), g.isCanUpload()))
        .orElse(Access.NONE);
  }

  /** ¿Debe verse el apartado en el menú? Solo para el superusuario o con algún proyecto autorizado. */
  @Transactional(readOnly = true)
  public boolean hasAnyAccess(CurrentUser user) {
    return isAdmin(user) || !grants.findByUserIdAndRevokedAtIsNull(user.id()).isEmpty();
  }

  // --- Proyectos ---------------------------------------------------------------

  public record ProjectSummary(ResolvedProject project, int documentCount, Access access) {}

  @Transactional(readOnly = true)
  public List<ProjectSummary> visibleProjects(CurrentUser user) {
    List<ResolvedProject> all = projects.findByArchivedAtIsNullOrderByCreatedAtDesc();
    Map<UUID, ResolvedAccessGrant> mine =
        isAdmin(user)
            ? Map.of()
            : grants.findByUserIdAndRevokedAtIsNull(user.id()).stream().collect(Collectors.toMap(ResolvedAccessGrant::getProjectId, Function.identity(), (a, b) -> a));
    Map<UUID, Long> docCounts = documents.findByRemovedAtIsNull().stream().collect(Collectors.groupingBy(ResolvedDocument::getProjectId, Collectors.counting()));
    return all.stream()
        .filter(p -> isAdmin(user) || mine.containsKey(p.getId()))
        .map(
            p -> {
              ResolvedAccessGrant g = mine.get(p.getId());
              Access access = isAdmin(user) ? Access.FULL : new Access(false, true, g.isCanDownload(), g.isCanUpload());
              return new ProjectSummary(p, docCounts.getOrDefault(p.getId(), 0L).intValue(), access);
            })
        .toList();
  }

  @Transactional(readOnly = true)
  public ResolvedProject requireVisible(UUID projectId, CurrentUser user) {
    ResolvedProject project = projects.findById(projectId).filter(p -> p.getArchivedAt() == null).orElseThrow(() -> notFound());
    if (!accessTo(projectId, user).view()) {
      // Mismo 404 que si no existiera: no se revela qué proyectos hay.
      throw notFound();
    }
    return project;
  }

  public record ProjectInput(String name, String description, String location, BigDecimal estimatedInvestment, BigDecimal estimatedValue) {}

  public ResolvedProject create(ProjectInput input, CurrentUser user) {
    String name = requireName(input.name());
    ResolvedProject project = projects.save(new ResolvedProject(name, user.id(), user.name(), clock.instant()));
    project.update(name, blank(input.description()), blank(input.location()), input.estimatedInvestment(), input.estimatedValue(), clock.instant());
    log(project.getId(), "PROJECT_CREATED", "Creó el proyecto «" + name + "»", user);
    return project;
  }

  public ResolvedProject update(UUID projectId, ProjectInput input, CurrentUser user) {
    ResolvedProject project = requireVisible(projectId, user);
    project.update(requireName(input.name()), blank(input.description()), blank(input.location()), input.estimatedInvestment(), input.estimatedValue(), clock.instant());
    log(projectId, "PROJECT_UPDATED", "Actualizó los datos del proyecto", user);
    return project;
  }

  public ResolvedProject approve(UUID projectId, String note, CurrentUser user) {
    ResolvedProject project = requireVisible(projectId, user);
    project.approve(user.id(), user.name(), blank(note), clock.instant());
    log(projectId, "PROJECT_APPROVED", "Aprobó el proyecto para comercialización" + (blank(note) == null ? "" : ": " + note.strip()), user);
    return project;
  }

  public ResolvedProject withdrawApproval(UUID projectId, String reason, CurrentUser user) {
    String cleaned = blank(reason);
    if (cleaned == null || cleaned.length() < 10) {
      throw new UnprocessableException("REASON_REQUIRED", "Explica por qué se retira la aprobación (mínimo 10 caracteres).");
    }
    ResolvedProject project = requireVisible(projectId, user);
    project.withdrawApproval(clock.instant());
    log(projectId, "APPROVAL_WITHDRAWN", "Retiró la aprobación: " + cleaned, user);
    return project;
  }

  /**
   * Pasa el proyecto aprobado al flujo de comercialización de CENTURY 21 Genera. No crea ni publica
   * nada por sí solo, no mueve ni duplica archivos y no abre accesos: deja el registro de quién y
   * cuándo, y la referencia (p. ej. folio del expediente o clave de la propiedad) si ya existe.
   */
  public ResolvedProject incorporate(UUID projectId, String note, String linkedReference, CurrentUser user) {
    ResolvedProject project = requireVisible(projectId, user);
    project.incorporate(user.name(), blank(note), blank(linkedReference), clock.instant());
    log(projectId, "PROJECT_INCORPORATED",
        "Incorporó el proyecto al flujo de comercialización de CENTURY 21 Genera" + (blank(linkedReference) == null ? "" : " (referencia: " + linkedReference.strip() + ")"), user);
    return project;
  }

  public void archive(UUID projectId, CurrentUser user) {
    ResolvedProject project = requireVisible(projectId, user);
    project.archive(clock.instant());
    log(projectId, "PROJECT_ARCHIVED", "Archivó el proyecto", user);
  }

  // --- Documentos --------------------------------------------------------------

  @Transactional(readOnly = true)
  public List<ResolvedDocument> documentsOf(UUID projectId, CurrentUser user) {
    requireVisible(projectId, user);
    return documents.findByProjectIdAndRemovedAtIsNullOrderByUploadedAtDesc(projectId);
  }

  public ResolvedDocument upload(UUID projectId, MultipartFile file, String name, String category, CurrentUser user) {
    requireVisible(projectId, user);
    if (!accessTo(projectId, user).upload()) {
      throw new UnprocessableException("UPLOAD_NOT_ALLOWED", "Tu acceso a este proyecto no incluye subir archivos.");
    }
    if (file == null || file.isEmpty()) {
      throw new UnprocessableException("NO_FILES", "Selecciona un archivo.");
    }
    if (file.getSize() > MAX_FILE_BYTES) {
      throw new UnprocessableException("FILE_TOO_LARGE", "El archivo pesa más de 50 MB.");
    }
    String original = file.getOriginalFilename() == null ? "archivo" : file.getOriginalFilename();
    String extension = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
    if (!ALLOWED_EXTENSIONS.contains(extension)) {
      throw new UnprocessableException("FILE_TYPE_NOT_ALLOWED", "Tipo de archivo no permitido. Usa PDF, imagen, Word, Excel, PowerPoint, CSV, DWG o ZIP.");
    }
    UUID id = UUID.randomUUID();
    String key = "propiedad-resuelta/%s/%s.%s".formatted(projectId, id, extension);
    String contentType = file.getContentType() == null || file.getContentType().isBlank() ? "application/octet-stream" : file.getContentType();
    FileStorage.StoredObjectMetadata stored;
    try (InputStream in = file.getInputStream()) {
      stored = storage.store(key, in, file.getSize(), contentType);
    } catch (IOException e) {
      throw new UncheckedIOException("No se pudo leer el archivo", e);
    }
    String displayName = blank(name) == null ? original : name.strip();
    ResolvedDocument document =
        documents.save(
            new ResolvedDocument(
                id, projectId, truncate(displayName, 200), truncate(blank(category), 80), stored.storageKey(), contentType, stored.size(), stored.sha256(), truncate(original, 255),
                user.id(), user.name(), clock.instant()));
    log(projectId, "DOCUMENT_UPLOADED", "Subió «" + document.getName() + "»", user);
    return document;
  }

  public record FileContent(ResolvedDocument document, InputStream content) {}

  /** Abrir (download=false) exige ver el proyecto; descargar exige además el permiso de descarga. */
  public FileContent open(UUID documentId, boolean download, CurrentUser user) {
    ResolvedDocument document = documents.findById(documentId).filter(d -> d.getRemovedAt() == null).orElseThrow(() -> notFound());
    Access access = accessTo(document.getProjectId(), user);
    if (!access.view()) {
      throw notFound();
    }
    if (download && !access.download()) {
      throw new UnprocessableException("DOWNLOAD_NOT_ALLOWED", "Tu acceso a este proyecto permite consultar los archivos, pero no descargarlos.");
    }
    log(document.getProjectId(), download ? "DOCUMENT_DOWNLOADED" : "DOCUMENT_VIEWED", (download ? "Descargó «" : "Consultó «") + document.getName() + "»", user);
    return new FileContent(document, storage.get(document.getStorageKey()));
  }

  public void removeDocument(UUID documentId, CurrentUser user) {
    ResolvedDocument document = documents.findById(documentId).filter(d -> d.getRemovedAt() == null).orElseThrow(() -> notFound());
    requireVisible(document.getProjectId(), user);
    document.remove(user.name(), clock.instant());
    log(document.getProjectId(), "DOCUMENT_REMOVED", "Retiró «" + document.getName() + "» (el archivo se conserva para auditoría)", user);
  }

  // --- Accesos -----------------------------------------------------------------

  @Transactional(readOnly = true)
  public List<ResolvedAccessGrant> grantsOf(UUID projectId) {
    return grants.findByProjectIdOrderByGrantedAtDesc(projectId);
  }

  public ResolvedAccessGrant grant(UUID projectId, UUID userId, boolean canDownload, boolean canUpload, CurrentUser admin) {
    requireVisible(projectId, admin);
    UserDirectory.UserContact contact =
        users.contactOf(userId).filter(UserDirectory.UserContact::active).orElseThrow(() -> new NotFoundException("Usuario", userId));
    Instant now = clock.instant();
    Optional<ResolvedAccessGrant> existing = grants.findFirstByProjectIdAndUserIdAndRevokedAtIsNull(projectId, userId);
    ResolvedAccessGrant grant;
    if (existing.isPresent()) {
      grant = existing.get();
      grant.change(canDownload, canUpload, admin.name(), now);
      log(projectId, "ACCESS_CHANGED", "Cambió el acceso de " + contact.name() + ": " + describe(canDownload, canUpload), admin);
    } else {
      grant = grants.save(new ResolvedAccessGrant(projectId, userId, contact.name(), canDownload, canUpload, admin.name(), now));
      log(projectId, "ACCESS_GRANTED", "Dio acceso a " + contact.name() + ": " + describe(canDownload, canUpload), admin);
    }
    return grant;
  }

  public void revoke(UUID projectId, UUID grantId, CurrentUser admin) {
    requireVisible(projectId, admin);
    ResolvedAccessGrant grant = grants.findById(grantId).filter(g -> g.getProjectId().equals(projectId)).orElseThrow(() -> new NotFoundException("Acceso", grantId));
    if (grant.isActive()) {
      grant.revoke(admin.name(), clock.instant());
      log(projectId, "ACCESS_REVOKED", "Retiró el acceso de " + grant.getUserName(), admin);
    }
  }

  @Transactional(readOnly = true)
  public List<UserDirectory.StaffMember> staff() {
    return users.activeStaff();
  }

  @Transactional(readOnly = true)
  public List<ResolvedActivity> activityOf(UUID projectId) {
    return activity.findTop100ByProjectIdOrderByOccurredAtDesc(projectId);
  }

  // --- Auxiliares --------------------------------------------------------------

  private static String describe(boolean download, boolean upload) {
    return "ver" + (download ? ", descargar" : "") + (upload ? ", subir archivos" : "");
  }

  private void log(UUID projectId, String action, String detail, CurrentUser user) {
    activity.save(new ResolvedActivity(projectId, action, detail, user.id(), user.name(), clock.instant()));
  }

  private static NotFoundException notFound() {
    return new NotFoundException("El proyecto no existe o no tienes acceso a él.");
  }

  private static String requireName(String name) {
    String cleaned = blank(name);
    if (cleaned == null || cleaned.length() < 3) {
      throw new UnprocessableException("NAME_REQUIRED", "Escribe el nombre del proyecto (mínimo 3 caracteres).");
    }
    return truncate(cleaned, 160);
  }

  private static String blank(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static String truncate(String value, int max) {
    return value == null || value.length() <= max ? value : value.substring(0, max);
  }
}
