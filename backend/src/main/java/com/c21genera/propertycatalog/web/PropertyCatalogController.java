package com.c21genera.propertycatalog.web;

import com.c21genera.propertycatalog.PropertyCatalog.Category;
import com.c21genera.propertycatalog.PropertyCatalog.Subtype;
import com.c21genera.propertycatalog.application.PropertyCatalogService;
import com.c21genera.propertycatalog.application.PropertyCatalogService.SubtypeInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Control de Cambios #26: el menú de tipo de inmueble (categorías con todas sus
 * subcategorías). Cualquier usuario interno lo consulta; mantenerlo requiere
 * PROPERTY_CATALOG_MANAGE (administrador).
 */
@RestController
@RequestMapping("/api/v1/internal/property-catalog")
public class PropertyCatalogController {

  private final PropertyCatalogService service;

  public PropertyCatalogController(PropertyCatalogService service) {
    this.service = service;
  }

  public record CategoryRequest(@NotBlank @Size(max = 80) String name, int sortOrder, boolean active) {}

  public record SubtypeRequest(
      @NotNull UUID categoryId,
      @NotBlank @Size(max = 80) String name,
      int sortOrder,
      boolean active,
      boolean requiresDetail,
      @NotBlank String caseType,
      @NotBlank String online21Type,
      @NotBlank String online21Subtype,
      // Control de Cambios #27: letras del tipo en la clave estructurada (opcional).
      @Size(max = 3) String keyCode) {

    SubtypeInput toInput() {
      return new SubtypeInput(categoryId, name, sortOrder, active, requiresDetail, caseType, online21Type, online21Subtype, keyCode);
    }
  }

  /** includeInactive=false (por omisión): solo lo que se ofrece en capturas nuevas. */
  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public List<Category> list(@RequestParam(defaultValue = "false") boolean includeInactive) {
    List<Category> all = service.all();
    if (includeInactive) {
      return all;
    }
    return all.stream()
        .filter(Category::active)
        .map(c -> new Category(c.id(), c.name(), c.sortOrder(), true, c.subtypes().stream().filter(Subtype::active).toList()))
        .filter(c -> !c.subtypes().isEmpty())
        .toList();
  }

  @PostMapping("/categories")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PROPERTY_CATALOG_MANAGE')")
  public Category createCategory(@Valid @RequestBody CategoryRequest request) {
    return service.createCategory(request.name(), request.sortOrder());
  }

  @PutMapping("/categories/{id}")
  @PreAuthorize("hasAuthority('PROPERTY_CATALOG_MANAGE')")
  public Category updateCategory(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
    return service.updateCategory(id, request.name(), request.sortOrder(), request.active());
  }

  @PostMapping("/subtypes")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('PROPERTY_CATALOG_MANAGE')")
  public Subtype createSubtype(@Valid @RequestBody SubtypeRequest request) {
    return service.createSubtype(request.toInput());
  }

  @PutMapping("/subtypes/{id}")
  @PreAuthorize("hasAuthority('PROPERTY_CATALOG_MANAGE')")
  public Subtype updateSubtype(@PathVariable UUID id, @Valid @RequestBody SubtypeRequest request) {
    return service.updateSubtype(id, request.toInput());
  }
}
