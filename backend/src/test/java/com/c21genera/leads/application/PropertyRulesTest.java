package com.c21genera.leads.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c21genera.identity.CurrentUser;
import com.c21genera.leads.domain.CrmProperty.Data;
import com.c21genera.shared.domain.UnprocessableException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PropertyRulesTest {

  private final PropertyService service = new PropertyService(null, null, null, null, null, null);
  private final UUID advisorId = UUID.randomUUID();
  private final CurrentUser advisor = new CurrentUser(advisorId, "Asesor", "a@test", "ADVISOR", List.of("PROPERTY_VIEW", "PROPERTY_MANAGE", "LEAD_VIEW_OWN"));
  private final CurrentUser director = new CurrentUser(UUID.randomUUID(), "Directora", "d@test", "DIRECTOR", List.of("PROPERTY_MANAGE", "LEAD_VIEW_ALL"));

  private static Data data(String operation, String type, String subtype, BigDecimal sale, BigDecimal rent, UUID advisor) {
    return new Data(
        operation, type, subtype, null, null, " Casa en Burgos ", "", sale, rent, null, false, null, null, "Burgos", "Temixco", "Morelos", null,
        false, true, 3, BigDecimal.valueOf(2), null, -1, null, null, null, null, null, null, true, advisor);
  }

  @Test
  void defaultsAndAdvisorIsForcedToSelfWithoutViewAll() {
    UUID other = UUID.randomUUID();
    Data d = service.normalize(data("VENTA", "habitacional", "casa", BigDecimal.valueOf(4_500_000), null, other), advisor, null);

    assertThat(d.status()).isEqualTo("enPromocion");
    assertThat(d.currency()).isEqualTo("MXN");
    assertThat(d.operation()).isEqualTo("venta");
    assertThat(d.headline()).isEqualTo("Casa en Burgos");
    assertThat(d.description()).isNull();
    assertThat(d.parking()).isNull();
    assertThat(d.advisorUserId()).isEqualTo(advisorId);
  }

  @Test
  void directorCanAssignAnotherAdvisor() {
    UUID other = UUID.randomUUID();
    Data d = service.normalize(data("renta", "comercial", "local", null, BigDecimal.valueOf(15_000), other), director, null);

    assertThat(d.advisorUserId()).isEqualTo(other);
    assertThat(d.rentPrice()).isEqualByComparingTo("15000");
  }

  @Test
  void rejectsSubtypeOfAnotherTypeAndMissingPrice() {
    assertThatThrownBy(() -> service.normalize(data("venta", "comercial", "casa", BigDecimal.ONE, null, null), director, null))
        .isInstanceOf(UnprocessableException.class)
        .hasMessageContaining("subtipo");
    assertThatThrownBy(() -> service.normalize(data("renta", "habitacional", "casa", BigDecimal.valueOf(100), null, null), director, null))
        .isInstanceOf(UnprocessableException.class)
        .hasMessageContaining("precio de renta");
  }
}
