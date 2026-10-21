package com.c21genera.propertycatalog.application;

import com.c21genera.propertycatalog.PropertyCatalog;
import com.c21genera.propertycatalog.domain.PropertyCategory;
import com.c21genera.propertycatalog.domain.PropertySubtype;
import com.c21genera.propertycatalog.infrastructure.PropertyCategoryRepository;
import com.c21genera.propertycatalog.infrastructure.PropertySubtypeRepository;
import com.c21genera.shared.domain.ConflictException;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Control de Cambios #26: catálogo de inmuebles. El administrador agrega,
 * edita o desactiva categorías y subcategorías; nada se borra, así que los
 * expedientes y propiedades conservan su clasificación aunque cambie el menú.
 */
@Service
@Transactional
public class PropertyCatalogService implements PropertyCatalog {

  /** Tratamientos de documentos y contrato (ver expedientes.PropertyCaseType). */
  public static final Set<String> CASE_TYPES = Set.of("HOUSING", "DEPARTMENT", "RESIDENTIAL_LAND", "COMMERCIAL");

  /** Subtipos que 21Online acepta en México por tipo (igual que leads.PropertyService.SUBTYPES). */
  public static final Map<String, List<String>> ONLINE21_SUBTYPES =
      Map.of(
          "habitacional",
          List.of("casa", "casa-duplex", "casa-en-condominio", "departamento", "penthouse", "cabana", "terreno", "huerta", "quinta", "rancho", "hacienda", "edificio"),
          "comercial",
          List.of("local", "oficinas", "bodega", "nave", "fabrica", "terreno", "edificio", "fraccionamiento", "escuela", "inmueble-productivo"));

  private final PropertyCategoryRepository categories;
  private final PropertySubtypeRepository subtypes;

  public PropertyCatalogService(PropertyCategoryRepository categories, PropertySubtypeRepository subtypes) {
    this.categories = categories;
    this.subtypes = subtypes;
  }

  public record SubtypeInput(
      UUID categoryId, String name, int sortOrder, boolean active, boolean requiresDetail, String caseType, String online21Type, String online21Subtype) {}

  // --- Consulta ------------------------------------------------------------

