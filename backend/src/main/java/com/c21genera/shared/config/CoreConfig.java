package com.c21genera.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableJpaAuditing
public class CoreConfig {

  /** Reloj inyectable en UTC (ver AGENTS §121). Permite fijar el tiempo en tests. */
  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * Spring Boot 4.1 usa Jackson 3 (tools.jackson.databind) por defecto para
   * los HttpMessageConverters de Spring MVC; este bean, de la línea clásica
   * com.fasterxml.jackson (ya presente transitivamente vía springdoc), es
   * para el uso interno propio del backend (serializar snapshots de
   * contrato, manifiestos de páginas, payloads de background_job, etc.) y
   * no compite con la serialización HTTP de los controladores.
   */
  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper().registerModule(new JavaTimeModule());
  }
}
