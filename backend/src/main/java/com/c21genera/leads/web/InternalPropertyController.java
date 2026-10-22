package com.c21genera.leads.web;

import com.c21genera.identity.CurrentUser;
import com.c21genera.leads.application.PropertyDossierService;
import com.c21genera.leads.application.PropertyDossierService.ClientsView;
import com.c21genera.leads.application.PropertyDossierService.DetailsView;
import com.c21genera.leads.application.PropertyService;
import com.c21genera.leads.application.PropertyService.InventoryItem;
import com.c21genera.leads.application.PropertyService.InventoryPage;
import com.c21genera.leads.application.PropertyService.Overview;
import com.c21genera.leads.application.PropertyService.PropertyView;
import com.c21genera.leads.application.PropertyService.ImportResult;
import com.c21genera.leads.domain.CrmProperty.Data;
import com.c21genera.online21.ListingAttributes;
import com.c21genera.online21.PropertyDossier.FollowUp;
import com.c21genera.online21.PropertyDossier.PortalListing;
import com.c21genera.online21.PropertyDossier.SheetVariant;
import com.c21genera.online21.PropertyListings.ListingFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Propiedades del CRM: inventario en vivo de 21Online y alta/edición de
 * propiedades que se publican en 21Online. Consultar: PROPERTY_VIEW.
 * Dar de alta o editar: PROPERTY_MANAGE.
 */
@RestController
@PreAuthorize("hasAuthority('PROPERTY_VIEW')")
public class InternalPropertyController {

  private final PropertyService service;
  private final PropertyDossierService dossier;

  public InternalPropertyController(PropertyService service, PropertyDossierService dossier) {
    this.service = service;
    this.dossier = dossier;
  }

  public record PropertyRequest(
      @NotBlank @Size(max = 8) String operation,
      // Control de Cambios #26: se toman de la subcategoría del catálogo (catalogSubtypeId).
      @Size(max = 24) String propertyType,
      @Size(max = 40) String subtype,
      @Size(max = 16) String contractType,
      @Size(max = 24) String status,
      @NotBlank @Size(max = 200) String headline,
      // Las traídas de 21Online pueden tener descripciones largas.
      @Size(max = 20000) String description,
      @DecimalMin("0") BigDecimal salePrice,
      @DecimalMin("0") BigDecimal rentPrice,
      @Size(max = 3) String currency,
      boolean hidePrice,
      @Size(max = 200) String street,
      @Size(max = 40) String extNumber,
      @NotBlank @Size(max = 160) String neighborhood,
      @NotBlank @Size(max = 160) String municipality,
      @NotBlank @Size(max = 80) String state,
      @Size(max = 10) String postalCode,
      boolean hideStreet,
      boolean hideNumber,
      @Min(0) @Max(200) Integer bedrooms,
      @DecimalMin("0") BigDecimal bathrooms,
      @Min(0) @Max(200) Integer halfBathrooms,
      @Min(0) @Max(2000) Integer parking,
      @DecimalMin("0") BigDecimal landM2,
      @DecimalMin("0") BigDecimal builtM2,
      @Min(0) @Max(300) Integer levels,
      @Min(0) @Max(500) Integer age,
      @DecimalMin("0") BigDecimal maintenanceFee,
      @DecimalMin("0") BigDecimal commission,
      boolean publishOnline,
      UUID advisorUserId,
      UUID catalogSubtypeId,
      @Size(max = 160) String propertyTypeDetail,
      // Atributos con clave de 21Online; se validan en PropertyService (ListingAttributes).
      @Size(max = 80) Map<String, Object> extras) {

    Data toData() {
      return new Data(
          operation, propertyType, subtype, contractType, status, headline, description, salePrice, rentPrice, currency, hidePrice, street, extNumber,
          neighborhood, municipality, state, postalCode, hideStreet, hideNumber, bedrooms, bathrooms, halfBathrooms, parking, landM2, builtM2, levels, age,
          maintenanceFee, commission, publishOnline, advisorUserId, catalogSubtypeId, propertyTypeDetail, extras);
    }
  }

  /** Inventario de 21Online (en vivo, 30 por página). */
  @GetMapping("/api/v1/internal/properties/inventory")
  public InventoryPage inventory(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String operation,
      @RequestParam(required = false) String subtype,
      @RequestParam(required = false) String municipality,
      @RequestParam(defaultValue = "1") int page) {
    return service.inventory(new ListingFilter(q, status, operation, subtype, municipality, page));
  }

  @GetMapping("/api/v1/internal/properties/inventory/{online21Id}")
  public InventoryItem inventoryItem(@PathVariable long online21Id) {
    return service.inventoryItem(online21Id);
  }

  // --- Detalle de 21Online (como su pantalla "Ver propiedad") -----------------------

  /** Datos generales completos, fotos, planos y asesor. */
  @GetMapping("/api/v1/internal/properties/inventory/{online21Id}/details")
  public DetailsView details(@PathVariable long online21Id) {
    return dossier.details(online21Id);
  }

  /** En qué portales está publicada (Mercado Libre, Inmuebles24…) con su liga y estado. */
  @GetMapping("/api/v1/internal/properties/inventory/{online21Id}/portals")
  public List<PortalListing> portals(@PathVariable long online21Id) {
    return dossier.portals(online21Id);
  }

