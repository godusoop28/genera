package com.c21genera.publicaccess.web;

import com.c21genera.identity.CurrentUser;
import com.c21genera.publicaccess.PublicAccessTokenApi;
import com.c21genera.publicaccess.PublicAccessTokenApi.LinkStatus;
import com.c21genera.shared.config.PublicLinkProperties;
import com.c21genera.shared.security.ExpedienteAccessPolicy;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * No depende del módulo expedientes (evitaría un ciclo entre módulos, ver
 * AGENTS §7): el acceso se verifica con la política compartida
 * ExpedienteAccessPolicy (implementada por expedientes).
 */
@RestController
@RequestMapping("/api/v1/internal/expedientes/{expedienteId}/public-link")
public class PublicLinkController {

  private final PublicAccessTokenApi tokenApi;
  private final PublicLinkProperties properties;
  private final ExpedienteAccessPolicy accessPolicy;

  public PublicLinkController(PublicAccessTokenApi tokenApi, PublicLinkProperties properties, ExpedienteAccessPolicy accessPolicy) {
    this.tokenApi = tokenApi;
    this.properties = properties;
    this.accessPolicy = accessPolicy;
  }

  public record PublicLinkResponse(String url, Instant expiresAt) {}

  /** Nunca incluye la URL: el token no se guarda, solo su hash. */
  public record PublicLinkStatusResponse(Instant createdAt, Instant expiresAt, Instant revokedAt, Instant lastUsedAt, boolean usable) {}

  @GetMapping
  @PreAuthorize("hasAnyAuthority('EXPEDIENT_VIEW_ALL', 'EXPEDIENT_VIEW_OWN')")
  public ResponseEntity<PublicLinkStatusResponse> status(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    requireAccess(expedienteId, jwt);
    return tokenApi
        .statusOf(expedienteId)
        .map(PublicLinkController::toStatus)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  /** Genera una liga nueva; la anterior (si existía) deja de funcionar de inmediato. */
  @PostMapping
  @PreAuthorize("hasAuthority('PUBLIC_LINK_GENERATE')")
  @ResponseStatus(HttpStatus.CREATED)
  public PublicLinkResponse generate(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(expedienteId, jwt);
    PublicAccessTokenApi.IssuedToken issued = tokenApi.generate(expedienteId, user.toActor());
    return new PublicLinkResponse(properties.baseUrl() + "/" + issued.rawToken(), issued.expiresAt());
  }

  @PostMapping("/regenerate")
  @PreAuthorize("hasAuthority('PUBLIC_LINK_GENERATE')")
  public PublicLinkResponse regenerate(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    return generate(expedienteId, jwt);
  }

  @DeleteMapping
  @PreAuthorize("hasAuthority('PUBLIC_LINK_GENERATE')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revoke(@PathVariable UUID expedienteId, @AuthenticationPrincipal Jwt jwt) {
    CurrentUser user = requireAccess(expedienteId, jwt);
    tokenApi.revoke(expedienteId, user.toActor(), "Revocada por el staff");
  }

  private static PublicLinkStatusResponse toStatus(LinkStatus s) {
    return new PublicLinkStatusResponse(s.createdAt(), s.expiresAt(), s.revokedAt(), s.lastUsedAt(), s.usable());
  }

  private CurrentUser requireAccess(UUID expedienteId, Jwt jwt) {
    CurrentUser user = CurrentUser.from(jwt);
    accessPolicy.requireAccess(expedienteId, user.id(), user.permissions());
    return user;
  }
}
