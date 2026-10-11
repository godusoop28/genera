package com.c21genera.assistant.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Agrupación de mensajes consecutivos ("ráfagas"), como en Inova: cada
 * mensaje nuevo empuja la respuesta a ahora + debounce, pero nunca más allá
 * de inicio de la ráfaga + maxWait. Así quien escribe en varios mensajes
 * recibe una sola respuesta, y quien no para de escribir la recibe a más
 * tardar maxWait después de su primer mensaje.
 */
public final class Burst {

  private Burst() {}

  public record Schedule(Instant processAfter, Instant burstStartedAt) {}

  public static Schedule schedule(Instant now, Instant burstStartedAt, Duration debounce, Duration maxWait) {
    Instant start = burstStartedAt != null ? burstStartedAt : now;
    Duration cap = maxWait.compareTo(debounce) < 0 ? debounce : maxWait;
    Instant byDebounce = now.plus(debounce);
    Instant byCap = start.plus(cap);
    return new Schedule(byDebounce.isBefore(byCap) ? byDebounce : byCap, start);
  }
}