  /** Clientes relacionados (prospectos interesados); el contacto solo para quien ve todos los leads o el asesor. */
  @GetMapping("/api/v1/internal/properties/inventory/{online21Id}/clients")
  public ClientsView clients(@PathVariable long online21Id, @AuthenticationPrincipal Jwt jwt) {
    return dossier.clients(online21Id, CurrentUser.from(jwt));
  }

  /** Historial de seguimientos (visitas, llamadas…). */
  @GetMapping("/api/v1/internal/properties/inventory/{online21Id}/follow-ups")
  public List<FollowUp> followUps(@PathVariable long online21Id) {
    return dossier.followUps(online21Id);
  }

  /** Ficha técnica en PDF: variant ADVISOR (con contacto del asesor), NO_CONTACT o NO_LOGO. */
  @GetMapping("/api/v1/internal/properties/inventory/{online21Id}/sheet")
  public ResponseEntity<byte[]> sheet(@PathVariable long online21Id, @RequestParam(defaultValue = "ADVISOR") SheetVariant variant) {
    PropertyDossierService.Sheet sheet = dossier.sheet(online21Id, variant);
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(sheet.fileName()).build().toString())
        .body(sheet.pdf());
  }

  public record AttributeCatalog(List<ListingAttributes.Attribute> attributes, List<ListingAttributes.Amenity> amenities) {}

  /** Atributos adicionales de 21Online que se capturan en el formulario (características, contrato, publicación). */
  @GetMapping("/api/v1/internal/properties/attributes")
  public AttributeCatalog attributes() {
    return new AttributeCatalog(ListingAttributes.ATTRIBUTES, ListingAttributes.AMENITIES);
  }

  /** Trae ya todo el inventario de la oficina (el proceso programado lo hace cada pocos minutos). */
  @PostMapping("/api/v1/internal/properties/import")
  @PreAuthorize("hasAuthority('PROPERTY_MANAGE') and hasAuthority('LEAD_VIEW_ALL')")
  public ImportResult importInventory() {
    return service.importOfficeInventory();
  }

  /** Propiedades capturadas en Genera, con su estado de envío a 21Online. */
  @GetMapping("/api/v1/internal/properties")
  public Overview captured(@AuthenticationPrincipal Jwt jwt) {
    return service.captured(CurrentUser.from(jwt));
  }

  @GetMapping("/api/v1/internal/properties/{id}")
  public PropertyView get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return service.get(id, CurrentUser.from(jwt));
  }

  @PostMapping("/api/v1/internal/properties")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PROPERTY_MANAGE')")
  public PropertyView create(@Valid @RequestBody PropertyRequest r, @AuthenticationPrincipal Jwt jwt) {
    return service.create(r.toData(), CurrentUser.from(jwt));
  }

  @PutMapping("/api/v1/internal/properties/{id}")
  @PreAuthorize("hasAuthority('PROPERTY_MANAGE')")
  public PropertyView update(@PathVariable UUID id, @Valid @RequestBody PropertyRequest r, @AuthenticationPrincipal Jwt jwt) {
    return service.update(id, r.toData(), CurrentUser.from(jwt));
  }

  public record NotesRequest(@Size(max = 4000) String privateNotes, @Size(max = 15) List<@NotBlank @Size(max = 40) String> tags) {}

  /** Notas internas y etiquetas: se guardan solo en Genera (no se mandan a 21Online). */
  @PutMapping("/api/v1/internal/properties/{id}/notes")
  @PreAuthorize("hasAuthority('PROPERTY_MANAGE')")
  public PropertyView notes(@PathVariable UUID id, @Valid @RequestBody NotesRequest r, @AuthenticationPrincipal Jwt jwt) {
    return service.annotate(id, r.privateNotes(), r.tags(), CurrentUser.from(jwt));
  }

  @PostMapping("/api/v1/internal/properties/{id}/sync")
  @PreAuthorize("hasAuthority('PROPERTY_MANAGE')")
  public PropertyView retry(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return service.retry(id, CurrentUser.from(jwt));
  }

  /** Vincula una propiedad dada de alta en 21Online para editarla desde Genera (queda sincronizada). */
  @PostMapping("/api/v1/internal/properties/inventory/{online21Id}/link")
  @PreAuthorize("hasAuthority('PROPERTY_MANAGE')")
  public PropertyView link(@PathVariable long online21Id, @AuthenticationPrincipal Jwt jwt) {
    return service.link(online21Id, CurrentUser.from(jwt));
  }

  /** Da de baja una propiedad de 21Online desde el inventario (21Online no borra: queda cancelada). */
  @PostMapping("/api/v1/internal/properties/inventory/{online21Id}/cancel")
  @PreAuthorize("hasAuthority('PROPERTY_MANAGE')")
  public PropertyView cancelRemote(@PathVariable long online21Id, @AuthenticationPrincipal Jwt jwt) {
    return service.removeRemote(online21Id, CurrentUser.from(jwt));
  }

  /** Eliminar: si ya está en 21Online queda cancelada allá (200); si nunca llegó, se borra (204). */
  @DeleteMapping("/api/v1/internal/properties/{id}")
  @PreAuthorize("hasAuthority('PROPERTY_MANAGE')")
  public ResponseEntity<PropertyView> remove(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    PropertyView view = service.remove(id, CurrentUser.from(jwt));
    return view == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(view);
  }
}
