package com.c21genera.online21.infrastructure;

import com.c21genera.online21.PropertyCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Índice en memoria del inventario en promoción de 21Online. Se recarga cada
 * {@code app.online21.refresh-interval} (3 h por defecto) y la ficha de una
 * propiedad concreta se consulta en vivo para tener precio y estatus al día.
 *
 * <p>Un solo proceso en Render: no hace falta guardar el índice en la base de
 * datos. Si 21Online no responde se conserva el último índice bueno.
 */
@Service
class Online21Catalog implements PropertyCatalog {

  private static final Logger log = LoggerFactory.getLogger(Online21Catalog.class);

  private final Online21Client client;
  private final Online21Properties properties;
  private final Clock clock;
  private final AtomicBoolean refreshing = new AtomicBoolean(false);
  private volatile List<CatalogProperty> index = List.of();
  private volatile Instant loadedAt;

  Online21Catalog(Online21Client client, Online21Properties properties, Clock clock) {
    this.client = client;
    this.properties = properties;
    this.clock = clock;
  }

  @Override
  public boolean available() {
    return client.configured();
  }

  @Scheduled(fixedDelayString = "${app.online21.refresh-interval:PT3H}", initialDelayString = "PT40S")
  void scheduledRefresh() {
    if (available()) {
      refresh();
    }
  }

  /** Recarga el índice completo (paginado). Devuelve cuántas propiedades quedaron. */
  int refresh() {
    if (!refreshing.compareAndSet(false, true)) {
      return index.size();
    }
    try {
      List<CatalogProperty> loaded = new ArrayList<>();
      for (int page = 1; page <= properties.maxPages(); page++) {
        List<JsonNode> items = client.propertiesPage(page);
        if (items.isEmpty()) {
          break;
        }
        for (JsonNode item : items) {
          loaded.add(Online21PropertyMapper.map(item, properties.publicUrlTemplate()));
        }
      }
      index = List.copyOf(loaded);
      loadedAt = clock.instant();
      log.info("Inventario de 21Online actualizado: {} propiedades en promoción", loaded.size());
    } catch (RuntimeException e) {
      // Sin datos del cuerpo: puede traer información de dueños.
      log.warn("No se pudo actualizar el inventario de 21Online ({}); se conserva el anterior ({} propiedades)", e.getClass().getSimpleName(), index.size());
    } finally {
      refreshing.set(false);
    }
    return index.size();
  }

  private List<CatalogProperty> currentIndex() {
    if (loadedAt == null && available()) {
      refresh();
    }
    return index;
  }

  @Override
  public SearchResult search(String query, Operation operation, int limit) {
    if (!available()) {
      return new SearchResult(Status.UNAVAILABLE, 0, List.of());
    }
    List<CatalogProperty> current = currentIndex();
    if (current.isEmpty()) {
      return new SearchResult(Status.UNAVAILABLE, 0, List.of());
    }
    List<CatalogProperty> found = CatalogSearch.search(current, query, operation, Math.min(limit, 5));
    return new SearchResult(found.isEmpty() ? Status.NOT_FOUND : Status.OK, current.size(), found);
  }

  @Override
  public Lookup find(String reference) {
    if (!available() || reference == null || reference.isBlank()) {
      return available() ? Lookup.notFound() : Lookup.unavailable();
    }
    String ref = reference.trim();
    if (ref.matches("\\d{5,8}(--\\d+)?")) {
      long id = Long.parseLong(ref.replaceAll("--.*", ""));
      try {
        JsonNode node = client.property(id);
        if (node == null) {
          return Lookup.notFound();
        }
        String status = Online21PropertyMapper.text(node, "status");
        if (status != null && !"enPromocion".equals(status)) {
          return Lookup.notFound(); // Cerrada, cancelada o suspendida: no se ofrece.
        }
        return Lookup.of(Online21PropertyMapper.map(node, properties.publicUrlTemplate()));
      } catch (RuntimeException e) {
        log.warn("No se pudo consultar la propiedad {} en 21Online ({})", id, e.getClass().getSimpleName());
        Optional<CatalogProperty> cached = index.stream().filter(p -> p.id() == id).findFirst();
        return cached.map(Lookup::of).orElseGet(Lookup::unavailable);
      }
    }
    List<CatalogProperty> current = currentIndex();
    if (current.isEmpty()) {
      return Lookup.unavailable();
    }
    return current.stream().filter(p -> CatalogSearch.matchesReference(p, ref)).findFirst().map(Lookup::of).orElseGet(Lookup::notFound);
  }

  @Override
  public Optional<CatalogProperty> cached(long id) {
    return index.stream().filter(p -> p.id() == id).findFirst();
  }

  @Override
  public List<String> referencesIn(String text) {
    return CatalogSearch.references(text);
  }
}
