package com.c21genera.shared.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

/**
 * Memoria real del contenedor en /actuator/info: el heap no basta para saber
 * por qué Render reinicia el servicio (E2E 02/10: 502 con el heap tranquilo).
 * rssMb es lo que usa el proceso (heap + nativa); cgroupCurrentMb/cgroupMaxMb
 * lo que cuenta el límite del contenedor. Sin dependencias: si los archivos no
 * existen (Windows, macOS) simplemente no aparecen.
 */
@Component
class ContainerMemoryInfoContributor implements InfoContributor {

  @Override
  public void contribute(Info.Builder builder) {
    Map<String, Object> memory = new LinkedHashMap<>();
    statusKb("VmRSS").ifPresent(kb -> memory.put("rssMb", kb / 1024));
    statusKb("VmHWM").ifPresent(kb -> memory.put("rssPeakMb", kb / 1024));
    bytes("/sys/fs/cgroup/memory.current", "/sys/fs/cgroup/memory/memory.usage_in_bytes").ifPresent(b -> memory.put("cgroupCurrentMb", b / 1048576));
    bytes("/sys/fs/cgroup/memory.peak", "/sys/fs/cgroup/memory/memory.max_usage_in_bytes").ifPresent(b -> memory.put("cgroupPeakMb", b / 1048576));
    bytes("/sys/fs/cgroup/memory.max", "/sys/fs/cgroup/memory/memory.limit_in_bytes").ifPresent(b -> memory.put("cgroupMaxMb", b / 1048576));
    // anon = memoria real del proceso; file = caché de archivos (temporales de carga, PDFBox), que el kernel
    // recupera antes de matar el proceso. cgroupCurrentMb suma ambas.
    cgroupStat("anon").ifPresent(b -> memory.put("cgroupAnonMb", b / 1048576));
    cgroupStat("file").ifPresent(b -> memory.put("cgroupFileMb", b / 1048576));
    if (!memory.isEmpty()) {
      builder.withDetail("container", memory);
    }
  }

  private static java.util.Optional<Long> statusKb(String key) {
    try {
      for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
        if (line.startsWith(key + ":")) {
          return java.util.Optional.of(Long.parseLong(line.replaceAll("[^0-9]", "")));
        }
      }
    } catch (Exception ignored) {
      // No es Linux: no se informa.
    }
    return java.util.Optional.empty();
  }

  private static java.util.Optional<Long> cgroupStat(String key) {
    try {
      for (String line : Files.readAllLines(Path.of("/sys/fs/cgroup/memory.stat"))) {
        if (line.startsWith(key + " ")) {
          return java.util.Optional.of(Long.parseLong(line.substring(key.length() + 1).strip()));
        }
      }
    } catch (Exception ignored) {
      // cgroup v1 o no es Linux: no se informa.
    }
    return java.util.Optional.empty();
  }

  private static java.util.Optional<Long> bytes(String... candidates) {
    for (String candidate : candidates) {
      try {
        String value = Files.readString(Path.of(candidate)).strip();
        if (!value.isEmpty() && !"max".equals(value)) {
          return java.util.Optional.of(Long.parseLong(value));
        }
      } catch (Exception ignored) {
        // Siguiente candidato (cgroup v1/v2).
      }
    }
    return java.util.Optional.empty();
  }
}
