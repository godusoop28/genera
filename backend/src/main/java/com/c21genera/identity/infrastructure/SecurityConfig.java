package com.c21genera.identity.infrastructure;

import com.c21genera.shared.config.CorsProperties;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Dos superficies HTTP separadas (ver AGENTS §15):
 * - /api/v1/public/**: sin autenticación Spring Security; la liga (token en
 *   la URL) es la credencial y cada caso de uso valida su propio alcance.
 * - /api/v1/internal/**: JWT Bearer obligatorio + permisos por método.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  private final PermissionAuthoritiesConverter authoritiesConverter;
  private final CorsProperties corsProperties;

  public SecurityConfig(PermissionAuthoritiesConverter authoritiesConverter, CorsProperties corsProperties) {
    this.authoritiesConverter = authoritiesConverter;
    this.corsProperties = corsProperties;
  }

  @Bean
  @Order(1)
  public SecurityFilterChain publicApiFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher(
            "/api/v1/public/**", "/api/v1/auth/**", "/actuator/health/**", "/swagger-ui/**", "/v3/api-docs/**")
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(
            headers ->
                headers.referrerPolicy(
                    referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    return http.build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain internalApiFilterChain(HttpSecurity http, CorsConfigurationSource corsConfigurationSource)
      throws Exception {
    http.securityMatcher("/**")
        .csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/**").permitAll().anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(authoritiesConverter)));
    return http.build();
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(corsProperties.allowedOrigins());
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-ID"));
    configuration.setExposedHeaders(List.of("X-Correlation-ID"));
    configuration.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
