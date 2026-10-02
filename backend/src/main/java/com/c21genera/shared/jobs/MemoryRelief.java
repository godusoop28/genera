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

  public static void afterHeavyWork() {
    System.gc();
    try {
      ManagementFactory.getPlatformMBeanServer()
          .invoke(
              new ObjectName("com.sun.management:type=DiagnosticCommand"),
              "systemTrimNativeHeap",
              new Object[] {new String[0]},
              new String[] {String[].class.getName()});
    } catch (Exception | LinkageError ignored) {
      // No disponible en este JVM/sistema: basta con la recolección.
    }
  }
}
