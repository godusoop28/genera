package com.c21genera.photos.web;

import com.c21genera.identity.CurrentUser;
import com.c21genera.photos.application.PropertyPhotoService;
import com.c21genera.photos.application.PropertyPhotoService.Limits;
import com.c21genera.photos.application.PropertyPhotoService.PhotoView;
import com.c21genera.photos.application.PropertyPhotoService.SelectionItem;
import com.c21genera.photos.domain.PropertyPhoto.UploadedBy;
import com.c21genera.publicaccess.PublicAccessTokenApi;
import com.c21genera.publicaccess.PublicLinkRevokedException;
import com.c21genera.shared.security.ExpedienteAccessPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Control de Cambios #33: fotos del inmueble. El cliente sube desde su liga (sin cuenta: el token
 * es la credencial y se resuelve a su expediente); el staff con acceso al expediente las consulta y
 * el que puede editarlo sube las suyas, elige las del portal y retira las que no sirven.
 */
@RestController
public class PropertyPhotoController {

  private final PropertyPhotoService photos;
  private final PublicAccessTokenApi tokens;
  private final ExpedienteAccessPolicy accessPolicy;

  public PropertyPhotoController(PropertyPhotoService photos, PublicAccessTokenApi tokens, ExpedienteAccessPolicy accessPolicy) {
    this.photos = photos;
    this.tokens = tokens;
    this.accessPolicy = accessPolicy;
  }

  public record PhotosResponse(Limits limits, List<PhotoView> photos) {}

  public record SelectionRequest(@NotNull @Size(max = 40) List<@Valid @NotNull SelectionItem> items) {}

  // --- Portal del cliente -----------------------------------------------------

  @GetMapping("/api/v1/public/expedientes/{token}/photos")
  public PhotosResponse publicList(@PathVariable String token) {
    UUID expedienteId = resolve(token);
    return new PhotosResponse(photos.limits(expedienteId), photos.listForClient(expedienteId));
  }

  @PostMapping("/api/v1/public/expedientes/{token}/photos")
  @ResponseStatus(HttpStatus.CREATED)
  public PhotosResponse publicUpload(@PathVariable String token, @RequestParam("files") List<MultipartFile> files) {
    UUID expedienteId = resolve(token);
    photos.upload(expedienteId, files, UploadedBy.CLIENT, null, "Cliente");
    return new PhotosResponse(photos.limits(expedienteId), photos.listForClient(expedienteId));
  }

  // --- Expediente (staff) -----------------------------------------------------

  @GetMapping("/api/v1/internal/expedientes/{expedienteId}/photos")
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
  public PhotosResponse list(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    requireAccess(expedienteId, jwt);
    return new PhotosResponse(photos.limits(expedienteId), photos.list(expedienteId));
  }

  @PostMapping("/api/v1/internal/expedientes/{expedienteId}/photos")
  @PreAuthorize("hasAuthority('EXPEDIENT_EDIT')")
  @ResponseStatus(HttpStatus.CREATED)
  public PhotosResponse upload(@PathVariable UUID expedienteId, @RequestParam("files") List<MultipartFile> files, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(expedienteId, jwt);
    photos.upload(expedienteId, files, UploadedBy.STAFF, user.id(), user.name());
    return new PhotosResponse(photos.limits(expedienteId), photos.list(expedienteId));
  }

  @PutMapping("/api/v1/internal/expedientes/{expedienteId}/photos/selection")
  @PreAuthorize("hasAuthority('EXPEDIENT_EDIT')")
  public PhotosResponse select(@PathVariable UUID expedienteId, @Valid @RequestBody SelectionRequest request, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(expedienteId, jwt);
    photos.saveSelection(expedienteId, request.items(), user.name());
    return new PhotosResponse(photos.limits(expedienteId), photos.list(expedienteId));
  }

  @DeleteMapping("/api/v1/internal/expedientes/{expedienteId}/photos/{photoId}")
  @PreAuthorize("hasAuthority('EXPEDIENT_EDIT')")
  public PhotosResponse remove(@PathVariable UUID expedienteId, @PathVariable UUID photoId, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(expedienteId, jwt);
    photos.remove(expedienteId, photoId, user.name());
    return new PhotosResponse(photos.limits(expedienteId), photos.list(expedienteId));
  }

  private UUID resolve(String token) {
    return tokens.resolve(token).orElseThrow(PublicLinkRevokedException::new);
  }

  private CurrentUser requireAccess(UUID expedienteId, Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    accessPolicy.requireAccess(expedienteId, user.id(), user.permissions());
    return user;
  }
}
