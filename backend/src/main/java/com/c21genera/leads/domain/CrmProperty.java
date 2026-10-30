package com.c21genera.leads.domain;

import com.c21genera.shared.jpa.AuditableEntity;
import com.c21genera.shared.jpa.JsonMapConverter;
import com.c21genera.shared.jpa.JsonStringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Propiedad capturada en Genera y publicada en 21Online. Los códigos
 * (operation "venta"/"renta", subtype "casa", status "enPromocion") son los
 * de la API de 21Online para mandarlos tal cual. syncStatus: PENDING (aún no
 * se manda, p. ej. la integración está apagada), DONE (21Online la aceptó y
 * online21Id quedó lleno) o FAILED (rechazada o sin respuesta).
 */
@Entity
@Table(name = "crm_property")
public class CrmProperty extends AuditableEntity {

  @Id
  private UUID id;

  private Long online21Id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SyncStatus syncStatus;

  private String syncError;

  @Column(nullable = false)
  private boolean syncUncertain;

  private Instant syncedAt;

  @Column(nullable = false)
  private String operation;

  @Column(nullable = false)
  private String propertyType;

  @Column(nullable = false)
  private String subtype;

  private String contractType;

  @Column(nullable = false)
  private String status;

  @Column(nullable = false)
  private String headline;

  private String description;

  private BigDecimal salePrice;

  private BigDecimal rentPrice;

  @Column(nullable = false)
  private String currency;

  @Column(nullable = false)
  private boolean hidePrice;

  private String street;

  private String extNumber;

  @Column(nullable = false)
  private String neighborhood;

  @Column(nullable = false)
  private String municipality;

  @Column(nullable = false)
  private String state;

  private String postalCode;

  @Column(nullable = false)
  private boolean hideStreet;

  @Column(nullable = false)
  private boolean hideNumber;

  private Integer bedrooms;

  private BigDecimal bathrooms;

  private Integer halfBathrooms;

  private Integer parking;

  @Column(name = "land_m2")
  private BigDecimal landM2;

  @Column(name = "built_m2")
  private BigDecimal builtM2;

  private Integer levels;

  private Integer age;

  private BigDecimal maintenanceFee;

  private BigDecimal commission;

  @Column(nullable = false)
  private boolean publishOnline;

  private UUID advisorUserId;

  private UUID catalogSubtypeId;

  @Column(length = 160)
  private String propertyTypeDetail;

  /** Null en las importadas de 21Online por el proceso programado (nadie las vinculó a mano). */
  private UUID createdByUserId;

  /** Atributos adicionales con clave y forma de 21Online (ver online21.ListingAttributes). */
  @Convert(converter = JsonMapConverter.class)
  private Map<String, Object> extraAttributes = new TreeMap<>();

  /** URLs de las fotos en 21Online (solo lectura: se suben allá). */
  @Convert(converter = JsonStringListConverter.class)
  private List<String> photoUrls = new ArrayList<>();

  /** caracteristicasJSON de 21Online: al editar las amenidades se conservan sus demás campos. */
  private String featuresTemplate;

  /** Notas internas: solo en Genera, nunca se mandan a 21Online. */
  private String privateNotes;

  @Convert(converter = JsonStringListConverter.class)
  private List<String> tags = new ArrayList<>();

  /** CC#36: la misma columna como texto, solo para buscar por etiqueta en el listado (no se escribe). */
  @Column(name = "tags", insertable = false, updatable = false)
  private String tagsJson;

  /** GENERA (capturada aquí) u ONLINE21 (dada de alta en 21Online y vinculada desde Genera). */
  @Column(nullable = false, length = 16)
  private String origin = ORIGIN_GENERA;

  /** Lo último que se vio o se mandó a 21Online (JSON): al editar solo se mandan los campos que cambiaron. */
  private String syncedDraft;

  /** fechaModificacion de 21Online la última vez que se trajo. */
  private Instant remoteModifiedAt;

  /**
   * CC#36: fecha de alta (fechaAlta de 21Online). Null en las capturadas en Genera que aún no
   * aparecen en el inventario de 21Online: para ordenar se usa entonces la fecha de captura.
   */
  private Instant listedAt;

  public static final String ORIGIN_GENERA = "GENERA";
  public static final String ORIGIN_ONLINE21 = "ONLINE21";

  protected CrmProperty() {}

