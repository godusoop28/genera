package com.c21genera.feasibility.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.c21genera.feasibility.domain.FeasibilityPromptVersion;
import com.c21genera.feasibility.domain.FeasibilityPromptVersion.PromptKey;
import com.c21genera.feasibility.infrastructure.FeasibilityPromptVersionRepository;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.events.Actor;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FeasibilityPromptServiceTest {

  private final List<FeasibilityPromptVersion> store = new ArrayList<>();
  private final Actor admin = Actor.staff(UUID.randomUUID(), "Admin Genera", "ADMIN");
  private FeasibilityPromptService service;

  @BeforeEach
  void setUp() {
    FeasibilityPromptVersionRepository repo = mock(FeasibilityPromptVersionRepository.class);
    when(repo.save(any())).thenAnswer(inv -> {
      store.add(inv.getArgument(0));
      return inv.getArgument(0);
    });
    when(repo.findByKeyOrderByVersionNumberDesc(any())).thenAnswer(inv -> byKey(inv.getArgument(0)));
    when(repo.findFirstByKeyOrderByVersionNumberDesc(any())).thenAnswer(inv -> byKey(inv.getArgument(0)).stream().findFirst());
    when(repo.findByKeyAndVersionNumber(any(), anyInt()))
        .thenAnswer(inv -> byKey(inv.getArgument(0)).stream().filter(v -> v.getVersionNumber() == (int) inv.getArgument(1)).findFirst());
    service = new FeasibilityPromptService(repo, Clock.fixed(Instant.parse("2026-10-24T18:00:00Z"), ZoneOffset.UTC));
  }

  private List<FeasibilityPromptVersion> byKey(PromptKey key) {
    return store.stream().filter(v -> v.getKey() == key).sorted(Comparator.comparingInt(FeasibilityPromptVersion::getVersionNumber).reversed()).toList();
  }

  private static String prompt(String marker) {
    return "# PROMPT MAESTRO " + marker + "\n" + "Analiza el inmueble con evidencia real. ".repeat(10);
  }

  @Test
  void startsWithClientOriginalsPlusFixedSystemRules() {
    var list = service.list();
    assertThat(list).extracting(FeasibilityPromptService.PromptView::key).containsExactly(PromptKey.ANALYST, PromptKey.PRESENTATION);
    assertThat(list.get(0).original()).isTrue();
    assertThat(list.get(0).content()).startsWith("# PROMPT MAESTRO — ANALISTA DE FACTIBILIDAD 3.0").contains("Nunca sustituyas evidencia por optimismo.");
    assertThat(list.get(1).content()).contains("CENTURY 21 GENERA").contains("DIAPOSITIVA 8. MAPA DE UBICACIÓN");
    var instructions = service.instructions(PromptKey.ANALYST);
    assertThat(instructions.version()).isZero();
    assertThat(instructions.text()).contains("REGLAS DE OPERACIÓN DENTRO DEL SISTEMA CENTURY 21 GENERA");
  }

  @Test
  void editCreatesVersionUsedByTheNextFeasibility() {
    var saved = service.update(PromptKey.ANALYST, prompt("v1"), "Mejoras de comparables", admin);
    assertThat(saved.version()).isEqualTo(1);
    assertThat(saved.original()).isFalse();
    assertThat(saved.updatedByName()).isEqualTo("Admin Genera");
    var instructions = service.instructions(PromptKey.ANALYST);
    assertThat(instructions.version()).isEqualTo(1);
    assertThat(instructions.text()).startsWith("# PROMPT MAESTRO v1").contains("REGLAS DE OPERACIÓN");
    // El otro prompt no cambia.
    assertThat(service.current(PromptKey.PRESENTATION).original()).isTrue();
  }

  @Test
  void sameTextDoesNotCreateEmptyVersion() {
    service.update(PromptKey.PRESENTATION, prompt("a"), null, admin);
    service.update(PromptKey.PRESENTATION, "  " + prompt("a") + "\n", null, admin);
    assertThat(store).hasSize(1);
  }

  @Test
  void rejectsTooShortOrTooLong() {
    assertThatThrownBy(() -> service.update(PromptKey.ANALYST, "corto", null, admin)).isInstanceOf(UnprocessableException.class);
    assertThatThrownBy(() -> service.update(PromptKey.ANALYST, "x".repeat(100_001), null, admin)).isInstanceOf(UnprocessableException.class);
    assertThat(store).isEmpty();
  }

  @Test
  void historyAndRestore() {
    service.update(PromptKey.ANALYST, prompt("v1"), null, admin);
    service.update(PromptKey.ANALYST, prompt("v2"), null, admin);
    var back = service.restore(PromptKey.ANALYST, 1, admin);
    assertThat(back.version()).isEqualTo(3);
    assertThat(back.content()).startsWith("# PROMPT MAESTRO v1");
    var original = service.restore(PromptKey.ANALYST, 0, admin);
    assertThat(original.content()).startsWith("# PROMPT MAESTRO — ANALISTA DE FACTIBILIDAD 3.0");
    var versions = service.versions(PromptKey.ANALYST);
    assertThat(versions).extracting(FeasibilityPromptService.VersionSummary::version).containsExactly(4, 3, 2, 1, 0);
    assertThat(versions.get(0).current()).isTrue();
    assertThat(versions.get(0).note()).isEqualTo("Restaurado el texto original");
    assertThat(service.versionContent(PromptKey.ANALYST, 2)).startsWith("# PROMPT MAESTRO v2");
    assertThatThrownBy(() -> service.versionContent(PromptKey.ANALYST, 99)).isInstanceOf(NotFoundException.class);
  }
}
