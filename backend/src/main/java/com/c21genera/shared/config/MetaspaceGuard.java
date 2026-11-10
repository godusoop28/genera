package com.c21genera.shared.config;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.management.NotificationEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Vigila el metaspace (clases cargadas). El 10/11/2026 se llenó tras 4 días con el tope de 160 MB: la JVM no muere
 * con "OutOfMemoryError: Metaspace", se queda encendida respondiendo error a todo (el login decía "No se pudo
 * conectar con el servidor") hasta reiniciarla a mano.
 *
 * <ul>
 *   <li>Cada hora deja en el log una línea con metaspace y clases: la tendencia se ve en los logs de Render.</li>
 *   <li>Al 80% escribe en el log el informe completo (qué paquetes ocupan las clases) para encontrar una fuga.</li>
 *   <li>Al 95% termina el proceso y Render lo reinicia solo.</li>
 * </ul>
 *
 * Solo vigila el metaspace: los OOM de heap de un PDF grande se siguen atrapando por documento.
 */
@Component
class MetaspaceGuard {

  private static final Logger log = LoggerFactory.getLogger(MetaspaceGuard.class);
  private static final double WARN = 0.80;
  private static final double HALT = 0.95;

  private final AtomicBoolean warned = new AtomicBoolean();

  @EventListener(ApplicationReadyEvent.class)
  void install() {
    MemoryPoolMXBean metaspace = metaspacePool();
    if (metaspace == null) {
      return;
    }
    long max = metaspace.getUsage().getMax();
    // La JVM avisa una vez al cruzar el umbral; check() lo sube al 95% después del primer aviso.
    metaspace.setUsageThreshold((long) (max * WARN));
    NotificationEmitter emitter = (NotificationEmitter) ManagementFactory.getMemoryMXBean();
    emitter.addNotificationListener((notification, handback) -> {
      if (MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED.equals(notification.getType())) {
        check();
      }
    }, null, null);
    log.info("MetaspaceGuard activo: informe al {}% y reinicio al {}% de {} MB; ahora {}", (int) (WARN * 100),
        (int) (HALT * 100), max / 1048576, JvmDiagnostics.summary());
  }

  /** Cada hora: la tendencia en el log y la misma revisión de umbrales, por si el aviso de la JVM se perdió. */
  @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1H")
  void hourly() {
    log.info("JVM: {}", JvmDiagnostics.summary());
    check();
  }

  private synchronized void check() {
    MemoryPoolMXBean metaspace = metaspacePool();
    if (metaspace == null) {
      return;
    }
    long used = metaspace.getUsage().getUsed();
    long max = metaspace.getUsage().getMax();
    if (used >= max * HALT) {
      log.error("Metaspace al {}% de {} MB: se reinicia el proceso. {}", used * 100 / max, max / 1048576,
          JvmDiagnostics.report());
      Runtime.getRuntime().halt(1);
    }
    if (used >= max * WARN && warned.compareAndSet(false, true)) {
      log.warn("Metaspace al {}% de {} MB. Informe: {}", used * 100 / max, max / 1048576, JvmDiagnostics.report());
      metaspace.setUsageThreshold((long) (max * HALT));
    }
  }

  private static MemoryPoolMXBean metaspacePool() {
    for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
      if ("Metaspace".equals(pool.getName()) && pool.getUsage().getMax() > 0 && pool.isUsageThresholdSupported()) {
        return pool;
      }
    }
    return null;
  }
}
