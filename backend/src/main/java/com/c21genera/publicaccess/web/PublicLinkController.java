package com.c21genera.publicaccess.web;

import com.c21genera.expedientes.ExpedienteLifecycleApi;
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

@RestController
@RequestMapping("/api/v1/internal/expedientes/{expedienteId}/public-link")
@PreAuthorize("hasAuthority('PUBLIC_LINK_GENERATE')")
public class PublicLinkController {

  private final PublicAccessTokenApi tokenApi;
  private final ExpedienteLifecycleApi expedienteApi;
  private final PublicLinkProperties properties;

  public PublicLinkController(
      PublicAccessTokenApi tokenApi, ExpedienteLifecycleApi expedienteApi, PublicLinkProperties properties) {
    this.tokenApi = tokenApi;
    this.expedienteApi = expedienteApi;
    this.properties = properties;
  }

  public record PublicLinkResponse(String url, Instant expiresAt) {}

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PublicLinkResponse generate(@PathVariable UUID expedienteId) {
    expedienteApi.getSummary(expedienteId); // valida existencia (404 si no existe)
    PublicAccessTokenApi.IssuedToken issued = tokenApi.generate(expedienteId);
    return toResponse(issued);
  }

  @PostMapping("/regenerate")
  public PublicLinkResponse regenerate(@PathVariable UUID expedienteId) {
    expedienteApi.getSummary(expedienteId);
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
