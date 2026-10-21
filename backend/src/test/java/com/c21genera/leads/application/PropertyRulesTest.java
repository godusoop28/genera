package com.c21genera.leads.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c21genera.identity.CurrentUser;
import com.c21genera.leads.domain.CrmProperty.Data;
import com.c21genera.propertycatalog.PropertyCatalog;
import com.c21genera.shared.domain.UnprocessableException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PropertyRulesTest {

  private static final UUID HABITACIONAL = UUID.randomUUID();
  private static final PropertyCatalog.Subtype CASA =
      new PropertyCatalog.Subtype(UUID.randomUUID(), HABITACIONAL, "Habitacional", "Casa", 10, true, false, "HOUSING", "habitacional", "casa");
  private static final PropertyCatalog.Subtype LOCAL =
      new PropertyCatalog.Subtype(UUID.randomUUID(), UUID.randomUUID(), "Comercial y oficinas", "Local comercial", 10, true, false, "COMMERCIAL", "comercial", "local");
  private static final PropertyCatalog.Subtype OTRO =
      new PropertyCatalog.Subtype(UUID.randomUUID(), UUID.randomUUID(), "Especiales", "Otro — especificar", 30, true, true, "COMMERCIAL", "comercial", "inmueble-productivo");
  private static final PropertyCatalog.Subtype RETIRED =
      new PropertyCatalog.Subtype(UUID.randomUUID(), HABITACIONAL, "Habitacional", "Quinta", 90, false, false, "HOUSING", "habitacional", "quinta");

  /** Catálogo en memoria (solo subtype() importa para estas reglas). */
  private static final PropertyCatalog CATALOG =
      new PropertyCatalog() {
        private final Map<UUID, Subtype> byId = Map.of(CASA.id(), CASA, LOCAL.id(), LOCAL, OTRO.id(), OTRO, RETIRED.id(), RETIRED);

        @Override
        public Optional<Subtype> subtype(UUID subtypeId) {
          return Optional.ofNullable(subtypeId == null ? null : byId.get(subtypeId));
        }

        @Override
        public Optional<Category> category(UUID categoryId) {
          return Optional.empty();
        }

        @Override
        public List<Category> all() {
          return List.of();
        }
      };

  private final PropertyService service = new PropertyService(null, null, null, null, CATALOG, null, null);
  private final UUID advisorId = UUID.randomUUID();
  private final CurrentUser advisor = new CurrentUser(advisorId, "Asesor", "a@test", "ADVISOR", List.of("PROPERTY_VIEW", "PROPERTY_MANAGE", "LEAD_VIEW_OWN"));
  private final CurrentUser director = new CurrentUser(UUID.randomUUID(), "Directora", "d@test", "DIRECTOR", List.of("PROPERTY_MANAGE", "LEAD_VIEW_ALL"));

  private static Data data(String operation, UUID subtypeId, String detail, BigDecimal sale, BigDecimal rent, UUID advisor) {
    return new Data(
        operation, null, null, null, null, " Casa en Burgos ", "", sale, rent, null, false, null, null, "Burgos", "Temixco", "Morelos", null,
        false, true, 3, BigDecimal.valueOf(2), null, -1, null, null, null, null, null, null, true, advisor, subtypeId, detail);
  }

  @Test
  void defaultsAndAdvisorIsForcedToSelfWithoutViewAll() {
    UUID other = UUID.randomUUID();
    Data d = service.normalize(data("VENTA", CASA.id(), null, BigDecimal.valueOf(4_500_000), null, other), advisor, null, null);

    assertThat(d.status()).isEqualTo("enPromocion");
    assertThat(d.currency()).isEqualTo("MXN");
    assertThat(d.operation()).isEqualTo("venta");
    assertThat(d.headline()).isEqualTo("Casa en Burgos");
    assertThat(d.description()).isNull();
    assertThat(d.parking()).isNull();
    assertThat(d.advisorUserId()).isEqualTo(advisorId);
  }

  @Test
  void online21TypeComesFromTheCatalogSubtype() {
    Data d = service.normalize(data("renta", LOCAL.id(), "ignorado", null, BigDecimal.valueOf(15_000), null), director, null, null);

    assertThat(d.propertyType()).isEqualTo("comercial");
    assertThat(d.subtype()).isEqualTo("local");
    assertThat(d.catalogSubtypeId()).isEqualTo(LOCAL.id());
    assertThat(d.propertyTypeDetail()).isNull();
  }

  @Test
  void directorCanAssignAnotherAdvisor() {
    UUID other = UUID.randomUUID();
    Data d = service.normalize(data("renta", LOCAL.id(), null, null, BigDecimal.valueOf(15_000), other), director, null, null);

    assertThat(d.advisorUserId()).isEqualTo(other);
    assertThat(d.rentPrice()).isEqualByComparingTo("15000");
  }

  @Test
  void requiresTheSubtypeAndTheDetailForOther() {
    assertThatThrownBy(() -> service.normalize(data("venta", null, null, BigDecimal.TEN, null, null), director, null, null))
        .isInstanceOf(UnprocessableException.class)
        .hasMessageContaining("subcategoría");
    assertThatThrownBy(() -> service.normalize(data("venta", OTRO.id(), " ", BigDecimal.TEN, null, null), director, null, null))
        .isInstanceOf(UnprocessableException.class)
        .hasMessageContaining("Describe");

    Data d = service.normalize(data("venta", OTRO.id(), " Capilla ", BigDecimal.TEN, null, null), director, null, null);
    assertThat(d.propertyTypeDetail()).isEqualTo("Capilla");
  }

  @Test
  void inactiveSubtypeOnlyWhenItWasAlreadyChosen() {
    assertThatThrownBy(() -> service.normalize(data("venta", RETIRED.id(), null, BigDecimal.TEN, null, null), director, null, null))
        .isInstanceOf(UnprocessableException.class)
        .hasMessageContaining("ya no se ofrece");

    Data d = service.normalize(data("venta", RETIRED.id(), null, BigDecimal.TEN, null, null), director, null, RETIRED.id());
    assertThat(d.subtype()).isEqualTo("quinta");
  }

  @Test
  void rejectsMissingPrice() {
    assertThatThrownBy(() -> service.normalize(data("renta", CASA.id(), null, BigDecimal.valueOf(100), null, null), director, null, null))
        .isInstanceOf(UnprocessableException.class)
        .hasMessageContaining("precio de renta");
  }
}
