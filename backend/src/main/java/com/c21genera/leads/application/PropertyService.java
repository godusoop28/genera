package com.c21genera.leads.application;

import com.c21genera.identity.CurrentUser;
import com.c21genera.leads.domain.AdvisorProfile;
import com.c21genera.leads.domain.CrmProperty;
import com.c21genera.leads.domain.CrmProperty.Data;
import com.c21genera.leads.domain.SyncStatus;
import com.c21genera.leads.infrastructure.AdvisorProfileRepository;
import com.c21genera.leads.infrastructure.CrmPropertyRepository;
import com.c21genera.online21.PropertyListings;
import com.c21genera.online21.PropertyListings.Listing;
import com.c21genera.online21.PropertyListings.ListingDraft;
import com.c21genera.online21.PropertyListings.ListingFilter;
import com.c21genera.online21.PropertyListings.ListingPage;
import com.c21genera.online21.PropertyListings.PublishException;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  private final TransactionTemplate tx;
  private final Clock clock;
  private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

  PropertyService(
      CrmPropertyRepository properties, AdvisorProfileRepository profiles, PropertyListings listings, StaffNames staff, TransactionTemplate tx, Clock clock) {
    this.properties = properties;
    this.profiles = profiles;
    this.listings = listings;
    this.staff = staff;
    this.tx = tx;
    this.clock = clock;
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
      boolean canEdit) {}

  public record InventoryItem(Listing listing, Person advisor, UUID generaId) {}

  public record InventoryPage(boolean configured, String error, List<InventoryItem> items, int page, Integer totalItems, boolean hasNext) {}

  public record Overview(boolean configured, List<PropertyView> captured) {}

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

  // ---------------------------------------------------------------------------
  // Alta, edición y envío a 21Online
  // ---------------------------------------------------------------------------

  public PropertyView create(Data input, CurrentUser user) {
    Data data = normalize(input, user);
    UUID id = tx.execute(s -> properties.save(new CrmProperty(user.id(), data)).getId());
    sync(id);
    return get(id, user);
  }

  public PropertyView update(UUID id, Data input, CurrentUser user) {
    tx.executeWithoutResult(
        s -> {
          CrmProperty p = load(id);
          requireEditable(p, user);
          Data data = normalize(input, user, p.getAdvisorUserId());
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
      ListingDraft draft = draft(p);
      Long createdId = null;
      String error = null;
      boolean uncertain = false;
      try {
        if (p.getOnline21Id() == null) {
          createdId = listings.create(draft);
        } else {
          listings.update(p.getOnline21Id(), draft);
        }
      } catch (PublishException e) {
        error = e.getMessage();
        uncertain = e.uncertain();
      }
      Long savedId = createdId;
      String savedError = error;
      boolean savedUncertain = uncertain;
      try {
        tx.executeWithoutResult(
            s -> {
              CrmProperty fresh = load(id);
              if (savedError == null) {
                fresh.markSynced(savedId, clock.instant());
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

  private ListingDraft draft(CrmProperty p) {
    Data d = p.data();
    Long advisorOnline21 = d.advisorUserId() == null ? null : profiles.findById(d.advisorUserId()).map(AdvisorProfile::getOnline21UserId).orElse(null);
    return new ListingDraft(
        d.operation(), d.propertyType(), d.subtype(), d.contractType(), d.status(), d.headline(), d.description(), d.salePrice(), d.rentPrice(),
        d.currency(), d.street(), d.extNumber(), d.neighborhood(), d.municipality(), d.state(), d.postalCode(), d.hideStreet(), d.hideNumber(),
        d.hidePrice(), d.bedrooms(), d.bathrooms(), d.halfBathrooms(), d.parking(), d.landM2(), d.builtM2(), d.levels(), d.age(), d.maintenanceFee(),
        d.commission(), d.publishOnline(), advisorOnline21, "genera:" + p.getId());
  }

  // ---------------------------------------------------------------------------
  // Reglas
  // ---------------------------------------------------------------------------

  private Data normalize(Data in, CurrentUser user) {
    return normalize(in, user, null);
  }

  /** Valida con las reglas de 21Online y fija el asesor (quien no ve todo solo puede ponerse a sí mismo). */
  Data normalize(Data in, CurrentUser user, UUID currentAdvisor) {
    String operation = lower(in.operation());
    if (!"venta".equals(operation) && !"renta".equals(operation)) {
      throw new UnprocessableException("PROPERTY_INVALID", "Elige si la propiedad es de venta o de renta.");
    }
    String type = lower(in.propertyType());
    List<String> subtypes = SUBTYPES.get(type);
    if (subtypes == null) {
      throw new UnprocessableException("PROPERTY_INVALID", "Elige el tipo de propiedad (habitacional o comercial).");
    }
    String subtype = lower(in.subtype());
    if (!subtypes.contains(subtype)) {
      throw new UnprocessableException("PROPERTY_INVALID", "El subtipo no corresponde al tipo de propiedad en 21Online.");
    }
    String status = in.status() == null || in.status().isBlank() ? "enPromocion" : in.status().trim();
    if (!STATUSES.contains(status)) {
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
        in.publishOnline(), advisor);
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
        new Person(p.getCreatedByUserId(), staff.nameOf(p.getCreatedByUserId(), names)), p.getCreatedAt(), p.getUpdatedAt(), canEdit(p, user));
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