  public CrmProperty(UUID createdByUserId, Data data) {
    this.id = UUID.randomUUID();
    this.createdByUserId = createdByUserId;
    this.syncStatus = SyncStatus.PENDING;
    apply(data);
  }

  /** Propiedad que ya existe en 21Online, vinculada para editarla desde Genera (queda sincronizada). */
  public static CrmProperty linked(UUID createdByUserId, long online21Id, Data data, List<String> photos, Instant remoteModifiedAt, Instant now) {
    CrmProperty p = new CrmProperty();
    p.id = UUID.randomUUID();
    p.createdByUserId = createdByUserId;
    p.online21Id = online21Id;
    p.origin = ORIGIN_ONLINE21;
    p.assign(data);
    p.photos(photos);
    p.syncStatus = SyncStatus.DONE;
    p.syncedAt = now;
    p.remoteModifiedAt = remoteModifiedAt;
    return p;
  }

  /** Datos editables de la propiedad (mismo orden que el formulario). */
  public record Data(
      String operation,
      String propertyType,
      String subtype,
      String contractType,
      String status,
      String headline,
      String description,
      BigDecimal salePrice,
      BigDecimal rentPrice,
      String currency,
      boolean hidePrice,
      String street,
      String extNumber,
      String neighborhood,
      String municipality,
      String state,
      String postalCode,
      boolean hideStreet,
      boolean hideNumber,
      Integer bedrooms,
      BigDecimal bathrooms,
      Integer halfBathrooms,
      Integer parking,
      BigDecimal landM2,
      BigDecimal builtM2,
      Integer levels,
      Integer age,
      BigDecimal maintenanceFee,
      BigDecimal commission,
      boolean publishOnline,
      UUID advisorUserId,
      // Control de Cambios #26: subcategoría del catálogo (de ella salen propertyType/subtype de 21Online).
      UUID catalogSubtypeId,
      String propertyTypeDetail,
      // Características, contrato y publicación con clave de 21Online (ver online21.ListingAttributes).
      // null al guardar = no vinieron (p. ej. la app móvil): se conservan los que ya tenía.
      Map<String, Object> extras) {

    public Data {
      extras = extras == null ? null : new TreeMap<>(extras);
    }
  }

  /** Cambia los datos; si ya estaba en 21Online, queda pendiente de mandar la actualización. */
  public void apply(Data d) {
    assign(d);
    this.syncStatus = SyncStatus.PENDING;
    this.syncError = null;
    this.syncUncertain = false;
  }

  /**
   * Trae lo que se cambió en 21Online (solo si aquí no hay cambios pendientes de mandar): queda
   * sincronizada con lo de allá.
   */
  public void applyRemote(Data d, List<String> photos, String syncedDraft, Instant remoteModifiedAt, Instant now) {
    assign(d);
    photos(photos);
    this.syncStatus = SyncStatus.DONE;
    this.syncError = null;
    this.syncUncertain = false;
    this.syncedDraft = syncedDraft;
    this.remoteModifiedAt = remoteModifiedAt;
    this.syncedAt = now;
  }

  /**
   * Se vio un cambio en 21Online pero aquí hay cambios sin mandar: se mandan primero (solo los campos
   * tocados). Las fotos sí se actualizan: solo se editan allá.
   */
  public void noteRemote(List<String> photos, Instant remoteModifiedAt) {
    photos(photos);
    this.remoteModifiedAt = remoteModifiedAt;
  }

  private void photos(List<String> photos) {
    this.photoUrls = photos == null ? new ArrayList<>() : new ArrayList<>(photos);
  }

  public void baseline(String syncedDraft) {
    this.syncedDraft = syncedDraft;
  }

  /** "Eliminada": cancelada en 21Online (su API no borra propiedades). */
  public void markCancelled(String syncedDraft, Instant at) {
    this.status = "cancelada";
    this.syncStatus = SyncStatus.DONE;
    this.syncError = null;
    this.syncUncertain = false;
    this.syncedDraft = syncedDraft;
    this.syncedAt = at;
  }

