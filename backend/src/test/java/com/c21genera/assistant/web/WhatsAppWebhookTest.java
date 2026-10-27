package com.c21genera.assistant.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.assistant.infrastructure.WhatsAppCloudClient;
import com.c21genera.assistant.infrastructure.WhatsAppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WhatsAppWebhookTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void extractsTextFromEachMessageType() throws Exception {
    assertThat(WhatsAppWebhookController.textOf(mapper.readTree("{\"type\":\"text\",\"text\":{\"body\":\"Hola\"}}"))).isEqualTo("Hola");
    assertThat(WhatsAppWebhookController.textOf(mapper.readTree("{\"type\":\"interactive\",\"interactive\":{\"button_reply\":{\"title\":\"Sí\"}}}"))).isEqualTo("Sí");
    assertThat(WhatsAppWebhookController.textOf(mapper.readTree("{\"type\":\"image\",\"image\":{\"caption\":\"esta casa\"}}"))).isEqualTo("[El cliente envió una imagen] esta casa");
    assertThat(WhatsAppWebhookController.textOf(mapper.readTree("{\"type\":\"sticker\"}"))).isNull();
  }

  @Test
  void validatesMetaSignature() throws Exception {
    WhatsAppProperties props = new WhatsAppProperties(null, null, null, "verify-me", "app-secret", null, null, null);
    WhatsAppCloudClient client = newClient(props);
    byte[] body = "{\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec("app-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    String good = "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
    assertThat(client.validSignature(body, good)).isTrue();
    assertThat(client.validSignature(body, "sha256=00")).isFalse();
    assertThat(client.validSignature(body, null)).isFalse();
    assertThat(client.verifyTokenMatches("verify-me")).isTrue();
    assertThat(client.verifyTokenMatches("otro")).isFalse();
  }

  @Test
  void rejectsEverythingWithoutAppSecret() throws Exception {
    WhatsAppCloudClient client = newClient(new WhatsAppProperties(null, null, null, null, null, null, null, null));
    assertThat(client.validSignature("{}".getBytes(StandardCharsets.UTF_8), "sha256=abc")).isFalse();
    assertThat(client.verifyTokenMatches("")).isFalse();
  }

  private WhatsAppCloudClient newClient(WhatsAppProperties props) throws Exception {
    var ctor = WhatsAppCloudClient.class.getDeclaredConstructor(WhatsAppProperties.class, ObjectMapper.class);
    ctor.setAccessible(true);
    return ctor.newInstance(props, mapper);
  }
}