  @Override
  @Transactional(readOnly = true)
  public Optional<Subtype> subtype(UUID subtypeId) {
    if (subtypeId == null) {
      return Optional.empty();
    }
    return subtypes.findById(subtypeId).map(s -> toView(s, categories.findById(s.getCategoryId()).map(PropertyCategory::getName).orElse("")));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Category> category(UUID categoryId) {
    if (categoryId == null) {
      return Optional.empty();
    }
    return all().stream().filter(c -> c.id().equals(categoryId)).findFirst();
  }

  @Override
  @Transactional(readOnly = true)
  public List<Category> all() {
    Map<UUID, List<PropertySubtype>> byCategory = subtypes.findAll().stream().collect(Collectors.groupingBy(PropertySubtype::getCategoryId));
    return categories.findAll().stream()
        .sorted(Comparator.comparingInt(PropertyCategory::getSortOrder).thenComparing(PropertyCategory::getName))
        .map(
            c ->
                new Category(
                    c.getId(),
                    c.getName(),
                    c.getSortOrder(),
                    c.isActive(),
                    byCategory.getOrDefault(c.getId(), List.of()).stream()
                        .sorted(Comparator.comparingInt(PropertySubtype::getSortOrder).thenComparing(PropertySubtype::getName))
                        .map(s -> toView(s, c.getName()))
                        .toList()))
        .toList();
  }

  // --- Mantenimiento (administrador) --------------------------------------

  public Category createCategory(String name, int sortOrder) {
    String clean = requireName(name, "la categoría");
    if (categories.existsByNameIgnoreCase(clean)) {
      throw new ConflictException("DUPLICATE_CATEGORY", "Ya existe la categoría \"" + clean + "\".");
    }
    PropertyCategory category = categories.save(new PropertyCategory(clean, sortOrder));
    return new Category(category.getId(), category.getName(), category.getSortOrder(), true, List.of());
  }

  public Category updateCategory(UUID id, String name, int sortOrder, boolean active) {
    PropertyCategory category = categories.findById(id).orElseThrow(() -> new NotFoundException("Categoría", id));
    String clean = requireName(name, "la categoría");
    if (!category.getName().equalsIgnoreCase(clean) && categories.existsByNameIgnoreCase(clean)) {
      throw new ConflictException("DUPLICATE_CATEGORY", "Ya existe la categoría \"" + clean + "\".");
    }
    category.update(clean, sortOrder, active);
    return category(id).orElseThrow();
  }

  public Subtype createSubtype(SubtypeInput input) {
    PropertyCategory category = categories.findById(input.categoryId()).orElseThrow(() -> new NotFoundException("Categoría", input.categoryId()));
    String clean = requireName(input.name(), "la subcategoría");
    if (subtypes.existsByCategoryIdAndNameIgnoreCase(category.getId(), clean)) {
      throw new ConflictException("DUPLICATE_SUBTYPE", "\"" + clean + "\" ya existe en " + category.getName() + ".");
    }
    validate(input);
    PropertySubtype subtype =
        new PropertySubtype(category.getId(), clean, input.sortOrder(), input.requiresDetail(), input.caseType(), input.online21Type(), input.online21Subtype());
    subtype.update(category.getId(), clean, input.sortOrder(), input.active(), input.requiresDetail(), input.caseType(), input.online21Type(), input.online21Subtype());
    return toView(subtypes.save(subtype), category.getName());
  }

  public Subtype updateSubtype(UUID id, SubtypeInput input) {
    PropertySubtype subtype = subtypes.findById(id).orElseThrow(() -> new NotFoundException("Subcategoría", id));
    PropertyCategory category = categories.findById(input.categoryId()).orElseThrow(() -> new NotFoundException("Categoría", input.categoryId()));
    String clean = requireName(input.name(), "la subcategoría");
    boolean renamedOrMoved = !subtype.getName().equalsIgnoreCase(clean) || !subtype.getCategoryId().equals(category.getId());
    if (renamedOrMoved && subtypes.existsByCategoryIdAndNameIgnoreCase(category.getId(), clean)) {
      throw new ConflictException("DUPLICATE_SUBTYPE", "\"" + clean + "\" ya existe en " + category.getName() + ".");
    }
    validate(input);
    subtype.update(category.getId(), clean, input.sortOrder(), input.active(), input.requiresDetail(), input.caseType(), input.online21Type(), input.online21Subtype());
    return toView(subtype, category.getName());
  }

  private static void validate(SubtypeInput input) {
    if (input.caseType() == null || !CASE_TYPES.contains(input.caseType())) {
      throw new UnprocessableException("INVALID_CASE_TYPE", "Elige cómo se tratan los documentos y el contrato de este tipo de inmueble.");
    }
    List<String> allowed = input.online21Type() == null ? null : ONLINE21_SUBTYPES.get(input.online21Type());
    if (allowed == null || !allowed.contains(input.online21Subtype())) {
      throw new UnprocessableException("INVALID_ONLINE21_TYPE", "Elige con qué tipo se publica en 21Online.");
    }
  }

  private static String requireName(String name, String what) {
    String clean = name == null ? "" : name.strip();
    if (clean.isEmpty() || clean.length() > 80) {
      throw new UnprocessableException("INVALID_NAME", "Escribe el nombre de " + what + " (máximo 80 caracteres).");
    }
    return clean;
  }

  private static Subtype toView(PropertySubtype s, String categoryName) {
    return new Subtype(
        s.getId(),
        s.getCategoryId(),
        categoryName,
        s.getName(),
        s.getSortOrder(),
        s.isActive(),
        s.isRequiresDetail(),
        s.getCaseType(),
        s.getOnline21Type(),
        s.getOnline21Subtype());
  }
}
