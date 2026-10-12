package com.c21genera.leads.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ronda de asignación pura (sin base de datos). Quién participa lo decide el
 * servicio con el calendario (en turno habilitado, sin ausencia, sin pausa,
 * bajo su límite); aquí solo se elige entre los elegibles y se calcula el
 * nuevo estado de la ronda, que el servicio guarda en la base de datos para
 * que no se reinicie con un reinicio del servidor.
 *
 * <ul>
 *   <li>EQUAL (equitativa): le toca a quien recibió hace más tiempo (o nunca);
 *       empate por id para que el orden sea estable.
 *   <li>WEIGHTED (ponderada): "smooth weighted round robin". Cada vez, a cada
 *       elegible se le suma su peso; gana el mayor acumulado y se le resta la
 *       suma de pesos. Con pesos 10 y 5 la secuencia es A, B, A, A, B, A… —
 *       proporcional y sin rachas largas.
 * </ul>
 */
public final class AssignmentEngine {

  private AssignmentEngine() {}

  public enum Mode {
    EQUAL,
    WEIGHTED
  }

  public record Candidate(UUID userId, int weight, Instant roundLastAt, int roundCurrentWeight) {}

  /** Elegido y estado de la ronda que hay que guardar (solo de los candidatos). */
  public record Pick(UUID userId, Map<UUID, Integer> newCurrentWeights) {}

  public static Optional<Pick> pick(Mode mode, List<Candidate> candidates) {
    if (candidates.isEmpty()) {
      return Optional.empty();
    }
    if (mode == Mode.EQUAL) {
      Candidate chosen =
          candidates.stream()
              .min(Comparator.comparing((Candidate c) -> c.roundLastAt() == null ? Instant.MIN : c.roundLastAt()).thenComparing(c -> c.userId().toString()))
              .orElseThrow();
      return Optional.of(new Pick(chosen.userId(), Map.of()));
    }
    List<Candidate> weighted = candidates.stream().filter(c -> c.weight() > 0).toList();
    if (weighted.isEmpty()) {
      return Optional.empty();
    }
    int total = weighted.stream().mapToInt(Candidate::weight).sum();
    Map<UUID, Integer> current = new HashMap<>();
    Candidate best = null;
    int bestValue = Integer.MIN_VALUE;
    for (Candidate c : weighted.stream().sorted(Comparator.comparing(c -> c.userId().toString())).toList()) {
      int value = c.roundCurrentWeight() + c.weight();
      current.put(c.userId(), value);
      if (value > bestValue) {
        bestValue = value;
        best = c;
      }
    }
    current.put(best.userId(), bestValue - total);
    return Optional.of(new Pick(best.userId(), current));
  }
}
