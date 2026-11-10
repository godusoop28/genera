package com.c21genera.shared.web;

import com.c21genera.shared.config.JvmDiagnostics;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Diagnóstico técnico de la JVM (metaspace y paquetes con más clases). Solo administrador/director. */
@RestController
class JvmDiagnosticsController {

  @GetMapping("/api/v1/internal/diagnostics/jvm")
  @PreAuthorize("hasAuthority('AUDIT_VIEW')")
  Map<String, Object> jvm() {
    return JvmDiagnostics.report();
  }
}
