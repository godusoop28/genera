package com.c21genera.shared.config;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import javax.management.NotificationEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Si el metaspace (clases cargadas) llega al 95% de -XX:MaxMetaspaceSize, termina el proceso para que Render lo
 * reinicie. El 10/11/2026 se llenó tras 4 días: la JVM no muere con "OutOfMemoryError: Metaspace", se queda
 * encendida respondiendo 500 a todo (el login decía "No se pudo conectar con el servidor") hasta reiniciarla a
 * mano. Solo vigila el metaspace: los OOM de heap de un PDF grande se siguen atrapando por documento.
 */
@Component
class MetaspaceGuard {

  private static final Logger log = LoggerFactory.getLogger(MetaspaceGuard.class);
  private static final double THRESHOLD = 0.95;

  @EventListener(ApplicationReadyEvent.class)
  void install() {
    for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
      long max = pool.getUsage().getMax();
      if (!"Metaspace".equals(pool.getName()) || max <= 0 || !pool.isUsageThresholdSupported()) {
        continue;
      }
      pool.setUsageThreshold((long) (max * THRESHOLD));
      NotificationEmitter emitter = (NotificationEmitter) ManagementFactory.getMemoryMXBean();
      emitter.addNotificationListener((notification, handback) -> {
        if (MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED.equals(notification.getType())) {
          log.error("Metaspace al {}% de {} MB ({} clases): se reinicia el proceso", (int) (THRESHOLD * 100),
              max / 1048576, ManagementFactory.getClassLoadingMXBean().getLoadedClassCount());
          Runtime.getRuntime().halt(1);
        }
      }, null, null);
      log.info("MetaspaceGuard activo: reinicio al {}% de {} MB", (int) (THRESHOLD * 100), max / 1048576);
    }
  }
}
