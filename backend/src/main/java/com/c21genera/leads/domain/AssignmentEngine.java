package com.c21genera.leads.domain;

import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntUnaryOperator;

/**
 * Motor de asignación puro (sin base de datos), igual que el de Inova.
 *
 * <ol>
 *   <li>Si el lead trae una propiedad cuyo asesor en 21Online es un usuario
 *       activo de Genera, se le asigna directo (eso lo decide el servicio).
 *   <li>Si no, ruleta ponderada entre quienes reciben leads, con peso mayor a
 *       0, sin pausa vigente y sin rebasar su límite diario: gana quien tenga
 *       la menor proporción (leads de hoy / peso). Empates: mayor peso y luego
 *       al azar.
 *   <li>Horario: si hay elegibles dentro de su horario, la ruleta se limita a
 *       ellos; si nadie está en horario, sigue con todos los elegibles (el
 *       lead nunca se queda sin asesor solo por la hora).
 * </ol>
 */
public final class AssignmentEngine {

  private AssignmentEngine() {}

  public record Candidate(
      UUID userId, boolean receivesLeads, int weight, Integer dailyLimit, LocalTime shiftStart, LocalTime shiftEnd, Instant pausedUntil) {}

  public static boolean eligible(Candidate c, Instant now, int todayCount) {
    if (!c.receivesLeads() || c.weight() <= 0) {
      return false;
    }
    if (c.pausedUntil() != null && c.pausedUntil().isAfter(now)) {
      return false;
    }
    return c.dailyLimit() == null || todayCount < c.dailyLimit();
  }

  /** ¿Está dentro de su horario? Sin horario = siempre. Soporta turnos que cruzan la medianoche. */
  public static boolean inShift(Candidate c, LocalTime localTime) {
    if (c.shiftStart() == null || c.shiftEnd() == null) {
      return true;
    }
    if (c.shiftStart().isBefore(c.shiftEnd())) {
      return !localTime.isBefore(c.shiftStart()) && localTime.isBefore(c.shiftEnd());
    }
    return !localTime.isBefore(c.shiftStart()) || localTime.isBefore(c.shiftEnd());
  }

  /** @param random devuelve un índice en [0, n): inyectable para pruebas deterministas. */
  public static Optional<UUID> pickRoulette(
      List<Candidate> candidates, Map<UUID, Integer> todayCounts, Instant now, LocalTime localTime, IntUnaryOperator random) {
    List<Candidate> eligible = new ArrayList<>();
    for (Candidate c : candidates) {
      if (eligible(c, now, todayCounts.getOrDefault(c.userId(), 0))) {
        eligible.add(c);
      }
    }
    if (eligible.isEmpty()) {
      return Optional.empty();
    }
    List<Candidate> onShift = eligible.stream().filter(c -> inShift(c, localTime)).toList();
    List<Candidate> pool = onShift.isEmpty() ? eligible : onShift;

    double best = Double.MAX_VALUE;
    List<Candidate> group = new ArrayList<>();
    for (Candidate c : pool) {
      double ratio = todayCounts.getOrDefault(c.userId(), 0) / (double) c.weight();
      if (ratio < best - 1e-9) {
        best = ratio;
        group = new ArrayList<>(List.of(c));
      } else if (Math.abs(ratio - best) <= 1e-9) {
        group.add(c);
      }
    }
    int maxWeight = group.stream().mapToInt(Candidate::weight).max().orElse(0);
    List<Candidate> top =
        group.stream().filter(c -> c.weight() == maxWeight).sorted(Comparator.comparing(c -> c.userId().toString())).toList();
    return Optional.of(top.size() == 1 ? top.get(0).userId() : top.get(random.applyAsInt(top.size())).userId());
  }
}
