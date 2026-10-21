package com.c21genera.online21.infrastructure;

import com.c21genera.online21.PropertyListings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Inventario y alta de propiedades en 21Online para la pantalla interna. Las
 * consultas van en vivo (21Online es la fuente de verdad); el alta usa el
 * esquema "Propiedades-propiedades.create" (estado y pais obligatorios). Nunca
 * registra los cuerpos: pueden traer datos de dueños.
 */
@Service
class Online21Listings implements PropertyListings {

  private static final Logger log = LoggerFactory.getLogger(Online21Listings.class);

  private final Online21Client client;
  private final Online21Catalog catalog;
  private final ObjectMapper objectMapper;

  Online21Listings(Online21Client client, Online21Catalog catalog, ObjectMapper objectMapper) {
    this.client = client;
    this.catalog = catalog;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean available() {
    return client.configured();
  }

  @Override
  public ListingPage list(ListingFilter filter) {
    if (!available()) {
      return new ListingPage(List.of(), 1, 0, false);
    }
    Map<String, String> params = new LinkedHashMap<>();
    String q = filter.query() == null ? "" : filter.query().trim();
    if (q.matches("\\d{4,8}(--\\d+)?")) {
      params.put("id", q.replaceAll("--.*", ""));
    } else if (!q.isEmpty()) {
      // Búsqueda por ubicación de 21Online (colonia, municipio o calle).
      params.put("match_ubicacion", q);
    }
    putIfPresent(params, "status", filter.status());
    putIfPresent(params, "tipoOperacion", filter.operation());
    putIfPresent(params, "subtipoPropiedad", filter.subtype());
    putIfPresent(params, "municipio", filter.municipality());
    int page = Math.max(1, filter.page());
    JsonNode root = client.listingsPage(params, page);
    List<Listing> items = client.membersOf(root).stream().map(Online21ListingMapper::toListing).toList();
    Integer total = root != null && root.has("hydra:totalItems") ? root.get("hydra:totalItems").asInt() : root != null && root.has("totalItems") ? root.get("totalItems").asInt() : null;
    JsonNode view = root == null ? null : root.has("hydra:view") ? root.get("hydra:view") : root.get("view");
    boolean hasNext = view != null && (view.hasNonNull("hydra:next") || view.hasNonNull("next"));
    return new ListingPage(items, page, total, hasNext);
  }

  @Override
  public Optional<Listing> get(long id) {
    if (!available()) {
      return Optional.empty();
    }
    JsonNode node = client.property(id);
    return node == null ? Optional.empty() : Optional.of(Online21ListingMapper.toListing(node));
  }

  @Override
  public long create(ListingDraft draft) {
    requireAvailable();
    try {
      long id = client.createProperty(Online21ListingMapper.toPayload(draft));
      refreshCatalogLater();
      return id;
    } catch (RuntimeException e) {
      throw translate(e, "dar de alta");
    }
  }

  @Override
  public void update(long id, ListingDraft draft) {
    requireAvailable();
    try {
      client.updateProperty(id, Online21ListingMapper.toPayload(draft));
      refreshCatalogLater();
    } catch (RuntimeException e) {
      throw translate(e, "actualizar");
    }
  }

  @Override
  public void update(long id, ListingDraft draft, ListingDraft baseline) {
    requireAvailable();
    Map<String, Object> changes = baseline == null ? Online21ListingMapper.toPayload(draft) : Online21ListingMapper.changes(baseline, draft);
    if (changes.isEmpty()) {
      return;
    }
    try {
      client.updateProperty(id, changes);
      refreshCatalogLater();
    } catch (RuntimeException e) {
      throw translate(e, "actualizar");
    }
  }

  @Override
  public Optional<RemoteListing> editable(long id) {
    if (!available()) {
      return Optional.empty();
    }
    JsonNode node = client.property(id);
    if (node == null) {
      return Optional.empty();
    }
    return Optional.of(new RemoteListing(id, Online21ListingMapper.toDraft(node), instant(node.get("fechaModificacion"))));
  }

  /** Consulta ligera (solo id y fecha) en lotes de 30 con el filtro id[] de 21Online. */
  @Override
  public Map<Long, Instant> modifiedAt(Collection<Long> ids) {
    Map<Long, Instant> out = new HashMap<>();
    if (!available() || ids.isEmpty()) {
      return out;
    }
    List<Long> all = new ArrayList<>(ids);
    for (int from = 0; from < all.size(); from += 30) {
      List<Long> batch = all.subList(from, Math.min(all.size(), from + 30));
      JsonNode root = client.modifiedAtPage(batch);
      for (JsonNode n : client.membersOf(root)) {
        Instant at = instant(n.get("fechaModificacion"));
        if (n.hasNonNull("id") && at != null) {
          out.put(n.get("id").asLong(), at);
        }
      }
    }
    return out;
  }

  @Override
  public void cancel(long id) {
    requireAvailable();
    try {
      client.updateProperty(id, Map.of("status", "cancelada"));
      refreshCatalogLater();
    } catch (RuntimeException e) {
      throw translate(e, "cancelar");
    }
  }

  private static Instant instant(JsonNode value) {
    if (value == null || value.isNull()) {
      return null;
    }
    try {
      return OffsetDateTime.parse(value.asText()).toInstant();
    } catch (RuntimeException e) {
      return null;
    }
  }

  private void requireAvailable() {
    if (!available()) {
      throw new PublishException("La integración con 21Online no está configurada (falta ONLINE21_API_KEY).", false);
    }
  }

  /** El bot de WhatsApp ve la propiedad nueva sin esperar las 3 h del índice. */
  private void refreshCatalogLater() {
    Thread.ofVirtual().name("online21-refresh").start(catalog::refresh);
  }

  private PublishException translate(RuntimeException e, String action) {
    if (e instanceof PublishException p) {
      return p;
    }
    if (e instanceof RestClientResponseException r) {
      int status = r.getStatusCode().value();
      log.warn("21Online rechazó {} una propiedad (HTTP {})", action, status);
      if (status == 401 || status == 403) {
        return new PublishException("21Online rechazó la llave de la API (HTTP " + status + "). Revisa ONLINE21_API_KEY y sus permisos.", false);
      }
      String detail = null;
      try {
        detail = Online21ListingMapper.violations(objectMapper.readTree(r.getResponseBodyAsString()));
      } catch (java.io.IOException | RuntimeException ignored) {
        // Cuerpo no JSON: se usa el mensaje genérico.
      }
      if (status >= 500) {
        return new PublishException("21Online tuvo un error (HTTP " + status + ")" + (detail != null ? ": " + detail : "") + ". Revisa en 21Online si se guardó antes de reintentar.", true);
      }
      return new PublishException("21Online no aceptó la propiedad" + (detail != null ? ": " + detail : " (HTTP " + status + ")."), false);
    }
    if (e instanceof ResourceAccessException) {
      log.warn("21Online no respondió al {} una propiedad ({})", action, e.getClass().getSimpleName());
      return new PublishException("21Online no respondió a tiempo. Revisa en 21Online si se guardó antes de reintentar.", true);
    }
    log.warn("No se pudo {} una propiedad en 21Online ({})", action, e.getClass().getSimpleName());
    return new PublishException("No se pudo " + action + " la propiedad en 21Online.", false);
  }

  private static void putIfPresent(Map<String, String> params, String key, String value) {
    if (value != null && !value.isBlank()) {
      params.put(key, value.trim());
    }
  }
}
