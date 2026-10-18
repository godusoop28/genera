package com.c21genera.leads.web;

import com.c21genera.identity.CurrentUser;
import com.c21genera.leads.application.PropertyService;
import com.c21genera.leads.application.PropertyService.InventoryItem;
import com.c21genera.leads.application.PropertyService.InventoryPage;
import com.c21genera.leads.application.PropertyService.Overview;
import com.c21genera.leads.application.PropertyService.PropertyView;
import com.c21genera.leads.domain.CrmProperty.Data;
import com.c21genera.online21.PropertyListings.ListingFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

  public InternalPropertyController(PropertyService service) {
    this.service = service;
  }

  public record PropertyRequest(
      @NotBlank @Size(max = 8) String operation,
      @NotBlank @Size(max = 24) String propertyType,
      @NotBlank @Size(max = 40) String subtype,
      @Size(max = 16) String contractType,
      @Size(max = 24) String status,
      @NotBlank @Size(max = 200) String headline,
      @Size(max = 8000) String description,
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
      UUID advisorUserId) {

    Data toData() {
      return new Data(
          operation, propertyType, subtype, contractType, status, headline, description, salePrice, rentPrice, currency, hidePrice, street, extNumber,
          neighborhood, municipality, state, postalCode, hideStreet, hideNumber, bedrooms, bathrooms, halfBathrooms, parking, landM2, builtM2, levels, age,
          maintenanceFee, commission, publishOnline, advisorUserId);
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

  @PostMapping("/api/v1/internal/properties/{id}/sync")
  @PreAuthorize("hasAuthority('PROPERTY_MANAGE')")
  public PropertyView retry(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
    return service.retry(id, CurrentUser.from(jwt));
  }
}
