package com.c21genera;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Verifica que ningún módulo entre directamente a las clases internas
 * (domain/application/infrastructure) de otro módulo, salvo a través de su
 * API pública (paquete raíz) o de eventos (ver AGENTS §7).
 */
class ModularityTests {

  @Test
  void verifiesModularStructure() {
    ApplicationModules.of(C21generaBackendApplication.class).verify();
  }
}
