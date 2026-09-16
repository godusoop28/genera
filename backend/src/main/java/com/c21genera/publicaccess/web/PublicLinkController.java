package com.c21genera.publicaccess.web;

import com.c21genera.publicaccess.PublicAccessTokenApi;
import com.c21genera.shared.config.PublicLinkProperties;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * No depende del módulo expedientes (evitaría un ciclo entre módulos, ver
 * AGENTS §7): la existencia del expediente la garantiza la llave foránea de
 * public_access_token -> expediente en la base de datos.
 */
@RestController
@RequestMapping("/api/v1/internal/expedientes/{expedienteId}/public-link")
@PreAuthorize("hasAuthority('PUBLIC_LINK_GENERATE')")
public class PublicLinkController {

  private final PublicAccessTokenApi tokenApi;
  private final PublicLinkProperties properties;

  public PublicLinkController(PublicAccessTokenApi tokenApi, PublicLinkProperties properties) {
    this.tokenApi = tokenApi;
    this.properties = properties;
  }

  public record PublicLinkResponse(String url, Instant expiresAt) {}

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PublicLinkResponse generate(@PathVariable UUID expedienteId) {
    return toResponse(tokenApi.generate(expedienteId));
  }

  @PostMapping("/regenerate")
  public PublicLinkResponse regenerate(@PathVariable UUID expedienteId) {
    return toResponse(tokenApi.regenerate(expedienteId));
  }

  @DeleteMapping
  public void revoke(@PathVariable UUID expedienteId) {
    tokenApi.revoke(expedienteId);
  }

  private PublicLinkResponse toResponse(PublicAccessTokenApi.IssuedToken issued) {
    return new PublicLinkResponse(properties.baseUrl() + "/" + issued.rawToken(), issued.expiresAt());
  }
}
