package com.c21genera.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.util.concurrent.ThreadFactory;
import org.springframework.boot.task.ThreadPoolTaskSchedulerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
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

  /**
   * Los trabajos de fondo (procesar archivos, leer con IA, correos) corren con prioridad baja: con
   * media CPU, mientras la IA preparaba imágenes las pantallas se "trababan" (29/10). El Dockerfile
   * activa -XX:ThreadPriorityPolicy=1 para que Linux respete la prioridad; sin esa opción no cambia nada.
   */
  @Bean
  public ThreadPoolTaskSchedulerCustomizer lowPrioritySchedulerThreads() {
    return scheduler -> scheduler.setThreadPriority(Thread.MIN_PRIORITY);
  }

  /** Hilos de prioridad baja para los trabajos de fondo con su propio pool. */
  public static ThreadFactory backgroundThreads(String prefix) {
    CustomizableThreadFactory factory = new CustomizableThreadFactory(prefix);
    factory.setThreadPriority(Thread.MIN_PRIORITY);
    return factory;
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
