package com.c21genera.leads;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.leads.domain.AssignmentEngine;
import com.c21genera.leads.domain.AssignmentEngine.Candidate;
import com.c21genera.leads.domain.AssignmentEngine.Mode;
import com.c21genera.leads.domain.AssignmentEngine.Pick;
import com.c21genera.leads.domain.Lead;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssignmentEngineTest {

  private final UUID a = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private final UUID b = UUID.fromString("00000000-0000-0000-0000-00000000000b");
  private final UUID c = UUID.fromString("00000000-0000-0000-0000-00000000000c");

  /** Simula la base de datos: el estado de la ronda sobrevive entre llamadas (y reinicios). */
  private final Map<UUID, Instant> lastAt = new HashMap<>();
  private final Map<UUID, Integer> current = new HashMap<>();

  private UUID next(Mode mode, Map<UUID, Integer> weights, Instant now) {
    List<Candidate> candidates = new ArrayList<>();
    weights.forEach((id, w) -> candidates.add(new Candidate(id, w, lastAt.get(id), current.getOrDefault(id, 0))));
    Pick pick = AssignmentEngine.pick(mode, candidates).orElseThrow();
    lastAt.put(pick.userId(), now);
    current.putAll(pick.newCurrentWeights());
    return pick.userId();
  }

  @Test
  void equalRoundRotatesInOrder() {
    Map<UUID, Integer> w = Map.of(a, 5, b, 5, c, 5);
    List<UUID> order = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      order.add(next(Mode.EQUAL, w, Instant.parse("2026-10-12T15:00:00Z").plusSeconds(i)));
    }
    assertThat(order).containsExactly(a, b, c, a, b, c);
  }

  @Test
  void equalRoundSkipsWhoIsNotAvailableAndResumesFairly() {
    next(Mode.EQUAL, Map.of(a, 5, b, 5), Instant.parse("2026-10-12T15:00:00Z")); // a
    // b sale de turno: solo a disponible.
    assertThat(next(Mode.EQUAL, Map.of(a, 5), Instant.parse("2026-10-12T15:01:00Z"))).isEqualTo(a);
    // b regresa: le toca a b (nunca ha recibido).
    assertThat(next(Mode.EQUAL, Map.of(a, 5, b, 5), Instant.parse("2026-10-12T15:02:00Z"))).isEqualTo(b);
  }

  @Test
  void weightedRoundIsProportionalWithoutLongStreaks() {
    Map<UUID, Integer> w = Map.of(a, 10, b, 5);
    Map<UUID, Integer> counts = new HashMap<>();
    List<UUID> sequence = new ArrayList<>();
    for (int i = 0; i < 30; i++) {
      UUID chosen = next(Mode.WEIGHTED, w, Instant.parse("2026-10-12T15:00:00Z").plusSeconds(i));
      counts.merge(chosen, 1, Integer::sum);
      sequence.add(chosen);
    }
    assertThat(counts.get(a)).isEqualTo(20);
    assertThat(counts.get(b)).isEqualTo(10);
    assertThat(sequence.subList(0, 3)).containsExactly(a, b, a);
  }

  @Test
  void nobodyEligibleMeansNoPick() {
    assertThat(AssignmentEngine.pick(Mode.EQUAL, List.of())).isEmpty();
    assertThat(AssignmentEngine.pick(Mode.WEIGHTED, List.of(new Candidate(a, 0, null, 0)))).isEmpty();
  }

  @Test
  void phoneDigitsIgnoreFormatAndCountryCode() {
    assertThat(Lead.digitsOf("+52 1 777 555 0142")).isEqualTo("7775550142");
    assertThat(Lead.digitsOf("5217775550142")).isEqualTo("7775550142");
    assertThat(Lead.digitsOf("777-555-0142")).isEqualTo("7775550142");
    assertThat(Lead.digitsOf("  ")).isNull();
  }
}
