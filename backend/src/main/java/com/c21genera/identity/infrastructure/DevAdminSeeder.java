package com.c21genera.identity.infrastructure;

import com.c21genera.identity.application.UserService;
import com.c21genera.identity.domain.RoleCode;
import com.c21genera.shared.config.DevSeedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Solo perfil local (ver AGENTS §145). Crea al administrador de demostración
 * (Jorge Ricardo Jurado Espinal) únicamente si DEV_ADMIN_PASSWORD está
 * definido; nunca hay una contraseña hardcodeada.
 */
@Component
@Profile("local")
public class DevAdminSeeder implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DevAdminSeeder.class);

  private final UserService userService;
  private final UserRepository userRepository;
  private final DevSeedProperties properties;

  public DevAdminSeeder(UserService userService, UserRepository userRepository, DevSeedProperties properties) {
    this.userService = userService;
    this.userRepository = userRepository;
    this.properties = properties;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!properties.enabled()) {
      return;
    }
    if (properties.adminPassword() == null || properties.adminPassword().isBlank()) {
      log.warn("app.dev-seed.enabled=true pero DEV_ADMIN_PASSWORD no está definido: no se creará el usuario admin de demo.");
      return;
    }
    if (userRepository.existsByEmailIgnoreCase(properties.adminEmail())) {
      return;
    }
    userService.create(properties.adminName(), properties.adminEmail(), properties.adminPassword(), RoleCode.ADMINISTRATOR);
    log.info("Usuario administrador de demo creado: {}", properties.adminEmail());
  }
}
