package com.c21genera.leads.application;

import com.c21genera.identity.CurrentUser;
import com.c21genera.leads.domain.AdvisorProfile;
import com.c21genera.leads.domain.CrmProperty;
import com.c21genera.leads.domain.CrmProperty.Data;
import com.c21genera.leads.domain.SyncStatus;
import com.c21genera.leads.infrastructure.AdvisorProfileRepository;
import com.c21genera.leads.infrastructure.CrmPropertyRepository;
import com.c21genera.online21.ListingAttributes;
import com.c21genera.online21.PropertyListings;
import com.c21genera.online21.PropertyListings.Listing;
import com.c21genera.online21.PropertyListings.ListingDraft;
import com.c21genera.online21.PropertyListings.ListingFilter;
import com.c21genera.online21.PropertyListings.ListingPage;
import com.c21genera.online21.PropertyListings.PublishException;
import com.c21genera.online21.PropertyListings.RemoteListing;
import com.c21genera.propertycatalog.PropertyCatalog;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Propiedades del CRM vinculadas con 21Online. El inventario se consulta en
 * vivo en 21Online; lo que se da de alta aquí se guarda primero en Genera y
 * enseguida se manda a 21Online (fuera de la transacción, para no tenerla
 * abierta durante la llamada). Si la integración está apagada queda
 * PENDIENTE y se manda sola en cuanto se configure la llave; si 21Online la
 * rechaza o no responde queda FALLIDA con el motivo y se reintenta a mano
 * (un reintento automático tras un tiempo agotado podría duplicarla allá).
 *
 * <p>Editar: quien la capturó, su asesor o quien ve todos los leads.
 *
 * <p>En ambos sentidos: una propiedad dada de alta en 21Online se vincula al
 * editarla o darla de baja desde aquí; lo que se cambie allá se trae cada
 * pocos minutos (si aquí no hay cambios sin mandar) y al editar aquí solo se
 * mandan los campos que cambiaron, para no pisar lo editado allá.
 */
@Service
public class PropertyService {

  private static final Logger log = LoggerFactory.getLogger(PropertyService.class);

  /** Subtipos que 21Online acepta en México por tipo de propiedad (ver su esquema). */
  static final Map<String, List<String>> SUBTYPES =
      Map.of(
          "habitacional",
          List.of("casa", "casa-duplex", "casa-en-condominio", "departamento", "penthouse", "cabana", "terreno", "huerta", "quinta", "rancho", "hacienda", "edificio"),
          "comercial",
          List.of("local", "oficinas", "bodega", "nave", "fabrica", "terreno", "edificio", "fraccionamiento", "escuela", "inmueble-productivo"));

  /** "cerrada" no se ofrece: el cierre en 21Online pide sus datos de cierre y se registra allá. */
  static final Set<String> STATUSES = Set.of("enPromocion", "suspendida", "cancelada", "conIntencion", "conContratoFirmado");

  private static final String WAITING = "La integración con 21Online no está configurada; se enviará en cuanto se configure la llave.";

  private final CrmPropertyRepository properties;
  private final AdvisorProfileRepository profiles;
  private final PropertyListings listings;
  private final StaffNames staff;
  private final PropertyCatalog catalog;
  private final TransactionTemplate tx;
  private final Clock clock;
  private final ObjectMapper objectMapper;
  private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

  PropertyService(
      CrmPropertyRepository properties,
      AdvisorProfileRepository profiles,
      PropertyListings listings,
      StaffNames staff,
      PropertyCatalog catalog,
      TransactionTemplate tx,
      Clock clock,
      ObjectMapper objectMapper) {
    this.properties = properties;
    this.profiles = profiles;
    this.listings = listings;
    this.staff = staff;
    this.catalog = catalog;
    this.tx = tx;
    this.clock = clock;
    this.objectMapper = objectMapper;
  }

  public record Person(UUID id, String name) {}

  public record PropertyView(
      UUID id,
      Long online21Id,
      String syncStatus,
      String syncError,
      boolean syncUncertain,
      Instant syncedAt,
      Data data,
      Person advisor,
      boolean advisorLinked,
      Person createdBy,
      Instant createdAt,
      Instant updatedAt,
      boolean canEdit,
      // Control de Cambios #26: "Habitacional → Casa en condominio" (null si se capturó antes del catálogo).
      String propertyTypeLabel,
      // GENERA (capturada aquí) u ONLINE21 (dada de alta allá y vinculada); cuándo se trajo lo de 21Online.
      String origin,
      Instant remoteModifiedAt,
      // Fotos de 21Online (solo lectura).
      List<String> photos,
      // Notas internas y etiquetas: solo en Genera, nunca se mandan a 21Online.
      String privateNotes,
      List<String> tags,
      // CC#36: fecha de alta (fechaAlta de 21Online o, si no hay, la de captura en Genera).
      Instant listedAt) {}

  /** Resultado de importar el inventario de la oficina. */
  public record ImportResult(boolean configured, int imported, int updated, int total) {}

  public record InventoryItem(Listing listing, Person advisor, UUID generaId) {}

  public record InventoryPage(boolean configured, String error, List<InventoryItem> items, int page, Integer totalItems, boolean hasNext) {}

  public record Overview(boolean configured, List<PropertyView> captured) {}

  /** CC#36: criterio principal de orden del listado (solo uno a la vez). */
  public enum CapturedSort {
    NEWEST,
    OLDEST,
    PRICE_ASC,
    PRICE_DESC
  }

  /**
   * CC#36: filtros del listado "En Genera" (todo el inventario de la oficina). Los precios se comparan
   * en la moneda indicada (MXN si no se indica) y contra el precio de la operación (venta o renta).
   */
  public record CapturedFilter(
      String query,
      String tag,
      String status,
      String operation,
      UUID advisorId,
      BigDecimal minPrice,
      BigDecimal maxPrice,
      String currency,
      CapturedSort sort,
      int page,
      int size) {}

