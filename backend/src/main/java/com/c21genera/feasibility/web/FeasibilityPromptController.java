package com.c21genera.feasibility.web;

import com.c21genera.feasibility.application.FeasibilityPromptService;
import com.c21genera.feasibility.application.FeasibilityPromptService.PromptView;
import com.c21genera.feasibility.application.FeasibilityPromptService.VersionSummary;
import com.c21genera.feasibility.domain.FeasibilityPromptVersion.PromptKey;
import com.c21genera.identity.CurrentUser;
import com.c21genera.shared.domain.NotFoundException;
import java.util.List;
import java.util.Locale;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Prompts maestros de la factibilidad, editables por el administrador en Configuración (con historial). */
@RestController
@PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
public class FeasibilityPromptController {

  private static final String BASE = "/api/v1/internal/settings/feasibility-prompts";

  private final FeasibilityPromptService service;

  public FeasibilityPromptController(FeasibilityPromptService service) {
    this.service = service;
  }

  public record UpdateRequest(String content, String note) {}

  public record RestoreRequest(Integer version) {}

  public record VersionContent(int version, String content) {}

  @GetMapping(BASE)
  public List<PromptView> list() {
    return service.list();
  }

  @PutMapping(BASE + "/{key}")
  public PromptView update(@PathVariable String key, @RequestBody UpdateRequest request, @AuthenticationPrincipal Jwt jwt) {
    return service.update(parse(key), request == null ? null : request.content(), request == null ? null : request.note(),
        CurrentUser.from(jwt).toActor());
  }

  @GetMapping(BASE + "/{key}/versions")
  public List<VersionSummary> versions(@PathVariable String key) {
    return service.versions(parse(key));
  }

  @GetMapping(BASE + "/{key}/versions/{version}")
  public VersionContent version(@PathVariable String key, @PathVariable int version) {
    return new VersionContent(version, service.versionContent(parse(key), version));
  }

  @PostMapping(BASE + "/{key}/restore")
  public PromptView restore(@PathVariable String key, @RequestBody RestoreRequest request, @AuthenticationPrincipal Jwt jwt) {
    int version = request == null || request.version() == null ? 0 : request.version();
    return service.restore(parse(key), version, CurrentUser.from(jwt).toActor());
  }

  private static PromptKey parse(String key) {
    try {
      return PromptKey.valueOf(key.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new NotFoundException("Prompt desconocido: " + key);
    }
  }
}
