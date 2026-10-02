package com.c21genera.drafts.web;

import com.c21genera.drafts.application.FormDraftService;
import com.c21genera.drafts.application.FormDraftService.DraftView;
import com.c21genera.identity.CurrentUser;
import com.c21genera.publicaccess.PublicAccessTokenApi;
import com.c21genera.publicaccess.PublicLinkRevokedException;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Borradores autoguardados. GET responde 204 si no hay borrador. El
 * contenido es el JSON del formulario tal como lo manda el navegador: el
 * backend no lo interpreta, solo lo guarda y lo devuelve a su dueño.
 */
@RestController
public class FormDraftController {

  private final FormDraftService service;
  private final PublicAccessTokenApi tokenApi;

  public FormDraftController(FormDraftService service, PublicAccessTokenApi tokenApi) {
    this.service = service;
    this.tokenApi = tokenApi;
  }

  public record DraftRequest(String payload) {}

  // --- Staff (usuario autenticado) -------------------------------------

  @GetMapping("/api/v1/internal/drafts/{formKey}")
  public ResponseEntity<DraftView> getOwn(@PathVariable String formKey, @AuthenticationPrincipal Jwt jwt) {
    return service.find(FormDraftService.userOwner(CurrentUser.from(jwt).id()), formKey)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @PutMapping("/api/v1/internal/drafts/{formKey}")
  public DraftView saveOwn(@PathVariable String formKey, @RequestBody DraftRequest request, @AuthenticationPrincipal Jwt jwt) {
    return service.save(FormDraftService.userOwner(CurrentUser.from(jwt).id()), formKey, request == null ? null : request.payload());
  }

  @DeleteMapping("/api/v1/internal/drafts/{formKey}")
  public ResponseEntity<Void> deleteOwn(@PathVariable String formKey, @AuthenticationPrincipal Jwt jwt) {
    service.delete(FormDraftService.userOwner(CurrentUser.from(jwt).id()), formKey);
    return ResponseEntity.noContent().build();
  }

  // --- Cliente (liga pública) ------------------------------------------

  @GetMapping("/api/v1/public/expedientes/{token}/drafts/{formKey}")
  public ResponseEntity<DraftView> getForLink(@PathVariable String token, @PathVariable String formKey) {
    return service.find(FormDraftService.linkOwner(resolve(token)), formKey)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @PutMapping("/api/v1/public/expedientes/{token}/drafts/{formKey}")
  public DraftView saveForLink(@PathVariable String token, @PathVariable String formKey, @RequestBody DraftRequest request) {
    return service.save(FormDraftService.linkOwner(resolve(token)), formKey, request == null ? null : request.payload());
  }

  @DeleteMapping("/api/v1/public/expedientes/{token}/drafts/{formKey}")
  public ResponseEntity<Void> deleteForLink(@PathVariable String token, @PathVariable String formKey) {
    service.delete(FormDraftService.linkOwner(resolve(token)), formKey);
    return ResponseEntity.noContent().build();
  }

  private UUID resolve(String token) {
    return tokenApi.resolve(token).orElseThrow(PublicLinkRevokedException::new);
  }
}
