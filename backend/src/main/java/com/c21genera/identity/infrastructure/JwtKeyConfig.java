package com.c21genera.identity.infrastructure;

import com.c21genera.shared.config.JwtProperties;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * El backend emite y valida sus propios JWT (no delega a un IdP externo),
 * por lo que usamos una clave simétrica (HS256). {@code JWT_SECRET} es
 * obligatorio fuera de local/test: si está vacío el arranque falla aquí
 * mismo con un mensaje claro, en vez de arrancar inseguro.
 */
@Configuration
public class JwtKeyConfig {

  @Bean
  public SecretKey jwtSecretKey(JwtProperties properties) {
    if (properties.secret() == null || properties.secret().isBlank()) {
      throw new IllegalStateException(
          "JWT_SECRET no está configurado. Define la variable de entorno JWT_SECRET (mínimo 32 caracteres) antes de arrancar.");
    }
    byte[] bytes = properties.secret().getBytes(StandardCharsets.UTF_8);
    if (bytes.length < 32) {
      throw new IllegalStateException("JWT_SECRET debe tener al menos 32 bytes (256 bits) para HS256.");
    }
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean
  public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
    return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
  }

  @Bean
  public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
    return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
  }
}
