package com.c21genera.shared.jobs;

import java.lang.management.ManagementFactory;
import javax.management.ObjectName;

/**
 * Devuelve memoria al sistema después de un trabajo pesado (procesar o analizar
 * con IA un documento). Medido en producción (E2E 02/10, /actuator/info): tras
 * analizar un PDF de 38.9 MB el proceso se quedaba en ~486 MB de los 512 del
 * contenedor aun en reposo, porque el JVM conserva el heap ya comprometido y
 * glibc la memoria nativa liberada. Con ese "piso" el siguiente archivo grande
 * apenas tenía margen.
 *
 * <ul>
 *   <li>Una recolección completa: con MaxHeapFreeRatio (Dockerfile) el heap se
 *       encoge y le regresa al sistema lo que no usa.
 *   <li>System.trim_native_heap: le pide a glibc que regrese la memoria nativa
 *       libre. Se invoca por JMX y, si el JVM no lo soporta, no pasa nada (una
 *       opción de arranque desconocida, en cambio, impediría que el servidor
 *       arranque).
 * </ul>
 *
 * Los trabajos corren de uno en uno (spring.task.scheduling.pool.size=1), así
 * que la pausa (décimas de segundo) no detiene a ningún otro trabajo.
 */
public final class MemoryRelief {

  private MemoryRelief() {}

  /** Resultado de la última liberación, para /actuator/info (diagnóstico sin acceso a los logs). */
  private static volatile String lastRun = "nunca";

  public static String lastRun() {
    return lastRun;
  }

  public static void afterHeavyWork() {
    long start = System.nanoTime();
    long before = committedHeapMb();
    // HotSpot encoge el heap de forma gradual entre recolecciones completas sucesivas (0%, 10%, 40%,
    // 100% de lo que sobra) para no oscilar: con una sola, 199 MB solo bajaban a 161 (E2E 02/10).
    for (int i = 0; i < 4; i++) {
      System.gc();
    }
    String trim = "sin trim";
    try {
      ManagementFactory.getPlatformMBeanServer()
          .invoke(
              new ObjectName("com.sun.management:type=DiagnosticCommand"),
              "systemTrimNativeHeap",
              new Object[] {new String[0]},
              new String[] {String[].class.getName()});
      trim = "trim ok";
    } catch (Exception | LinkageError e) {
      // No disponible en este JVM/sistema: basta con la recolección.
      trim = "trim no disponible (" + e.getClass().getSimpleName() + ")";
    }
    lastRun =
        "%s: heap comprometido %d -> %d MB en %d ms, %s"
            .formatted(java.time.Instant.now(), before, committedHeapMb(), (System.nanoTime() - start) / 1_000_000, trim);
  }

  private static long committedHeapMb() {
    return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getCommitted() / 1048576;
  }
}
