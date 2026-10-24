package com.c21genera.feasibility.web;

import com.c21genera.feasibility.application.FeasibilityService;
import com.c21genera.feasibility.application.FeasibilityService.Photo;
import com.c21genera.feasibility.domain.FeasibilityReport;
import com.c21genera.feasibility.domain.PresentationData;
import com.c21genera.identity.CurrentUser;
import com.c21genera.shared.config.StorageProperties;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.security.ExpedienteAccessPolicy;
import com.c21genera.shared.storage.FileStorage;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.tika.Tika;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Factibilidad comercial con IA de un expediente (Word del análisis y PowerPoint para el propietario). */
@RestController
@PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
public class FeasibilityController {

  private static final Set<String> PHOTO_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

  private final FeasibilityService service;
  private final ExpedienteAccessPolicy accessPolicy;
  private final FileStorage fileStorage;
  private final StorageProperties storageProperties;
  private final ObjectMapper objectMapper;
  private final Tika tika = new Tika();

  public FeasibilityController(
      FeasibilityService service, ExpedienteAccessPolicy accessPolicy, FileStorage fileStorage, StorageProperties storageProperties, ObjectMapper objectMapper) {
    this.service = service;
    this.accessPolicy = accessPolicy;
    this.fileStorage = fileStorage;
    this.storageProperties = storageProperties;
    this.objectMapper = objectMapper;
  }

  public record ComparableSummary(String id, String group, String zone, Long price, String url, boolean urlVerified, String locationPrecision) {}

  public record Summary(
      String executiveSummary,
      Long ownerPrice,
      Long rangeMin,
      Long rangeMax,
      Long recommendedPrice,
      String classification,
      String strategy,
      List<ComparableSummary> comparables,
      List<String> pendingItems) {}

  public record FeasibilityResponse(
      UUID id,
      UUID expedienteId,
      int versionNumber,
      FeasibilityReport.Status status,
      Instant requestedAt,
      String requestedByName,
      Instant stageStartedAt,
      Instant completedAt,
      String error,
      boolean hasDocx,
      boolean hasPptx,
      Summary summary) {}

  public record ListResponse(boolean available, List<FeasibilityResponse> reports) {}

  public record DownloadResponse(URI url) {}

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/feasibility")
  public ListResponse list(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    require(expedienteId, jwt);
    return new ListResponse(service.available(), service.listOf(expedienteId).stream().map(this::toResponse).toList());
  }

  /** Arranca una factibilidad nueva; tarda varios minutos (el front consulta el listado). */
  @PostMapping("/api/v1/internal/expedientes/{expedienteId}/feasibility")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public FeasibilityResponse request(
      @PathVariable UUID expedienteId,
      @RequestParam(required = false) BigDecimal askingPrice,
      @RequestParam(required = false) String notes,
      @RequestPart(name = "photos", required = false) List<MultipartFile> photos,
      @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = require(expedienteId, jwt);
    accessPolicy.requireNotArchived(expedienteId);
    if (notes != null && notes.length() > 4000) {
      throw new UnprocessableException("NOTES_TOO_LONG", "Las notas para la factibilidad admiten hasta 4,000 caracteres.");
    }
    if (askingPrice != null && askingPrice.signum() <= 0) {
      throw new UnprocessableException("INVALID_PRICE", "El precio solicitado debe ser mayor a cero.");
    }
    List<Photo> accepted = new ArrayList<>();
    for (MultipartFile file : photos == null ? List.<MultipartFile>of() : photos) {
      if (file.isEmpty()) {
        continue;
      }
      try {
        byte[] bytes = file.getBytes();
        if (!PHOTO_TYPES.contains(tika.detect(bytes))) {
          throw new UnprocessableException("INVALID_PHOTO", "\"" + file.getOriginalFilename() + "\" no es una fotografía JPG, PNG o WEBP.");
        }
        accepted.add(new Photo(file.getOriginalFilename(), bytes));
      } catch (IOException e) {
        throw new UnprocessableException("INVALID_PHOTO", "No se pudo leer una de las fotografías.");
      }
    }
    return toResponse(service.request(expedienteId, askingPrice, notes, accepted, user.id(), user.name()));
  }

  @GetMapping("/api/v1/internal/feasibility/{reportId}")
  public FeasibilityResponse get(@PathVariable UUID reportId, @AuthenticationPrincipal Jwt jwt) {
    FeasibilityReport report = service.get(reportId);
    require(report.getExpedienteId(), jwt);
    return toResponse(report);
  }

  /** file=docx (reporte de factibilidad) o pptx (presentación para el propietario). */
  @GetMapping("/api/v1/internal/feasibility/{reportId}/download")
  public DownloadResponse download(@PathVariable UUID reportId, @RequestParam(defaultValue = "docx") String file, @AuthenticationPrincipal Jwt jwt) {
    FeasibilityReport report = service.get(reportId);
    require(report.getExpedienteId(), jwt);
    String key = "pptx".equals(file) ? report.getPptxStorageKey() : report.getDocxStorageKey();
    if (key == null) {
      throw new NotFoundException("Ese archivo todavía no está disponible para esta factibilidad.");
    }
    return new DownloadResponse(fileStorage.generateTemporaryDownloadUrl(key, storageProperties.presignedUrlTtl()));
  }

  private FeasibilityResponse toResponse(FeasibilityReport r) {
    return new FeasibilityResponse(
        r.getId(),
        r.getExpedienteId(),
        r.getVersionNumber(),
        r.getStatus(),
        r.getRequestedAt(),
        r.getRequestedByName(),
        r.getStageStartedAt(),
        r.getCompletedAt(),
        r.getError(),
        r.getDocxStorageKey() != null,
        r.getPptxStorageKey() != null,
        summaryOf(r));
  }

  private Summary summaryOf(FeasibilityReport r) {
    if (r.getPresentationJson() == null) {
      return null;
    }
    try {
      PresentationData d = objectMapper.readValue(r.getPresentationJson(), PresentationData.class);
      var p = d.pricing();
      return new Summary(
          d.executiveSummary(),
          p == null ? null : p.ownerPrice(),
          p == null ? null : p.rangeMin(),
          p == null ? null : p.rangeMax(),
          p == null ? null : p.recommendedPrice(),
          p == null ? null : p.classification(),
          p == null ? null : p.strategy(),
          PresentationData.safe(d.comparables()).stream()
              .map(c -> new ComparableSummary(c.id(), c.group(), c.zone(), c.price(), c.url(), c.urlVerified(), c.locationPrecision()))
              .toList(),
          PresentationData.safe(d.pendingItems()));
    } catch (IOException e) {
      return null;
    }
  }

  private CurrentUser require(UUID expedienteId, Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    accessPolicy.requireAccess(expedienteId, user.id(), user.permissions());
    return user;
  }
}
