package com.c21genera.online21.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.online21.PropertyCatalog.CatalogProperty;
import com.c21genera.online21.PropertyCatalog.Operation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class Online21CatalogLogicTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private static CatalogProperty prop(long id, String key, String subtype, String op, long price, String colonia, String municipio) {
    return new CatalogProperty(
        id, id + "--649", key, subtype + " en " + op + " en " + colonia, op, subtype, BigDecimal.valueOf(price), "MXN", null, colonia, municipio, "Morelos",
        3, BigDecimal.valueOf(2), 2, null, null, null, 41388L, null, null);
  }

  private final List<CatalogProperty> index =
      List.of(
          prop(634294, "GN 14", "Casa", "venta", 5_980_000, "Burgos", "Temixco"),
          prop(634331, "GA 08", "Casa", "venta", 3_290_000, "Residencial La Palma", "Jiutepec"),
          prop(634274, "GA 12", "Departamento", "renta", 7_500, "Miguel Hidalgo", "Cuernavaca"),
          prop(634390, "EA 14", "Terreno", "venta", 1_530_000, "Lomas de Cortés", "Cuernavaca"));

  @Test
  void mapsOnline21JsonWithoutPrivateData() throws Exception {
    JsonNode node =
        mapper.readTree(
            """
            {"id":634294,"clave":"GN 14","idAfiliados":649,"tipoOperacion":"venta","subtipoPropiedad":"casa","precioVenta":"5980000.00","moneda":"MXN",
             "calle":"Retorno de Fátima","numero":"Lote 47","colonia":"Burgos","municipio":"Temixco","estado":"Morelos","ocultarCalleInternet":false,
             "recamaras":"4","banios":"4","estacionamientos":"2","m2T":"430.0","m2C":"330.0","idAsesorExclusiva":41388,
             "claveCatastral":"C21G-26-TMX-0001","fechaModificacion":"2026-10-08T12:00:00-06:00"}
            """);
    CatalogProperty p = Online21PropertyMapper.map(node, "https://example.test/propiedad/{id}");
    assertThat(p.key()).isEqualTo("634294--649");
    assertThat(p.internalKey()).isEqualTo("GN 14");
    assertThat(p.subtype()).isEqualTo("Casa");
    assertThat(p.price()).isEqualByComparingTo("5980000");
    assertThat(p.bedrooms()).isEqualTo(4);
    assertThat(p.builtM2()).isEqualByComparingTo("330");
    assertThat(p.advisorOnline21Id()).isEqualTo(41388L);
    assertThat(p.publicUrl()).isEqualTo("https://example.test/propiedad/634294");
    assertThat(p.street()).isEqualTo("Retorno de Fátima");
    // Nunca sale el número exterior ni la clave catastral.
    assertThat(p.toString()).doesNotContain("Lote 47").doesNotContain("C21G-26");
  }

  @Test
  void hidesStreetAndPriceWhenOnline21SaysSo() throws Exception {
    JsonNode node =
        mapper.readTree(
            """
            {"id":1,"tipoOperacion":"renta","subtipoPropiedad":"departamento","precioRenta":"7500","calle":"Humboldt",
             "ocultarCalleInternet":true,"ocultarPrecioInternet":true,"usuarios":"/api/usuarios/555"}
            """);
    CatalogProperty p = Online21PropertyMapper.map(node, null);
    assertThat(p.street()).isNull();
    assertThat(p.price()).isNull();
    assertThat(p.advisorOnline21Id()).isEqualTo(555L);
    assertThat(p.publicUrl()).isNull();
  }

  @Test
  void exactReferenceWins() {
    assertThat(CatalogSearch.search(index, "info de la 634331 porfa", Operation.ANY, 5)).extracting(CatalogProperty::id).containsExactly(634331L);
    assertThat(CatalogSearch.search(index, "la GN-14", Operation.ANY, 5)).extracting(CatalogProperty::id).containsExactly(634294L);
    assertThat(CatalogSearch.search(index, "https://portal.test/inmueble/634274--649?utm=x", Operation.ANY, 5))
        .extracting(CatalogProperty::id)
        .containsExactly(634274L);
  }

  @Test
  void searchesByPlaceTypeAndOperation() {
    assertThat(CatalogSearch.search(index, "casa en jiutepec", Operation.SALE, 5)).extracting(CatalogProperty::id).first().isEqualTo(634331L);
    assertThat(CatalogSearch.search(index, "departamento cuernavaca", Operation.RENT, 5)).extracting(CatalogProperty::id).containsExactly(634274L);
    assertThat(CatalogSearch.search(index, "terreno lomas de cortes", Operation.ANY, 5)).extracting(CatalogProperty::id).first().isEqualTo(634390L);
    assertThat(CatalogSearch.search(index, "algo en Monterrey", Operation.ANY, 5)).isEmpty();
  }

  @Test
  void budgetPushesExpensiveOptionsDown() {
    List<CatalogProperty> found = CatalogSearch.search(index, "casa hasta 3.5 millones", Operation.SALE, 5);
    assertThat(found).extracting(CatalogProperty::id).first().isEqualTo(634331L);
    assertThat(CatalogSearch.budgetOf("hasta 3.5 millones")).isEqualByComparingTo("3500000");
    assertThat(CatalogSearch.budgetOf("presupuesto 800 mil")).isEqualByComparingTo("800000");
    assertThat(CatalogSearch.budgetOf("casa en burgos")).isNull();
  }
}
