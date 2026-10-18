package com.c21genera.online21.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.online21.PropertyListings.Listing;
import com.c21genera.online21.PropertyListings.ListingDraft;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Online21ListingMapperTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private static ListingDraft draft(String operation, BigDecimal sale, BigDecimal rent, Long advisor) {
    return new ListingDraft(
        operation, "habitacional", "casa", "exclusiva", null, "Casa con jardín", "Descripción", sale, rent, null, "Av. Siempre Viva", "S/N",
        "Centro", "Cuernavaca", "Morelos", "62000", false, true, false, 3, new BigDecimal("2.5"), 1, 2, new BigDecimal("300.00"), new BigDecimal("220"),
        2, 5, null, new BigDecimal("5"), true, advisor, "genera:abc");
  }

  @Test
  void buildsCreatePayloadWithRequiredCountryAndTextNumbers() {
    Map<String, Object> p = Online21ListingMapper.toPayload(draft("venta", new BigDecimal("4500000.00"), null, 41388L));

    assertThat(p).containsEntry("pais", "México").containsEntry("estado", "Morelos").containsEntry("status", "enPromocion");
    assertThat(p).containsEntry("precioVenta", "4500000").doesNotContainKey("precioRenta");
    assertThat(p).containsEntry("banios", "2.5").containsEntry("m2T", "300").containsEntry("recamaras", "3");
    assertThat(p).containsEntry("usuarios", "/api/usuarios/41388").containsEntry("moneda", "MXN");
    assertThat(p).containsEntry("comision", "5").containsEntry("tipoComision", "porcentaje");
    assertThat(p).containsEntry("ocultarNumeroInternet", true).containsEntry("enInternet", true);
    assertThat(p).doesNotContainKey("cuotaMantenimiento").doesNotContainKey("claveCatastral");
  }

  @Test
  void rentUsesRentPriceAndSkipsAdvisorWhenNotLinked() {
    Map<String, Object> p = Online21ListingMapper.toPayload(draft("renta", null, new BigDecimal("12000"), null));

    assertThat(p).containsEntry("precioRenta", "12000").doesNotContainKey("precioVenta").doesNotContainKey("usuarios");
  }

  @Test
  void readsInternalListingWithoutOwnerOrCadastralData() throws Exception {
    Listing l =
        Online21ListingMapper.toListing(
            mapper.readTree(
                """
                {"id":634294,"clave":"GN 14","idAfiliados":649,"status":"suspendida","tipoOperacion":"venta","tipoPropiedad":"habitacional",
                 "subtipoPropiedad":"casa","precioVenta":"5980000.00","calle":"Retorno","numero":"47","colonia":"Burgos","municipio":"Temixco",
                 "estado":"Morelos","recamaras":"4","banios":"3.5","enInternet":false,"usuarios":"/api/usuarios/41388",
                 "idClientesVendedor":99,"claveCatastral":"X-1","fotos":[{"path":"https://cdn.test/1.jpg"},{"path":"https://cdn.test/2.jpg"}],
                 "fechaAlta":"2026-09-01T10:00:00-06:00"}
                """));

    assertThat(l.key()).isEqualTo("634294--649");
    assertThat(l.status()).isEqualTo("suspendida");
    assertThat(l.title()).isEqualTo("Casa en venta en Burgos");
    assertThat(l.salePrice()).isEqualByComparingTo("5980000");
    assertThat(l.bathrooms()).isEqualByComparingTo("3.5");
    assertThat(l.advisorOnline21Id()).isEqualTo(41388L);
    assertThat(l.publishedOnline()).isFalse();
    assertThat(l.photoUrl()).isEqualTo("https://cdn.test/1.jpg");
    assertThat(l.photoCount()).isEqualTo(2);
    assertThat(l.toString()).doesNotContain("X-1");
  }

  @Test
  void summarizesApiPlatformViolations() throws Exception {
    String msg =
        Online21ListingMapper.violations(
            mapper.readTree(
                """
                {"@type":"ConstraintViolationList","violations":[{"propertyPath":"estado","message":"Este valor no debería estar vacío."},
                 {"propertyPath":"subtipoPropiedad","message":"El valor no es válido."}]}
                """));

    assertThat(msg).isEqualTo("estado: Este valor no debería estar vacío.; subtipoPropiedad: El valor no es válido.");
  }
}
