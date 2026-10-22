package com.c21genera.online21.infrastructure;

import com.c21genera.online21.Online21Users;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** GET /api/usuarios (la llave necesita el scope "usuarios"). Nunca registra los cuerpos. */
@Service
class Online21UserDirectory implements Online21Users {

  private static final int MAX_PAGES = 20;

  private final Online21Client client;

  Online21UserDirectory(Online21Client client) {
    this.client = client;
  }

  @Override
  public boolean available() {
    return client.configured();
  }

  @Override
  public List<RemoteUser> list() {
    if (!available()) {
      return List.of();
    }
    List<RemoteUser> out = new ArrayList<>();
    for (int page = 1; page <= MAX_PAGES; page++) {
      JsonNode root = client.collection("/api/usuarios", Map.of(), page);
      List<JsonNode> members = client.membersOf(root);
      members.forEach(n -> out.add(toUser(n)));
      JsonNode view = root == null ? null : root.has("hydra:view") ? root.get("hydra:view") : root.get("view");
      boolean hasNext = view != null && (view.hasNonNull("hydra:next") || view.hasNonNull("next"));
      if (members.isEmpty() || !hasNext) {
        break;
      }
    }
    return out;
  }

  static RemoteUser toUser(JsonNode n) {
    String full = Online21PropertyMapper.text(n, "nombreCompleto");
    if (full == null) {
      full =
          String.join(
                  " ",
                  java.util.stream.Stream.of("nombre", "apellidoP", "apellidoM").map(f -> Online21PropertyMapper.text(n, f)).filter(s -> s != null).toList())
              .trim();
    }
    return new RemoteUser(
        n.path("id").asLong(),
        full,
        Online21PropertyMapper.text(n, "email"),
        Online21PropertyMapper.text(n, "puesto"),
        Online21PropertyMapper.text(n, "telMovil"),
        Online21PropertyMapper.text(n, "whatsapp"),
        Online21PropertyMapper.text(n, "telOfna"),
        photoUrl(n),
        Online21PropertyMapper.text(n, "urlUsuarioActivo"));
  }

  /**
   * fotoPath es relativo ("/uploads/649/usuarios/41388/x.jpg"); path es la base del CDN que termina en
   * ".../uploads/649/usuarios/". La foto es la base del CDN (hasta "/uploads/") + fotoPath.
   */
  static String photoUrl(JsonNode n) {
    String photo = Online21PropertyMapper.text(n, "fotoPath");
    if (photo == null) {
      return null;
    }
    if (photo.startsWith("http")) {
      return photo;
    }
    String base = Online21PropertyMapper.text(n, "path");
    int cut = base == null ? -1 : base.indexOf("/uploads/");
    if (cut < 0 || !base.startsWith("http")) {
      return null;
    }
    return base.substring(0, cut) + (photo.startsWith("/") ? photo : "/" + photo);
  }
}
