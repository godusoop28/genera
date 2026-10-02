package com.c21genera.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.shared.config.JwtProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SignedFileLinksTest {

  private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

  private SignedFileLinks links(Instant now) {
    return new SignedFileLinks(
        new JwtProperties("issuer", Duration.ofMinutes(15), Duration.ofDays(7), "secreto-de-prueba-0123456789abcdef"),
        Clock.fixed(now, ZoneOffset.UTC),
        "https://api.example");
  }

  private static String tokenOf(java.net.URI uri) {
    String path = uri.getPath();
    return path.substring(path.lastIndexOf('/') + 1);
  }

  @Test
  void aValidLinkResolvesToItsStorageKey() {
    String token = tokenOf(links(NOW).linkFor("expedientes/1/escritura.pdf", Duration.ofMinutes(10)));

    assertThat(links(NOW.plusSeconds(60)).verify(token)).contains("expedientes/1/escritura.pdf");
  }

  @Test
  void anExpiredOrTamperedLinkIsRejected() {
    String token = tokenOf(links(NOW).linkFor("expedientes/1/escritura.pdf", Duration.ofMinutes(10)));

    assertThat(links(NOW.plus(Duration.ofMinutes(11))).verify(token)).isEmpty();
    assertThat(links(NOW).verify("x" + token)).isEmpty();
    assertThat(links(NOW).verify(token.substring(0, token.indexOf('.')) + ".firmafalsa")).isEmpty();
  }
}
