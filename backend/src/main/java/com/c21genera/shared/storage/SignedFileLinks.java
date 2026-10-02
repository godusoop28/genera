package com.c21genera.shared.storage;

import com.c21genera.shared.config.JwtProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Ligas de descarga temporales servidas por el propio backend
 * ({@code GET /api/v1/public/files/{token}}), para archivos que el proveedor de
 * almacenamiento no puede entregar con una sola URL firmada (p. ej. un PDF de
 * 40 MB guardado en partes en Cloudinary). El token lleva la storageKey y la
 * expiración, firmados con HMAC-SHA256: no se puede alterar ni reutilizar
 * después de que vence, igual que una URL prefirmada de S3.
 */
@Component
public class SignedFileLinks {

  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

  private final byte[] key;
  private final Clock clock;
  private final String fallbackBaseUrl;

  public SignedFileLinks(
      JwtProperties jwtProperties,
      Clock clock,
      @Value("${app.public-api-base-url:${RENDER_EXTERNAL_URL:http://localhost:8080}}") String fallbackBaseUrl) {
    String secret = jwtProperties.secret();
    if (secret == null || secret.isBlank()) {
      // Sin secreto configurado (p. ej. pruebas): uno aleatorio por arranque basta para ligas de minutos.
      byte[] random = new byte[32];
      new SecureRandom().nextBytes(random);
      this.key = random;
    } else {
      this.key = ("file-links:" + secret).getBytes(StandardCharsets.UTF_8);
    }
    this.clock = clock;
    this.fallbackBaseUrl = fallbackBaseUrl.replaceAll("/+$", "");
  }

  public URI linkFor(String storageKey, Duration ttl) {
    long expiresAt = clock.instant().plus(ttl).getEpochSecond();
    String payload = ENCODER.encodeToString((expiresAt + "|" + storageKey).getBytes(StandardCharsets.UTF_8));
    String token = payload + "." + ENCODER.encodeToString(sign(payload));
    return URI.create(baseUrl() + "/api/v1/public/files/" + token);
  }

  /** storageKey del token si la firma es válida y no ha vencido. */
  public Optional<String> verify(String token) {
    int dot = token.indexOf('.');
    if (dot <= 0) {
      return Optional.empty();
    }
    String payload = token.substring(0, dot);
    byte[] signature;
    String decoded;
    try {
      signature = DECODER.decode(token.substring(dot + 1));
      decoded = new String(DECODER.decode(payload), StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
    if (!MessageDigest.isEqual(signature, sign(payload))) {
      return Optional.empty();
    }
    int bar = decoded.indexOf('|');
    if (bar <= 0) {
      return Optional.empty();
    }
    try {
      long expiresAt = Long.parseLong(decoded.substring(0, bar));
      if (clock.instant().getEpochSecond() > expiresAt) {
        return Optional.empty();
      }
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
    return Optional.of(decoded.substring(bar + 1));
  }

  private String baseUrl() {
    if (RequestContextHolder.getRequestAttributes() != null) {
      return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString().replaceAll("/+$", "");
    }
    return fallbackBaseUrl;
  }

  private byte[] sign(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo firmar la liga de descarga", e);
    }
  }
}
