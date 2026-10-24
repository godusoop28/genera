package com.c21genera.feasibility.application;

import com.c21genera.feasibility.domain.FeasibilityPromptVersion;
import com.c21genera.feasibility.domain.FeasibilityPromptVersion.PromptKey;
import com.c21genera.feasibility.infrastructure.FeasibilityPromptVersionRepository;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.events.Actor;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Los dos prompts maestros de la factibilidad, editables desde Configuración. Cada guardado crea la
 * versión siguiente (nunca se sobrescribe); la vigente es la de número más alto. La versión 0 es el
 * texto original que entregó el cliente (resources/feasibility). Las reglas de operación del sistema
 * (formato que el servidor necesita para armar el Word y el PowerPoint) se agregan siempre al final
 * y no se editan.
 */
@Service
@Transactional
public class FeasibilityPromptService {

  static final int MIN_LENGTH = 200;
  static final int MAX_LENGTH = 100_000;

  /** version 0 = texto original del cliente. */
  public record PromptView(
      PromptKey key, String title, String content, int version, boolean original, Instant updatedAt, String updatedByName, String systemRules) {}

  public record VersionSummary(int version, Instant createdAt, String createdByName, String note, int length, boolean current) {}

  /** Texto que se manda a la IA (prompt vigente + reglas del sistema) y la versión usada. */
  public record Instructions(String text, int version) {}

  private final FeasibilityPromptVersionRepository repository;
  private final Clock clock;

  public FeasibilityPromptService(FeasibilityPromptVersionRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  static String title(PromptKey key) {
    return key == PromptKey.ANALYST ? "Analista de Factibilidad 3.0 (reporte en Word)" : "Presentación de factibilidad (PowerPoint para el propietario)";
  }

  static String original(PromptKey key) {
    return key == PromptKey.ANALYST ? FeasibilityPrompts.ANALYST : FeasibilityPrompts.PRESENTATION;
  }

  static String systemRules(PromptKey key) {
    return key == PromptKey.ANALYST ? FeasibilityPrompts.ANALYST_OPERATION : FeasibilityPrompts.PRESENTATION_OPERATION;
  }

  @Transactional(readOnly = true)
  public List<PromptView> list() {
    return List.of(current(PromptKey.ANALYST), current(PromptKey.PRESENTATION));
  }

  @Transactional(readOnly = true)
  public PromptView current(PromptKey key) {
    return repository
        .findFirstByKeyOrderByVersionNumberDesc(key)
        .map(v -> new PromptView(key, title(key), v.getContent(), v.getVersionNumber(), false, v.getCreatedAt(), v.getCreatedByName(), systemRules(key)))
        .orElseGet(() -> new PromptView(key, title(key), original(key), 0, true, null, null, systemRules(key)));
  }

  @Transactional(readOnly = true)
  public Instructions instructions(PromptKey key) {
    PromptView current = current(key);
    return new Instructions(current.content() + systemRules(key), current.version());
  }

  public PromptView update(PromptKey key, String content, String note, Actor actor) {
    String cleaned = content == null ? "" : content.strip();
    if (cleaned.length() < MIN_LENGTH) {
      throw new UnprocessableException("PROMPT_TOO_SHORT", "El prompt debe tener al menos " + MIN_LENGTH + " caracteres.");
    }
    if (cleaned.length() > MAX_LENGTH) {
      throw new UnprocessableException("PROMPT_TOO_LONG", "El prompt puede tener hasta " + String.format("%,d", MAX_LENGTH) + " caracteres.");
    }
    PromptView current = current(key);
    if (cleaned.equals(current.content().strip())) {
      return current;
    }
    String n = note == null || note.isBlank() ? null : note.strip();
    if (n != null && n.length() > 200) {
      n = n.substring(0, 200);
    }
    repository.save(new FeasibilityPromptVersion(key, current.version() + 1, cleaned, n, clock.instant(), actor.userId(), actor.name()));
    return current(key);
  }

  @Transactional(readOnly = true)
  public List<VersionSummary> versions(PromptKey key) {
    List<FeasibilityPromptVersion> saved = repository.findByKeyOrderByVersionNumberDesc(key);
    int currentVersion = saved.isEmpty() ? 0 : saved.get(0).getVersionNumber();
    List<VersionSummary> out = new ArrayList<>();
    for (FeasibilityPromptVersion v : saved) {
      out.add(new VersionSummary(v.getVersionNumber(), v.getCreatedAt(), v.getCreatedByName(), v.getNote(), v.getContent().length(), v.getVersionNumber() == currentVersion));
    }
    out.add(new VersionSummary(0, null, null, "Texto original del cliente", original(key).length(), currentVersion == 0));
    return out;
  }

  @Transactional(readOnly = true)
  public String versionContent(PromptKey key, int version) {
    if (version == 0) {
      return original(key);
    }
    return repository
        .findByKeyAndVersionNumber(key, version)
        .map(FeasibilityPromptVersion::getContent)
        .orElseThrow(() -> new NotFoundException("No existe la versión " + version + " de este prompt."));
  }

  /** Vuelve a una versión anterior (0 = original); queda registrado como una versión nueva. */
  public PromptView restore(PromptKey key, int version, Actor actor) {
    String content = versionContent(key, version);
    return update(key, content, version == 0 ? "Restaurado el texto original" : "Restaurada la versión " + version, actor);
  }
}
