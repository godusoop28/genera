package com.c21genera.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.assistant.domain.AssistantContract;
import com.c21genera.assistant.domain.AssistantPolicy;
import com.c21genera.assistant.domain.AssistantPolicy.Fact;
import com.c21genera.assistant.domain.AssistantPolicy.Intent;
import com.c21genera.assistant.domain.AssistantPolicy.Trigger;
import com.c21genera.assistant.domain.Burst;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssistantPolicyTest {

  @Test
  void providersAndManagementNeverEnterTheRoulette() {
    assertThat(AssistantPolicy.decideHandoff(Intent.PROVIDER, List.of(), false, Trigger.ASSISTANT).allowed()).isFalse();
    assertThat(AssistantPolicy.decideHandoff(Intent.MANAGEMENT, List.of(), true, Trigger.HUMAN_REQUEST).allowed()).isFalse();
    // Proveedor que además quiere comprar: sí.
    assertThat(AssistantPolicy.decideHandoff(Intent.PROVIDER, List.of(Intent.BUY), false, Trigger.ASSISTANT).allowed()).isTrue();
  }

  @Test
  void needsRealEstateInterestOrIdentifiedProperty() {
    assertThat(AssistantPolicy.decideHandoff(Intent.UNKNOWN, List.of(), false, Trigger.ASSISTANT).allowed()).isFalse();
    assertThat(AssistantPolicy.decideHandoff(Intent.BUY, List.of(), false, Trigger.ASSISTANT).allowed()).isTrue();
    assertThat(AssistantPolicy.decideHandoff(Intent.AGENT_COLLABORATION, List.of(), false, Trigger.ASSISTANT).allowed()).isFalse();
    assertThat(AssistantPolicy.decideHandoff(Intent.AGENT_COLLABORATION, List.of(), true, Trigger.ASSISTANT).allowed()).isTrue();
    assertThat(AssistantPolicy.decideHandoff(Intent.HUMAN_REQUEST, List.of(), true, Trigger.HUMAN_REQUEST).allowed()).isTrue();
  }

  @Test
  void factsMergeRespectsCorrectionsAndDeclines() {
    Map<String, Fact> previous = Map.of("zone", new Fact("Temixco", "known"), "budget_max", new Fact(null, "declined"));
    Map<String, Fact> merged =
        AssistantPolicy.mergeFacts(
            previous,
            Map.of(
                "zone", new Fact("Jiutepec", "known"),
                "budget_max", new Fact(null, "unknown"),
                "bedrooms", new Fact("3", "known"),
                "operation", new Fact("  ", "known")));
    assertThat(merged.get("zone").value()).isEqualTo("Jiutepec");
    assertThat(merged.get("budget_max").status()).isEqualTo("declined");
    assertThat(merged.get("bedrooms").value()).isEqualTo("3");
    assertThat(merged).doesNotContainKey("operation");
  }

  @Test
  void detectsUnconfirmedAssignmentClaimsAndInternalTerms() {
    assertThat(AssistantPolicy.claimsAssignment("Listo, ya te asigné con Aivy")).isTrue();
    assertThat(AssistantPolicy.claimsAssignment("Un asesor te contactará pronto")).isTrue();
    assertThat(AssistantPolicy.claimsAssignment("¿En qué zona te gustaría vivir?")).isFalse();
    assertThat(AssistantPolicy.mentionsInternalProcess("Te tocó por la ruleta")).isTrue();
    assertThat(AssistantPolicy.stripInternalProcess("Quedó con Eva Alanis (elegida por la ruleta). Saludos.")).doesNotContain("ruleta");
  }

  @Test
  void burstWaitsForSilenceButNeverMoreThanMaxWait() {
    Instant t0 = Instant.parse("2026-10-12T10:00:00Z");
    Duration debounce = Duration.ofSeconds(6);
    Duration maxWait = Duration.ofSeconds(25);
    var first = Burst.schedule(t0, null, debounce, maxWait);
    assertThat(first.processAfter()).isEqualTo(t0.plusSeconds(6));
    var later = Burst.schedule(t0.plusSeconds(22), first.burstStartedAt(), debounce, maxWait);
    assertThat(later.processAfter()).isEqualTo(t0.plusSeconds(25));
    assertThat(later.burstStartedAt()).isEqualTo(t0);
  }

  @Test
  void outputSchemaIsStrictAndComplete() {
    @SuppressWarnings("unchecked")
    List<String> required = (List<String>) AssistantContract.OUTPUT_SCHEMA.get("required");
    assertThat(required).contains("reply", "facts", "property_refs", "summary");
    assertThat(AssistantContract.TOOLS).hasSize(AssistantContract.TOOL_NAMES.size());
  }
}
