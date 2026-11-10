package com.c21genera.shared.config;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import javax.management.ObjectName;

/**
 * Qué ocupa el metaspace, leído de la propia JVM (lo mismo que {@code jcmd VM.classloader_stats} y
 * {@code VM.classloaders}, que en Render no se pueden correr). Sirve para distinguir una fuga de clases
 * (un paquete o las clases ocultas crecen día tras día) del calentamiento normal de una app con lazy-init.
 */
public final class JvmDiagnostics {

  private static final long MB = 1048576;
  private static final Pattern CLASS_NAME = Pattern.compile("[\\w$.]+(/0x[0-9a-fA-F]+)?");

  private JvmDiagnostics() {}

  /** Números de una línea: metaspace, code cache y clases cargadas/descargadas desde el arranque. */
  public static Map<String, Object> summary() {
    Map<String, Object> out = new LinkedHashMap<>();
    for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
      String name = pool.getName();
      String key = "Metaspace".equals(name) ? "metaspace" : name.startsWith("CodeHeap") || "CodeCache".equals(name) ? "codeCache" : null;
      if (key == null) {
        continue;
      }
      out.merge(key + "UsedMb", pool.getUsage().getUsed() / MB, (a, b) -> (Long) a + (Long) b);
      long max = pool.getUsage().getMax();
      if (max > 0) {
        out.merge(key + "MaxMb", max / MB, (a, b) -> (Long) a + (Long) b);
      }
    }
    ClassLoadingMXBean classes = ManagementFactory.getClassLoadingMXBean();
    out.put("loadedClasses", classes.getLoadedClassCount());
    // Si totalLoaded crece mucho más que loaded, se generan y descargan clases sin parar (proxies, lambdas).
    out.put("totalLoadedClasses", classes.getTotalLoadedClassCount());
    out.put("unloadedClasses", classes.getUnloadedClassCount());
    return out;
  }

  /** Informe completo: resumen, estadísticas por classloader y los paquetes con más clases cargadas. */
  public static Map<String, Object> report() {
    Map<String, Object> out = new LinkedHashMap<>(summary());
    out.put("classLoaderStats", diagnosticCommand("vmClassloaderStats"));
    out.put("topPackages", topPackages(40));
    return out;
  }

  /** Paquetes (3 primeros niveles) con más clases; las clases ocultas (lambdas, proxies) van aparte. */
  static Map<String, Integer> topPackages(int limit) {
    String listing = diagnosticCommand("vmClassloaders", "show-classes=true");
    Map<String, Integer> counts = new HashMap<>();
    for (String line : listing.split("\n")) {
      String trimmed = line.strip();
      String name = trimmed.substring(trimmed.lastIndexOf(' ') + 1);
      if (name.isEmpty() || name.startsWith("[") || name.indexOf('.') < 0 || !CLASS_NAME.matcher(name).matches()) {
        continue;
      }
      boolean hidden = name.contains("/0x");
      String[] parts = name.split("\\.");
      StringBuilder pkg = new StringBuilder(hidden ? "(oculta) " : "");
      for (int i = 0; i < Math.min(3, parts.length - 1); i++) {
        pkg.append(i > 0 ? "." : "").append(parts[i]);
      }
      counts.merge(pkg.toString(), 1, Integer::sum);
    }
    Map<String, Integer> top = new LinkedHashMap<>();
    counts.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .limit(limit)
        .forEach(e -> top.put(e.getKey(), e.getValue()));
    return top;
  }

  private static String diagnosticCommand(String operation, String... args) {
    try {
      Object result = ManagementFactory.getPlatformMBeanServer().invoke(
          new ObjectName("com.sun.management:type=DiagnosticCommand"), operation,
          new Object[] {args}, new String[] {String[].class.getName()});
      return String.valueOf(result);
    } catch (Exception e) {
      return "no disponible: " + e.getMessage();
    }
  }
}