  /** page empieza en 1; tags: todas las etiquetas existentes (para el filtro). */
  public record CapturedPage(
      boolean configured, List<PropertyView> captured, int page, int size, long totalItems, int totalPages, List<String> tags) {}

  // ---------------------------------------------------------------------------
  // Consulta
  // ---------------------------------------------------------------------------

  public InventoryPage inventory(ListingFilter filter) {
    if (!listings.available()) {
      return new InventoryPage(false, null, List.of(), 1, 0, false);
    }
    ListingPage page;
    try {
      page = listings.list(filter);
    } catch (RuntimeException e) {
      log.warn("No se pudo consultar el inventario de 21Online ({})", e.getClass().getSimpleName());
      return new InventoryPage(true, "21Online no respondió. Intenta de nuevo en un momento.", List.of(), Math.max(1, filter.page()), null, false);
    }
    Map<Long, UUID> local =
        page.items().isEmpty()
            ? Map.of()
            : properties.findByOnline21IdIn(page.items().stream().map(Listing::id).toList()).stream()
                .collect(Collectors.toMap(CrmProperty::getOnline21Id, CrmProperty::getId, (a, b) -> a));
    AdvisorLookup advisors = new AdvisorLookup();
    List<InventoryItem> items = page.items().stream().map(l -> new InventoryItem(l, advisors.of(l.advisorOnline21Id()), local.get(l.id()))).toList();
    return new InventoryPage(true, null, items, page.page(), page.totalItems(), page.hasNext());
  }

  public InventoryItem inventoryItem(long online21Id) {
    if (!listings.available()) {
      throw new UnprocessableException("ONLINE21_NOT_CONFIGURED", "La integración con 21Online no está configurada.");
    }
    Optional<Listing> listing;
    try {
      listing = listings.get(online21Id);
    } catch (RuntimeException e) {
      throw new UnprocessableException("ONLINE21_UNAVAILABLE", "21Online no respondió. Intenta de nuevo en un momento.");
    }
    Listing l = listing.orElseThrow(() -> new NotFoundException("Propiedad de 21Online", online21Id));
    UUID local = properties.findByOnline21IdIn(List.of(online21Id)).stream().findFirst().map(CrmProperty::getId).orElse(null);
    return new InventoryItem(l, new AdvisorLookup().of(l.advisorOnline21Id()), local);
  }

  public Overview captured(CurrentUser user) {
    Map<UUID, String> names = staff.names();
    List<PropertyView> list = properties.findTop200ByOrderByCreatedAtDesc().stream().map(p -> view(p, user, names)).toList();
    return new Overview(listings.available(), list);
  }

  public PropertyView get(UUID id, CurrentUser user) {
    return view(load(id), user, staff.names());
  }

  /**
   * CC#36: listado paginado con filtros y orden aplicados en la base de datos, sobre todas las
   * propiedades (no solo la página visible).
   */
  public CapturedPage search(CapturedFilter f, CurrentUser user) {
    if (f.minPrice() != null && f.minPrice().signum() < 0 || f.maxPrice() != null && f.maxPrice().signum() < 0) {
      throw new UnprocessableException("INVALID_PRICE_RANGE", "Los precios no pueden ser negativos.");
    }
    if (f.minPrice() != null && f.maxPrice() != null && f.minPrice().compareTo(f.maxPrice()) > 0) {
      throw new UnprocessableException("INVALID_PRICE_RANGE", "El precio mínimo no puede ser mayor que el máximo.");
    }
    Map<UUID, String> names = staff.names();
    int size = Math.min(Math.max(f.size(), 1), 100);
    int page = Math.max(f.page(), 1);
    Page<CrmProperty> result = properties.findAll(specification(f, names), PageRequest.of(page - 1, size));
    List<PropertyView> items = result.getContent().stream().map(p -> view(p, user, names)).toList();
    return new CapturedPage(listings.available(), items, page, size, result.getTotalElements(), result.getTotalPages(), allTags());
  }

  private List<String> allTags() {
    java.util.TreeSet<String> out = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    for (String json : properties.allTagsJson()) {
      try {
        out.addAll(java.util.Arrays.asList(objectMapper.readValue(json, String[].class)));
      } catch (Exception e) {
        // Etiquetas ilegibles: se omiten del filtro.
      }
    }
    return List.copyOf(out);
  }

