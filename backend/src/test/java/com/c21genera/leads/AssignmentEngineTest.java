package com.c21genera.leads;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.leads.domain.AssignmentEngine;
import com.c21genera.leads.domain.AssignmentEngine.Candidate;
import com.c21genera.leads.domain.Lead;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssignmentEngineTest {

  private static final Instant NOW = Instant.parse("2026-10-12T17:00:00Z");
  private static final LocalTime NOON = LocalTime.of(11, 0);
  private final UUID a = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private final UUID b = UUID.fromString("00000000-0000-0000-0000-00000000000b");
  private final UUID c = UUID.fromString("00000000-0000-0000-0000-00000000000c");

  private Candidate cand(UUID id, int weight) {
    return new Candidate(id, true, weight, null, null, null, null);
  }

  @Test
  void picksLowestLeadsPerWeight() {
    // a: 4/10 = 0.4 ; b: 1/5 = 0.2 → b
    var pick = AssignmentEngine.pickRoulette(List.of(cand(a, 10), cand(b, 5)), Map.of(a, 4, b, 1), NOW, NOON, n -> 0);
    assertThat(pick).contains(b);
  }

  @Test
  void higherWeightReceivesProportionallyMore() {
    Map<UUID, Integer> counts = new java.util.HashMap<>();
    List<Candidate> pool = List.of(cand(a, 10), cand(b, 5));
    for (int i = 0; i < 30; i++) {
      UUID chosen = AssignmentEngine.pickRoulette(pool, counts, NOW, NOON, n -> 0).orElseThrow();
      counts.merge(chosen, 1, Integer::sum);
    }
    assertThat(counts.get(a)).isEqualTo(20);
    assertThat(counts.get(b)).isEqualTo(10);
  }

  @Test
  void tieBreaksByHigherWeight() {
    var pick = AssignmentEngine.pickRoulette(List.of(cand(a, 5), cand(b, 8)), Map.of(), NOW, NOON, n -> 0);
    assertThat(pick).contains(b);
  }

  @Test
  void skipsInactivePausedZeroWeightAndFullDailyLimit() {
    List<Candidate> pool =
        List.of(
            new Candidate(a, false, 10, null, null, null, null),
            new Candidate(b, true, 10, null, null, null, NOW.plusSeconds(3600)),
            new Candidate(c, true, 10, 2, null, null, null));
    assertThat(AssignmentEngine.pickRoulette(pool, Map.of(c, 2), NOW, NOON, n -> 0)).isEmpty();
    assertThat(AssignmentEngine.pickRoulette(List.of(cand(a, 0)), Map.of(), NOW, NOON, n -> 0)).isEmpty();
  }

  @Test
  void prefersAdvisorsOnShiftButFallsBackToEveryoneAtNight() {
    Candidate morning = new Candidate(a, true, 5, null, LocalTime.of(9, 0), LocalTime.of(15, 0), null);
    Candidate evening = new Candidate(b, true, 5, null, LocalTime.of(15, 0), LocalTime.of(21, 0), null);
    assertThat(AssignmentEngine.pickRoulette(List.of(morning, evening), Map.of(), NOW, LocalTime.of(16, 0), n -> 0)).contains(b);
    assertThat(AssignmentEngine.pickRoulette(List.of(morning, evening), Map.of(), NOW, LocalTime.of(10, 0), n -> 0)).contains(a);
    // A las 23:00 nadie está en horario: la ruleta sigue con todos.
    assertThat(AssignmentEngine.pickRoulette(List.of(morning, evening), Map.of(), NOW, LocalTime.of(23, 0), n -> 0)).isPresent();
  }

  @Test
  void shiftCanCrossMidnight() {
    Candidate night = new Candidate(a, true, 5, null, LocalTime.of(20, 0), LocalTime.of(2, 0), null);
    assertThat(AssignmentEngine.inShift(night, LocalTime.of(23, 30))).isTrue();
    assertThat(AssignmentEngine.inShift(night, LocalTime.of(1, 0))).isTrue();
    assertThat(AssignmentEngine.inShift(night, LocalTime.of(12, 0))).isFalse();
  }

  @Test
  void phoneDigitsIgnoreFormatAndCountryCode() {
    assertThat(Lead.digitsOf("+52 1 777 555 0142")).isEqualTo("7775550142");
    assertThat(Lead.digitsOf("5217775550142")).isEqualTo("7775550142");
    assertThat(Lead.digitsOf("777-555-0142")).isEqualTo("7775550142");
    assertThat(Lead.digitsOf("  ")).isNull();
  }
}