  private void assign(Data d) {
    this.operation = d.operation();
    this.propertyType = d.propertyType();
    this.subtype = d.subtype();
    this.contractType = d.contractType();
    this.status = d.status();
    this.headline = d.headline();
    this.description = d.description();
    this.salePrice = d.salePrice();
    this.rentPrice = d.rentPrice();
    this.currency = d.currency();
    this.hidePrice = d.hidePrice();
    this.street = d.street();
    this.extNumber = d.extNumber();
    this.neighborhood = d.neighborhood();
    this.municipality = d.municipality();
    this.state = d.state();
    this.postalCode = d.postalCode();
    this.hideStreet = d.hideStreet();
    this.hideNumber = d.hideNumber();
    this.bedrooms = d.bedrooms();
    this.bathrooms = d.bathrooms();
    this.halfBathrooms = d.halfBathrooms();
    this.parking = d.parking();
    this.landM2 = d.landM2();
    this.builtM2 = d.builtM2();
    this.levels = d.levels();
    this.age = d.age();
    this.maintenanceFee = d.maintenanceFee();
    this.commission = d.commission();
    this.publishOnline = d.publishOnline();
    this.advisorUserId = d.advisorUserId();
    this.catalogSubtypeId = d.catalogSubtypeId();
    this.propertyTypeDetail = d.propertyTypeDetail();
    if (d.extras() != null) {
      this.extraAttributes = new TreeMap<>(d.extras());
    }
  }

  public Data data() {
    return new Data(
        operation, propertyType, subtype, contractType, status, headline, description, salePrice, rentPrice, currency, hidePrice, street, extNumber,
        neighborhood, municipality, state, postalCode, hideStreet, hideNumber, bedrooms, bathrooms, halfBathrooms, parking, landM2, builtM2, levels, age,
        maintenanceFee, commission, publishOnline, advisorUserId, catalogSubtypeId, propertyTypeDetail,
        extraAttributes == null ? new TreeMap<>() : extraAttributes);
  }

  public List<String> getPhotoUrls() {
    return List.copyOf(photoUrls);
  }

  /** Cambia solo las notas internas: no toca lo que se sincroniza con 21Online. */
  public void annotate(String notes, List<String> tags) {
    this.privateNotes = notes == null || notes.isBlank() ? null : notes.strip();
    this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
  }

  public String getPrivateNotes() {
    return privateNotes;
  }

  public List<String> getTags() {
    return tags == null ? List.of() : List.copyOf(tags);
  }

  public void featuresTemplate(String template) {
    if (template != null) {
      this.featuresTemplate = template;
    }
  }

  public String getFeaturesTemplate() {
    return featuresTemplate;
  }

  public void markSynced(Long online21Id, Instant at, String syncedDraft) {
    markSynced(online21Id, at);
    this.syncedDraft = syncedDraft;
  }

  public void markSynced(Long online21Id, Instant at) {
    if (online21Id != null) {
      this.online21Id = online21Id;
    }
    this.syncStatus = SyncStatus.DONE;
    this.syncError = null;
    this.syncUncertain = false;
    this.syncedAt = at;
  }

  /** Guarda el id de 21Online sin cambiar el estado (se editó mientras se enviaba el alta). */
  public void linkOnline21(Long online21Id) {
    if (this.online21Id == null) {
      this.online21Id = online21Id;
    }
  }

  public void markFailed(String error, boolean uncertain) {
    this.syncStatus = SyncStatus.FAILED;
    this.syncError = error == null ? null : error.length() > 500 ? error.substring(0, 499) + "…" : error;
    this.syncUncertain = uncertain;
  }

  /** Se deja como pendiente sin intentar (la integración está apagada). */
  public void markWaiting(String reason) {
    this.syncStatus = SyncStatus.PENDING;
    this.syncError = reason;
    this.syncUncertain = false;
  }

  public UUID getId() {
    return id;
  }

  public Long getOnline21Id() {
    return online21Id;
  }

  public SyncStatus getSyncStatus() {
    return syncStatus;
  }

  public String getSyncError() {
    return syncError;
  }

  public boolean isSyncUncertain() {
    return syncUncertain;
  }

  public Instant getSyncedAt() {
    return syncedAt;
  }

  public String getHeadline() {
    return headline;
  }

  public UUID getAdvisorUserId() {
    return advisorUserId;
  }

  public UUID getCreatedByUserId() {
    return createdByUserId;
  }

  public String getOrigin() {
    return origin;
  }

  public String getSyncedDraft() {
    return syncedDraft;
  }

  public Instant getListedAt() {
    return listedAt;
  }

  /** CC#36: solo se reemplaza con una fecha conocida (nunca se borra). */
  public void recordListedAt(Instant listedAt) {
    if (listedAt != null) {
      this.listedAt = listedAt;
    }
  }

  public Instant getRemoteModifiedAt() {
    return remoteModifiedAt;
  }
}