  static Specification<CrmProperty> specification(CapturedFilter f, Map<UUID, String> names) {
    return (root, query, cb) -> {
      List<Predicate> where = new java.util.ArrayList<>();
      Expression<BigDecimal> price =
          cb.<BigDecimal>selectCase()
              .when(cb.equal(root.get("operation"), "renta"), root.<BigDecimal>get("rentPrice"))
              .otherwise(root.<BigDecimal>get("salePrice"));
      String q = f.query() == null ? "" : f.query().trim().toLowerCase(Locale.ROOT);
      if (!q.isEmpty()) {
        String like = "%" + escapeLike(q) + "%";
        List<UUID> advisors =
            names.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().toLowerCase(Locale.ROOT).contains(q))
                .map(Map.Entry::getKey)
                .toList();
        List<Predicate> any = new java.util.ArrayList<>();
        any.add(cb.like(cb.lower(root.get("headline")), like, '!'));
        any.add(cb.like(cb.lower(root.get("neighborhood")), like, '!'));
        any.add(cb.like(cb.lower(root.get("municipality")), like, '!'));
        any.add(cb.like(cb.lower(cb.coalesce(root.<String>get("tagsJson"), "")), like, '!'));
        if (q.matches("\\d{1,12}")) {
          any.add(cb.equal(root.get("online21Id"), Long.parseLong(q)));
        }
        if (!advisors.isEmpty()) {
          any.add(root.get("advisorUserId").in(advisors));
        }
        where.add(cb.or(any.toArray(Predicate[]::new)));
      }
      if (f.tag() != null && !f.tag().isBlank()) {
        // Coincidencia exacta del elemento dentro del arreglo JSON.
        String element = "\"" + f.tag().strip().replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        where.add(cb.like(root.<String>get("tagsJson"), "%" + escapeLike(element) + "%", '!'));
      }
      if (f.status() != null && !f.status().isBlank()) {
        where.add(cb.equal(root.get("status"), f.status()));
      }
      if (f.operation() != null && !f.operation().isBlank()) {
        where.add(cb.equal(root.get("operation"), f.operation()));
      }
      if (f.advisorId() != null) {
        where.add(cb.equal(root.get("advisorUserId"), f.advisorId()));
      }
      boolean priceFilter = f.minPrice() != null || f.maxPrice() != null;
      String currency =
          f.currency() != null && !f.currency().isBlank() ? f.currency().trim().toUpperCase(Locale.ROOT) : priceFilter ? "MXN" : null;
      if (currency != null) {
        where.add(cb.equal(cb.upper(root.get("currency")), currency));
      }
      // Límites inclusivos.
      if (f.minPrice() != null) {
        where.add(cb.greaterThanOrEqualTo(price, f.minPrice()));
      }
      if (f.maxPrice() != null) {
        where.add(cb.lessThanOrEqualTo(price, f.maxPrice()));
      }
      // La consulta de conteo no lleva orden.
      if (query.getResultType() != Long.class && query.getResultType() != long.class) {
        Expression<Instant> listed = cb.coalesce(root.<Instant>get("listedAt"), root.<Instant>get("createdAt"));
        CapturedSort sort = f.sort() == null ? CapturedSort.NEWEST : f.sort();
        List<jakarta.persistence.criteria.Order> order = new java.util.ArrayList<>();
        switch (sort) {
          case OLDEST -> order.add(cb.asc(listed));
          case PRICE_ASC, PRICE_DESC -> {
            // Sin precio al final; sin filtro de moneda, agrupadas por moneda.
            order.add(cb.asc(cb.<Integer>selectCase().when(cb.isNull(price), 1).otherwise(0)));
            if (currency == null) {
              order.add(cb.asc(root.get("currency")));
            }
            order.add(sort == CapturedSort.PRICE_ASC ? cb.asc(price) : cb.desc(price));
            order.add(cb.desc(listed));
          }
          default -> order.add(cb.desc(listed));
        }
        order.add(cb.asc(root.get("id")));
        query.orderBy(order);
      }
      return cb.and(where.toArray(Predicate[]::new));
    };
  }

  private static String escapeLike(String s) {
    return s.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }

  // ---------------------------------------------------------------------------
  // Alta, edición y envío a 21Online
  // ---------------------------------------------------------------------------

  public PropertyView create(Data input, CurrentUser user) {
    Data data = normalize(input, user);
    UUID id = tx.execute(s -> properties.save(new CrmProperty(user.id(), data)).getId());
    sync(id);
    return get(id, user);
  }

  /** Notas internas y etiquetas: no se sincronizan con 21Online. */
  public PropertyView annotate(UUID id, String notes, List<String> tags, CurrentUser user) {
    tx.executeWithoutResult(
        s -> {
          CrmProperty p = load(id);
          requireEditable(p, user);
          p.annotate(notes, tags(tags));
        });
    return get(id, user);
  }

  static List<String> tags(List<String> in) {
    if (in == null) {
      return List.of();
    }
    Map<String, String> unique = new LinkedHashMap<>();
    for (String t : in) {
      String tag = t == null ? "" : t.strip().replaceAll("\\s+", " ");
      if (!tag.isEmpty()) {
        unique.putIfAbsent(tag.toLowerCase(Locale.ROOT), tag);
      }
    }
    return List.copyOf(unique.values());
  }

  public PropertyView update(UUID id, Data input, CurrentUser user) {
    tx.executeWithoutResult(
        s -> {
          CrmProperty p = load(id);
          requireEditable(p, user);
          Data data = normalize(input, user, p.getAdvisorUserId(), p.data().catalogSubtypeId(), p.data().status());
          p.apply(data);
        });
    sync(id);
    return get(id, user);
  }

  /** Reintento manual (FALLIDA o PENDIENTE). */
  public PropertyView retry(UUID id, CurrentUser user) {
    CrmProperty p = load(id);
    requireEditable(p, user);
    if (p.getSyncStatus() == SyncStatus.DONE) {
      return get(id, user);
    }
    sync(id);
    return get(id, user);
  }

  /** Manda a 21Online las que quedaron pendientes porque la integración estaba apagada. */
  @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT2M")
  void sendPending() {
    if (!listings.available()) {
      return;
    }
    for (CrmProperty p : properties.findTop25BySyncStatusOrderByCreatedAtAsc(SyncStatus.PENDING)) {
      sync(p.getId());
    }
  }

  /** Una sola llamada a la vez por propiedad (la petición y el proceso programado no se pisan). */
  void sync(UUID id) {
    if (!inFlight.add(id)) {
      return;
    }
    try {
      CrmProperty p = load(id);
      if (!listings.available()) {
        tx.executeWithoutResult(s -> load(id).markWaiting(WAITING));
        return;
      }
      if (p.getOnline21Id() != null && p.getFeaturesTemplate() == null) {
        p = withTemplate(id, p);
      }
      ListingDraft draft = draft(p);
      ListingDraft baseline = baselineOf(p);
      if (p.getOnline21Id() != null && p.getFeaturesTemplate() == null) {
        // Sin el caracteristicasJSON de 21Online no se mandan amenidades: se perderían sus demás campos.
        draft = withoutAmenities(draft);
        baseline = baseline == null ? null : withoutAmenities(baseline);
      }
      Long createdId = null;
      String error = null;
      boolean uncertain = false;
      try {
        if (p.getOnline21Id() == null) {
          createdId = listings.create(draft);
        } else {
          // Solo lo que cambió respecto de lo último que se vio/mandó (sin baseline: todo, como antes).
          listings.update(p.getOnline21Id(), draft, baseline);
        }
      } catch (PublishException e) {
        error = e.getMessage();
        uncertain = e.uncertain();
      }
      Long savedId = createdId;
      String savedError = error;
      boolean savedUncertain = uncertain;
      ListingDraft sent = draft;
      try {
        tx.executeWithoutResult(
            s -> {
              CrmProperty fresh = load(id);
              if (savedError == null) {
                fresh.markSynced(savedId, clock.instant(), json(sent));
              } else {
                fresh.markFailed(savedError, savedUncertain);
              }
            });
      } catch (OptimisticLockingFailureException e) {
        // Se editó mientras se enviaba: la edición la dejó PENDIENTE y se manda en la siguiente pasada
        // (como actualización si 21Online ya le dio id).
        if (savedId != null) {
          tx.executeWithoutResult(s -> load(id).linkOnline21(savedId));
        }
      }
    } finally {
      inFlight.remove(id);
    }
  }

  // ---------------------------------------------------------------------------
  // Propiedades dadas de alta en 21Online: vincular, dar de baja y traer cambios
  // ---------------------------------------------------------------------------

  /**
   * Vincula una propiedad de 21Online para editarla desde Genera (si ya estaba vinculada, la devuelve).
   * Puede quien ve todos los leads o el asesor de la propiedad.
   */
  public PropertyView link(long online21Id, CurrentUser user) {
    Optional<CrmProperty> existing = properties.findByOnline21IdIn(List.of(online21Id)).stream().findFirst();
    if (existing.isPresent()) {
      requireEditable(existing.get(), user);
      return get(existing.get().getId(), user);
    }
    RemoteListing remote = fetchRemote(online21Id);
    if (!user.hasPermission("PROPERTY_MANAGE")
        || !(user.hasPermission("LEAD_VIEW_ALL") || isOwnListing(remote.draft().advisorOnline21Id(), user.id()))) {
      throw new AccessDeniedException("Solo el asesor de la propiedad o un directivo puede editarla desde Genera.");
    }
    UUID id;
    try {
      id =
          tx.execute(
              s -> {
                CrmProperty p = CrmProperty.linked(user.id(), online21Id, fromRemote(remote.draft(), null), remote.photos(), remote.updatedAt(), clock.instant());
                p.recordListedAt(remote.listedAt());
                p.featuresTemplate(templateOf(remote));
                p.baseline(json(draft(p)));
                return properties.save(p).getId();
              });
    } catch (DataIntegrityViolationException e) {
      // Otra persona la vinculó al mismo tiempo.
      id = properties.findByOnline21IdIn(List.of(online21Id)).stream().findFirst().map(CrmProperty::getId).orElseThrow(() -> e);
    }
    return get(id, user);
  }

  /**
   * "Eliminar": si ya está en 21Online se cancela allá (su API no borra propiedades) y queda como
   * cancelada; si nunca llegó a 21Online se borra de Genera. Devuelve null si se borró.
   */
  public PropertyView remove(UUID id, CurrentUser user) {
    CrmProperty p = load(id);
    requireEditable(p, user);
    if (p.getOnline21Id() == null) {
      if (p.isSyncUncertain()) {
        throw new UnprocessableException(
            "PROPERTY_UNCERTAIN", "21Online no respondió al darla de alta y pudo haberse creado allá. Revisa en 21Online antes de eliminarla.");
      }
      tx.executeWithoutResult(s -> properties.delete(load(id)));
      return null;
    }
    if (!listings.available()) {
      throw new UnprocessableException("ONLINE21_NOT_CONFIGURED", "La integración con 21Online no está configurada.");
    }
    if (!inFlight.add(id)) {
      throw new UnprocessableException("PROPERTY_BUSY", "Se está enviando a 21Online en este momento. Intenta de nuevo en unos segundos.");
    }
    try {
      try {
        listings.cancel(p.getOnline21Id());
      } catch (PublishException e) {
        throw new UnprocessableException("ONLINE21_REJECTED", e.getMessage());
      }
      tx.executeWithoutResult(
          s -> {
            CrmProperty fresh = load(id);
            fresh.markCancelled(json(draft(fresh, "cancelada")), clock.instant());
          });
    } finally {
      inFlight.remove(id);
    }
    return get(id, user);
  }

  /** Dar de baja desde el inventario una propiedad de 21Online (se vincula y se cancela). */
  public PropertyView removeRemote(long online21Id, CurrentUser user) {
    PropertyView linked = link(online21Id, user);
    return remove(linked.id(), user);
  }

  /**
   * Importa a mano todo el inventario de la oficina (el proceso programado hace lo mismo cada pocos
   * minutos). Lanza si 21Online no responde.
   */
  public ImportResult importOfficeInventory() {
    if (!listings.available()) {
      return new ImportResult(false, 0, 0, 0);
    }
    InventorySync r;
    try {
      r = syncInventory();
    } catch (RuntimeException e) {
      log.warn("No se pudo importar el inventario de 21Online ({})", e.getClass().getSimpleName());
      throw new UnprocessableException("ONLINE21_UNAVAILABLE", "21Online no respondió. Intenta de nuevo en un momento.");
    }
    return new ImportResult(true, r.imported(), r.updated(), r.seen().size());
  }

  private record InventorySync(Set<Long> seen, int imported, int updated) {}

  /**
   * Todo el inventario de la oficina queda en Genera: lo nuevo en 21Online se importa y lo que cambió allá
   * se trae (con la misma regla que una propiedad vinculada: si aquí hay cambios sin mandar, se conservan).
   */
  private InventorySync syncInventory() {
    List<RemoteListing> remote = listings.officeInventory();
    Map<Long, CrmProperty> local =
        remote.isEmpty()
            ? Map.of()
            : properties.findByOnline21IdIn(remote.stream().map(RemoteListing::id).toList()).stream()
                .collect(Collectors.toMap(CrmProperty::getOnline21Id, p -> p, (a, b) -> a));
    Set<Long> seen = new java.util.HashSet<>();
    int imported = 0;
    int updated = 0;
    for (RemoteListing r : remote) {
      seen.add(r.id());
      CrmProperty p = local.get(r.id());
      try {
        if (p == null) {
          if (importNew(r)) {
            imported++;
          }
        } else if (changedRemotely(p, r) || missingRemoteData(p, r)) {
          if (applyRemote(p.getId(), r)) {
            updated++;
          }
        }
      } catch (RuntimeException e) {
        log.warn("No se pudo importar de 21Online la propiedad {} ({})", r.id(), e.getClass().getSimpleName());
      }
    }
    if (imported > 0 || updated > 0) {
      log.info("Inventario de 21Online: {} propiedad(es) nuevas y {} actualizadas", imported, updated);
    }
    recordListingDates(remote, local);
    return new InventorySync(seen, imported, updated);
  }

  /** CC#36: fechaAlta de 21Online para ordenar por antigüedad (también en las que no cambiaron allá). */
  private void recordListingDates(List<RemoteListing> remote, Map<Long, CrmProperty> local) {
    List<RemoteListing> pending =
        remote.stream()
            .filter(r -> r.listedAt() != null && local.containsKey(r.id()) && !r.listedAt().equals(local.get(r.id()).getListedAt()))
            .toList();
    if (pending.isEmpty()) {
      return;
    }
    try {
      tx.executeWithoutResult(s -> pending.forEach(r -> properties.updateListedAt(local.get(r.id()).getId(), r.listedAt())));
    } catch (RuntimeException e) {
      log.warn("No se pudieron guardar las fechas de alta de 21Online ({})", e.getClass().getSimpleName());
    }
  }

  /** Trae de 21Online la plantilla de amenidades de una propiedad vinculada antes de guardarlas; si falla, sigue sin ella. */
  private CrmProperty withTemplate(UUID id, CrmProperty p) {
    try {
      String template = listings.editable(p.getOnline21Id()).map(PropertyService::templateOf).orElse(null);
      if (template != null) {
        tx.executeWithoutResult(s -> load(id).featuresTemplate(template));
        return load(id);
      }
    } catch (RuntimeException e) {
      log.warn("No se pudo traer de 21Online la plantilla de amenidades de {} ({})", p.getOnline21Id(), e.getClass().getSimpleName());
    }
    return p;
  }

  private static ListingDraft withoutAmenities(ListingDraft d) {
    Map<String, Object> extras = new java.util.TreeMap<>(d.extras());
    extras.remove(ListingAttributes.AMENITIES_KEY);
    extras.remove(ListingAttributes.FEATURES_TEMPLATE);
    return new ListingDraft(
        d.operation(), d.propertyType(), d.subtype(), d.contractType(), d.status(), d.headline(), d.description(), d.salePrice(), d.rentPrice(),
        d.currency(), d.street(), d.number(), d.neighborhood(), d.municipality(), d.state(), d.postalCode(), d.hideStreet(), d.hideNumber(),
        d.hidePrice(), d.bedrooms(), d.bathrooms(), d.halfBathrooms(), d.parking(), d.landM2(), d.builtM2(), d.levels(), d.age(), d.maintenanceFee(),
        d.commission(), d.publishOnline(), d.advisorOnline21Id(), d.integratorId(), extras);
  }

  private static String templateOf(RemoteListing r) {
    return r.draft().extras().get(ListingAttributes.FEATURES_TEMPLATE) instanceof String s ? s : null;
  }

  private static boolean changedRemotely(CrmProperty p, RemoteListing r) {
    return r.updatedAt() != null && (p.getRemoteModifiedAt() == null || r.updatedAt().isAfter(p.getRemoteModifiedAt()));
  }

  /** Vinculadas antes de sincronizar fotos y atributos adicionales: se completan una vez aunque no cambien allá. */
  private static boolean missingRemoteData(CrmProperty p, RemoteListing r) {
    return (p.getPhotoUrls().isEmpty() && !r.photos().isEmpty())
        || (ListingAttributes.writeOnly(p.data().extras()).size() == p.data().extras().size() && !r.draft().extras().isEmpty());
  }

  /** Propiedad de 21Online que aún no está en Genera; queda a nombre de su asesor (si está vinculado). */
  private boolean importNew(RemoteListing r) {
    try {
      tx.executeWithoutResult(
          s -> {
            Data data = fromRemote(r.draft(), null);
            CrmProperty p = CrmProperty.linked(data.advisorUserId(), r.id(), data, r.photos(), r.updatedAt(), clock.instant());
            p.recordListedAt(r.listedAt());
            p.featuresTemplate(templateOf(r));
            p.baseline(json(draft(p)));
            properties.save(p);
          });
      return true;
    } catch (DataIntegrityViolationException e) {
      // Alguien la vinculó al mismo tiempo.
      return false;
    }
  }

  /**
   * Trae a Genera lo que se cambió en 21Online: importa el inventario de la oficina y revisa las vinculadas
   * que ya no aparecen en él (canceladas, cerradas…).
   */
  @Scheduled(fixedDelayString = "${app.online21.pull-interval:PT10M}", initialDelayString = "PT3M")
  void pullRemoteChanges() {
    if (!listings.available()) {
      return;
    }
    Set<Long> seen = Set.of();
    try {
      seen = syncInventory().seen();
    } catch (RuntimeException e) {
      log.warn("No se pudo importar el inventario de 21Online ({})", e.getClass().getSimpleName());
    }
    Set<Long> inInventory = seen;
    List<CrmProperty> linked = properties.findByOnline21IdNotNull().stream().filter(p -> !inInventory.contains(p.getOnline21Id())).toList();
    if (linked.isEmpty()) {
      return;
    }
    Map<Long, Instant> remote;
    try {
      remote = listings.modifiedAt(linked.stream().map(CrmProperty::getOnline21Id).toList());
    } catch (RuntimeException e) {
      log.warn("No se pudieron consultar los cambios de 21Online ({})", e.getClass().getSimpleName());
      return;
    }
    int refreshed = 0;
    for (CrmProperty p : linked) {
      Instant at = remote.get(p.getOnline21Id());
      if (at != null && (p.getRemoteModifiedAt() == null || at.isAfter(p.getRemoteModifiedAt()))) {
        try {
          refresh(p.getId());
          refreshed++;
        } catch (RuntimeException e) {
          log.warn("No se pudo traer de 21Online la propiedad {} ({})", p.getOnline21Id(), e.getClass().getSimpleName());
        }
      }
    }
    if (refreshed > 0) {
      log.info("Se trajeron de 21Online los cambios de {} propiedad(es)", refreshed);
    }
  }

  /** Trae la ficha de 21Online; si aquí hay cambios sin mandar, se conservan y se mandan (solo los campos tocados). */
  void refresh(UUID id) {
    if (!inFlight.add(id)) {
      return;
    }
    try {
      CrmProperty p = load(id);
      if (p.getOnline21Id() == null) {
        return;
      }
      Optional<RemoteListing> remote = listings.editable(p.getOnline21Id());
      if (remote.isEmpty()) {
        return;
      }
      store(id, remote.get());
    } finally {
      inFlight.remove(id);
    }
  }

  /** Igual que refresh, con la ficha ya leída (importación del inventario). false si estaba ocupada. */
  private boolean applyRemote(UUID id, RemoteListing r) {
    if (!inFlight.add(id)) {
      return false;
    }
    try {
      store(id, r);
      return true;
    } finally {
      inFlight.remove(id);
    }
  }

  private void store(UUID id, RemoteListing r) {
    try {
      tx.executeWithoutResult(
          s -> {
            CrmProperty fresh = load(id);
            if (fresh.getSyncStatus() == SyncStatus.DONE) {
              fresh.applyRemote(fromRemote(r.draft(), fresh), r.photos(), null, r.updatedAt(), clock.instant());
              fresh.featuresTemplate(templateOf(r));
              fresh.baseline(json(draft(fresh)));
            } else {
              fresh.noteRemote(r.photos(), r.updatedAt());
            }
          });
    } catch (OptimisticLockingFailureException e) {
      // Se editó en Genera al mismo tiempo: en la siguiente pasada se vuelve a comparar.
    }
  }

  private RemoteListing fetchRemote(long online21Id) {
    if (!listings.available()) {
      throw new UnprocessableException("ONLINE21_NOT_CONFIGURED", "La integración con 21Online no está configurada.");
    }
    Optional<RemoteListing> remote;
    try {
      remote = listings.editable(online21Id);
    } catch (RuntimeException e) {
      throw new UnprocessableException("ONLINE21_UNAVAILABLE", "21Online no respondió. Intenta de nuevo en un momento.");
    }
    return remote.orElseThrow(() -> new NotFoundException("Propiedad de 21Online", online21Id));
  }

  private boolean isOwnListing(Long advisorOnline21Id, UUID userId) {
    return advisorOnline21Id != null && profiles.findById(userId).map(AdvisorProfile::getOnline21UserId).filter(advisorOnline21Id::equals).isPresent();
  }

  /**
   * Datos de Genera a partir de la ficha de 21Online. La subcategoría del catálogo se conserva si sigue
   * correspondiendo al subtipo; si no, se toma la primera activa que corresponda. El asesor sale del
   * vínculo Asesores → usuario de 21Online (si no está vinculado se conserva el que tenía).
   */
  Data fromRemote(ListingDraft r, CrmProperty current) {
    Data before = current == null ? null : current.data();
    PropertyCatalog.Subtype match = null;
    if (before != null && before.catalogSubtypeId() != null) {
      match = catalog.subtype(before.catalogSubtypeId()).filter(s -> s.online21Subtype().equals(r.subtype())).orElse(null);
    }
    if (match == null && r.subtype() != null) {
      List<PropertyCatalog.Subtype> candidates =
          catalog.all().stream().flatMap(c -> c.subtypes().stream()).filter(s -> s.active() && r.subtype().equals(s.online21Subtype())).toList();
      match = candidates.stream().filter(s -> s.online21Type().equals(r.propertyType())).findFirst().orElse(candidates.isEmpty() ? null : candidates.get(0));
    }
    UUID keptAdvisor = before == null ? null : before.advisorUserId();
    UUID advisor =
        r.advisorOnline21Id() == null
            ? keptAdvisor
            : profiles.findByOnline21UserId(r.advisorOnline21Id()).map(AdvisorProfile::getUserId).orElse(keptAdvisor);
    String detail = match != null && before != null && match.id().equals(before.catalogSubtypeId()) ? before.propertyTypeDetail() : null;
    return new Data(
        r.operation() != null ? r.operation() : "venta",
        match != null ? match.online21Type() : r.propertyType(),
        r.subtype() != null ? r.subtype() : "casa",
        r.contractType(),
        r.status() != null ? r.status() : "enPromocion",
        cut(r.headline() != null ? r.headline() : "Propiedad", 200),
        r.description(),
        r.salePrice(),
        r.rentPrice(),
        r.currency() != null ? r.currency() : "MXN",
        r.hidePrice(),
        cut(r.street(), 200),
        cut(r.number(), 40),
        cut(r.neighborhood() != null ? r.neighborhood() : "", 160),
        cut(r.municipality() != null ? r.municipality() : "", 160),
        cut(r.state() != null ? r.state() : "", 80),
        cut(r.postalCode(), 10),
        r.hideStreet(),
        r.hideNumber(),
        r.bedrooms(),
        r.bathrooms(),
        r.halfBathrooms(),
        r.parking(),
        r.landM2(),
        r.builtM2(),
        r.levels(),
        r.age(),
        r.maintenanceFee(),
        r.commission(),
        r.publishOnline(),
        advisor,
        match != null ? match.id() : null,
        detail,
        remoteExtras(r, before));
  }

  /** Atributos de 21Online más los que su API no devuelve (se conserva lo último capturado en Genera). */
  private static Map<String, Object> remoteExtras(ListingDraft r, Data before) {
    Map<String, Object> extras = new java.util.TreeMap<>(r.extras());
    extras.remove(ListingAttributes.FEATURES_TEMPLATE);
    if (before != null) {
      extras.putAll(ListingAttributes.writeOnly(before.extras()));
    }
    return extras;
  }

  private ListingDraft baselineOf(CrmProperty p) {
    if (p.getSyncedDraft() == null || p.getOnline21Id() == null) {
      return null;
    }
    try {
      return objectMapper.readValue(p.getSyncedDraft(), ListingDraft.class);
    } catch (java.io.IOException | RuntimeException e) {
      return null;
    }
  }

  private String json(ListingDraft draft) {
    try {
      return objectMapper.writeValueAsString(draft);
    } catch (java.io.IOException e) {
      return null;
    }
  }

  private static String cut(String s, int max) {
    return s == null || s.length() <= max ? s : s.substring(0, max);
  }

  private ListingDraft draft(CrmProperty p, String status) {
    ListingDraft d = draft(p);
    return new ListingDraft(
        d.operation(), d.propertyType(), d.subtype(), d.contractType(), status, d.headline(), d.description(), d.salePrice(), d.rentPrice(),
        d.currency(), d.street(), d.number(), d.neighborhood(), d.municipality(), d.state(), d.postalCode(), d.hideStreet(), d.hideNumber(),
        d.hidePrice(), d.bedrooms(), d.bathrooms(), d.halfBathrooms(), d.parking(), d.landM2(), d.builtM2(), d.levels(), d.age(), d.maintenanceFee(),
        d.commission(), d.publishOnline(), d.advisorOnline21Id(), d.integratorId(), d.extras());
  }

  private ListingDraft draft(CrmProperty p) {
    Data d = p.data();
    Long advisorOnline21 = d.advisorUserId() == null ? null : profiles.findById(d.advisorUserId()).map(AdvisorProfile::getOnline21UserId).orElse(null);
    return new ListingDraft(
        d.operation(), d.propertyType(), d.subtype(), d.contractType(), d.status(), d.headline(), d.description(), d.salePrice(), d.rentPrice(),
        d.currency(), d.street(), d.extNumber(), d.neighborhood(), d.municipality(), d.state(), d.postalCode(), d.hideStreet(), d.hideNumber(),
        d.hidePrice(), d.bedrooms(), d.bathrooms(), d.halfBathrooms(), d.parking(), d.landM2(), d.builtM2(), d.levels(), d.age(), d.maintenanceFee(),
        d.commission(), d.publishOnline(), advisorOnline21, "genera:" + p.getId(), draftExtras(p));
  }

  /** Atributos de la propiedad más la plantilla de caracteristicasJSON que trajo 21Online. */
  private static Map<String, Object> draftExtras(CrmProperty p) {
    Map<String, Object> extras = new java.util.TreeMap<>(p.data().extras());
    if (p.getFeaturesTemplate() != null) {
      extras.put(ListingAttributes.FEATURES_TEMPLATE, p.getFeaturesTemplate());
    }
    return extras;
  }

  // ---------------------------------------------------------------------------
  // Reglas
  // ---------------------------------------------------------------------------

  private Data normalize(Data in, CurrentUser user) {
    return normalize(in, user, null, null, null);
  }

  Data normalize(Data in, CurrentUser user, UUID currentAdvisor, UUID currentSubtype) {
    return normalize(in, user, currentAdvisor, currentSubtype, null);
  }

  /**
   * Valida con las reglas de 21Online y fija el asesor (quien no ve todo solo puede ponerse a sí mismo).
   * Control de Cambios #26: el tipo se elige en el catálogo de inmuebles; el tipo y subtipo de 21Online
   * salen de la subcategoría. currentSubtype: la que ya tenía (se acepta aunque se haya desactivado).
   * currentStatus: el que ya tenía (p. ej. "cerrada" en una propiedad traída de 21Online se conserva).
   */
  Data normalize(Data in, CurrentUser user, UUID currentAdvisor, UUID currentSubtype, String currentStatus) {
    String operation = lower(in.operation());
    if (!"venta".equals(operation) && !"renta".equals(operation)) {
      throw new UnprocessableException("PROPERTY_INVALID", "Elige si la propiedad es de venta o de renta.");
    }
    if (in.catalogSubtypeId() == null) {
      throw new UnprocessableException("PROPERTY_INVALID", "Selecciona el tipo de inmueble (la subcategoría, no solo la categoría).");
    }
    PropertyCatalog.Subtype catalogSubtype =
        catalog.subtype(in.catalogSubtypeId()).orElseThrow(() -> new UnprocessableException("PROPERTY_INVALID", "Ese tipo de inmueble no existe en el catálogo."));
    if (!catalogSubtype.active() && !catalogSubtype.id().equals(currentSubtype)) {
      throw new UnprocessableException("PROPERTY_INVALID", "\"" + catalogSubtype.name() + "\" ya no se ofrece en el catálogo. Elige otro tipo de inmueble.");
    }
    String typeDetail = catalogSubtype.requiresDetail() ? blankToNull(in.propertyTypeDetail()) : null;
    if (catalogSubtype.requiresDetail() && typeDetail == null) {
      throw new UnprocessableException("PROPERTY_INVALID", "Describe el tipo de inmueble (elegiste \"" + catalogSubtype.name() + "\").");
    }
    String type = catalogSubtype.online21Type();
    List<String> subtypes = SUBTYPES.get(type);
    if (subtypes == null) {
      throw new UnprocessableException("PROPERTY_INVALID", "Elige el tipo de propiedad (habitacional o comercial).");
    }
    String subtype = catalogSubtype.online21Subtype();
    if (!subtypes.contains(subtype)) {
      throw new UnprocessableException("PROPERTY_INVALID", "El subtipo no corresponde al tipo de propiedad en 21Online.");
    }
    String status = in.status() == null || in.status().isBlank() ? "enPromocion" : in.status().trim();
    if (!STATUSES.contains(status) && !status.equals(currentStatus)) {
      throw new UnprocessableException("PROPERTY_INVALID", "Estatus no válido. El cierre se registra en 21Online.");
    }
    String contract = lower(in.contractType());
    if (contract != null && !"exclusiva".equals(contract) && !"opcion".equals(contract)) {
      throw new UnprocessableException("PROPERTY_INVALID", "El tipo de contrato debe ser exclusiva u opción.");
    }
    BigDecimal price = "renta".equals(operation) ? in.rentPrice() : in.salePrice();
    if (price == null || price.signum() <= 0) {
      throw new UnprocessableException("PROPERTY_INVALID", "Captura el precio de " + operation + ".");
    }
    String currency = in.currency() == null || in.currency().isBlank() ? "MXN" : in.currency().trim().toUpperCase();
    if (!"MXN".equals(currency) && !"USD".equals(currency)) {
      throw new UnprocessableException("PROPERTY_INVALID", "La moneda debe ser MXN o USD.");
    }
    String headline = required(in.headline(), "el encabezado del anuncio");
    String neighborhood = required(in.neighborhood(), "la colonia");
    String municipality = required(in.municipality(), "el municipio");
    String state = required(in.state(), "el estado");
    if (in.commission() != null && (in.commission().signum() < 0 || in.commission().compareTo(BigDecimal.valueOf(100)) > 0)) {
      throw new UnprocessableException("PROPERTY_INVALID", "La comisión es un porcentaje entre 0 y 100.");
    }
    UUID advisor = in.advisorUserId();
    if (!user.hasPermission("LEAD_VIEW_ALL")) {
      // Un asesor solo puede asignársela a sí mismo; si ya tenía otro asesor, se conserva.
      advisor = currentAdvisor != null ? currentAdvisor : user.id();
    }
    return new Data(
        operation, type, subtype, contract, status, headline, blankToNull(in.description()),
        "venta".equals(operation) ? price : positive(in.salePrice()), "renta".equals(operation) ? price : positive(in.rentPrice()),
        currency, in.hidePrice(), blankToNull(in.street()), blankToNull(in.extNumber()), neighborhood, municipality, state, blankToNull(in.postalCode()),
        in.hideStreet(), in.hideNumber(), nonNegative(in.bedrooms()), positive(in.bathrooms()), nonNegative(in.halfBathrooms()), nonNegative(in.parking()),
        positive(in.landM2()), positive(in.builtM2()), nonNegative(in.levels()), nonNegative(in.age()), positive(in.maintenanceFee()), in.commission(),
        in.publishOnline(), advisor, catalogSubtype.id(), typeDetail, extras(in.extras()));
  }

  /** null = no vinieron en la petición (se conservan los que ya tenía la propiedad). */
  private static Map<String, Object> extras(Map<String, Object> in) {
    if (in == null) {
      return null;
    }
    try {
      return ListingAttributes.normalize(in);
    } catch (IllegalArgumentException e) {
      throw new UnprocessableException("PROPERTY_INVALID", e.getMessage());
    }
  }

  private void requireEditable(CrmProperty p, CurrentUser user) {
    if (!canEdit(p, user)) {
      throw new AccessDeniedException("Solo quien la capturó, su asesor o un directivo puede editarla.");
    }
  }

  private static boolean canEdit(CrmProperty p, CurrentUser user) {
    return user.hasPermission("PROPERTY_MANAGE")
        && (user.hasPermission("LEAD_VIEW_ALL") || user.id().equals(p.getCreatedByUserId()) || user.id().equals(p.getAdvisorUserId()));
  }

  private CrmProperty load(UUID id) {
    return properties.findById(id).orElseThrow(() -> new NotFoundException("Propiedad", id));
  }

  private PropertyView view(CrmProperty p, CurrentUser user, Map<UUID, String> names) {
    UUID advisor = p.getAdvisorUserId();
    boolean linked = advisor != null && profiles.findById(advisor).map(AdvisorProfile::getOnline21UserId).isPresent();
    return new PropertyView(
        p.getId(), p.getOnline21Id(), p.getSyncStatus().name(), p.getSyncError(), p.isSyncUncertain(), p.getSyncedAt(), p.data(),
        advisor == null ? null : new Person(advisor, staff.nameOf(advisor, names)), linked,
        p.getCreatedByUserId() == null ? null : new Person(p.getCreatedByUserId(), staff.nameOf(p.getCreatedByUserId(), names)),
        p.getCreatedAt(),
        p.getUpdatedAt(),
        canEdit(p, user),
        catalog.subtype(p.data().catalogSubtypeId()).map(s -> s.label(p.data().propertyTypeDetail())).orElse(null),
        p.getOrigin(),
        p.getRemoteModifiedAt(),
        p.getPhotoUrls(),
        p.getPrivateNotes(),
        p.getTags(),
        p.getListedAt() != null ? p.getListedAt() : p.getCreatedAt());
  }

  /** Asesor de Genera vinculado al usuario de 21Online (Asesores → usuario de 21Online). */
  private final class AdvisorLookup {
    private final Map<Long, Optional<Person>> cache = new HashMap<>();
    private final Map<UUID, String> names = staff.names();

    Person of(Long online21UserId) {
      if (online21UserId == null) {
        return null;
      }
      return cache
          .computeIfAbsent(online21UserId, id -> profiles.findByOnline21UserId(id).map(pr -> new Person(pr.getUserId(), staff.nameOf(pr.getUserId(), names))))
          .orElse(null);
    }
  }

  private static String required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new UnprocessableException("PROPERTY_INVALID", "Captura " + label + ".");
    }
    return value.trim();
  }

  private static String lower(String value) {
    return value == null || value.isBlank() ? null : value.trim().toLowerCase();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static BigDecimal positive(BigDecimal value) {
    return value != null && value.signum() > 0 ? value : null;
  }

  private static Integer nonNegative(Integer value) {
    return value != null && value >= 0 ? value : null;
  }
}
