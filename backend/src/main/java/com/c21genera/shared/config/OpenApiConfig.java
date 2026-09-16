package com.c21genera.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Documentación OpenAPI/Swagger UI (ver AGENTS §108): declara el esquema bearer JWT usado por /api/v1/internal/**. */
@Configuration
public class OpenApiConfig {

  private static final String BEARER_SCHEME = "bearerAuth";

  @Bean
  public OpenAPI c21generaOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("CENTURY 21 Genera - Módulo 1 API")
                .description(
                    "API interna (JWT) y pública (liga con token) del Módulo 1. "
                        + "Prototipo: los datos legales mostrados no sustituyen la asesoría de CENTURY 21 Genera.")
                .version("v1"))
        .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
        .components(
            new Components()
                .addSecuritySchemes(
                    BEARER_SCHEME,
                    new SecurityScheme()
                        .name(BEARER_SCHEME)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }
}
