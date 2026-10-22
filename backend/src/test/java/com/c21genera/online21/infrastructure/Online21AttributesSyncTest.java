package com.c21genera.online21.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c21genera.online21.ListingAttributes;
import com.c21genera.online21.PropertyListings.ListingDraft;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Atributos adicionales, amenidades y fotos de 21Online en ambos sentidos. */
class Online21AttributesSyncTest {

  private final ObjectMapper json = new ObjectMapper();

  /** Forma real de GET /api/propiedades/{id} (campos relevantes). */
  private JsonNode remote() throws Exception {
    return json.readTree(
        """
        {"id": 609011, "tipoOperacion": "venta", "subtipoPropiedad": "casa", "status": "enPromocion", "encabezado": "Casa en Burgos",
         "precioVenta": "4990000.00", "colonia": "Burgos", "municipio": "Temixco", "estado": "Morelos",
         "cocina": "integral", "edoConservacion": "Muy Bueno", "tipoTerreno": "regular", "alberca": true, "mascotas": null,
         "metrosFrente": "12.50", "pisoEnQueSeEncuentra": "2", "regaderas": 3, "aceptaCreditos": false,
         "lat": "18.8985979", "lon": "-99.19959689999999", "videoUrl": null, "observacionesOfna": "nota interna", "claveCatastral": "X-1",
         "caracteristicasJSON": "{\\"campos\\":[{\\"nombre\\":\\"jardin\\",\\"label\\":\\"Jard\\\\u00edn\\",\\"tipo\\":\\"checkbox\\",\\"required\\":false,\\"valor\\":true},{\\"nombre\\":\\"estudio\\",\\"label\\":\\"Estudio\\",\\"tipo\\":\\"checkbox\\",\\"required\\":false,\\"valor\\":false},{\\"nombre\\":\\"matterport\\",\\"label\\":\\"URL Recorrido virtual\\",\\"tipo\\":\\"input\\",\\"required\\":false,\\"valor\\":\\"https://my.matterport.com/show/?m=abc\\"}]}",
         "fotos": [{"orden": 2, "path": "https://cdn.test/2.jpg"}, {"orden": 1, "path": "https://cdn.test/1.jpg"}, {"orden": 3, "path": "/sin-host.jpg"}]}
        """);
  }

  @Test
  void readsExtraAttributesInCanonicalFormWithoutInternalFields() throws Exception {
    Map<String, Object> extras = Online21ListingMapper.toDraft(remote()).extras();

    assertThat(extras)
        .containsEntry("cocina", "integral")
        .containsEntry("edoConservacion", "Muy Bueno")
        .containsEntry("alberca", true)
        .containsEntry("aceptaCreditos", false)
        .containsEntry("metrosFrente", "12.5")
        .containsEntry("pisoEnQueSeEncuentra", "2")
        .containsEntry("regaderas", 3)
        .containsEntry("lon", "-99.19959689999999")
        .doesNotContainKeys("mascotas", "videoUrl", "observacionesOfna", "claveCatastral");
    assertThat(extras.get(ListingAttributes.AMENITIES_KEY))
        .isEqualTo(Map.of("jardin", true, ListingAttributes.VIRTUAL_TOUR, "https://my.matterport.com/show/?m=abc"));
  }

  @Test
  void photosFollowTwentyOneOnlineOrderAndSkipRelativePaths() throws Exception {
    assertThat(Online21ListingMapper.photos(remote())).containsExactly("https://cdn.test/1.jpg", "https://cdn.test/2.jpg");
  }

  @Test
  void amenitiesAreSentAsTheFullCatalogInTwentyOneOnlineFormat() throws Exception {
    String features = Online21ListingMapper.featuresJson(null, Map.of("gimnasio", true, ListingAttributes.VIRTUAL_TOUR, "https://tour.test/1"));

    JsonNode fields = json.readTree(features).get("campos");
    assertThat(fields).hasSize(ListingAttributes.AMENITIES.size() + 1);
    assertThat(fields.get(0).get("nombre").asText()).isEqualTo("jardin");
    assertThat(fields.get(0).get("valor").asBoolean()).isFalse();
    JsonNode gym = null;
    for (JsonNode f : fields) {
      if (f.get("nombre").asText().equals("gimnasio")) {
        gym = f;
      }
    }
    assertThat(gym).isNotNull();
    assertThat(gym.get("valor").asBoolean()).isTrue();
    assertThat(gym.get("tipo").asText()).isEqualTo("checkbox");
    JsonNode tour = fields.get(fields.size() - 1);
    assertThat(tour.get("nombre").asText()).isEqualTo("matterport");
    assertThat(tour.get("tipo").asText()).isEqualTo("input");
    assertThat(tour.get("valor").asText()).isEqualTo("https://tour.test/1");
  }

  @Test
  void editingAmenitiesKeepsEveryOtherFieldOfTheTemplate() throws Exception {
    String template =
        """
        {"campos":[{"nombre":"jardin","label":"Jardin","tipo":"checkbox","required":false,"valor":true},
         {"nombre":"acabados","label":"Acabados","tipo":"input","required":false,"valor":"Marmol"},
         {"nombre":"hasGarden","label":"Jardin","tipo":"checkbox","required":false,"valor":true},
         {"nombre":"matterport","label":"URL Recorrido virtual","tipo":"input","required":false,"valor":"https://tour.test/viejo"}]}
        """;

    JsonNode fields = json.readTree(Online21ListingMapper.featuresJson(template, Map.of("hasGarden", true, "gimnasio", true))).get("campos");

    assertThat(fields).hasSize(5);
    assertThat(fields.get(0).get("valor").asBoolean()).isFalse();
    assertThat(fields.get(1).get("valor").asText()).isEqualTo("Marmol");
    assertThat(fields.get(2).get("valor").asBoolean()).isTrue();
    assertThat(fields.get(3).get("valor").asText()).isEmpty();
    assertThat(fields.get(4).get("nombre").asText()).isEqualTo("gimnasio");
    assertThat(fields.get(4).get("valor").asBoolean()).isTrue();
  }

  @Test
  void onlyChangedAttributesTravelOnEdit() throws Exception {
    ListingDraft before = Online21ListingMapper.toDraft(remote());
    Map<String, Object> extras = new LinkedHashMap<>(before.extras());
    extras.put("cocina", "cocineta");
    extras.remove("edoConservacion");
    extras.put("fechaFirmaCPS", "2026-10-01");
    ListingDraft after = withExtras(before, ListingAttributes.normalize(extras));

    Map<String, Object> changes = Online21ListingMapper.changes(before, after);

    assertThat(changes).containsOnlyKeys("cocina", "edoConservacion", "fechaFirmaCPS");
    assertThat(changes).containsEntry("cocina", "cocineta").containsEntry("edoConservacion", null).containsEntry("fechaFirmaCPS", "2026-10-01");
  }

  @Test
  void unchangedAmenitiesProduceNoUpdate() throws Exception {
    ListingDraft d = Online21ListingMapper.toDraft(remote());

    assertThat(Online21ListingMapper.changes(d, withExtras(d, ListingAttributes.normalize(d.extras())))).isEmpty();
  }

  @Test
  void formValuesAreValidatedAndNormalized() {
    Map<String, Object> in = new LinkedHashMap<>();
    in.put("alberca", "true");
    in.put("duracionCPS", "365");
    in.put("precioEnContrato", "4,500,000.00");
    in.put("fechaFirmaCPS", "2026-04-15T00:00:00+00:00");
    in.put("lat", "18.90");
    in.put("tipoPiso", "  ");
    in.put("claveCatastral", "no se acepta");
    in.put(ListingAttributes.AMENITIES_KEY, Map.of("cisterna", true, "estudio", false));

    Map<String, Object> out = ListingAttributes.normalize(in);

    assertThat(out)
        .containsEntry("alberca", true)
        .containsEntry("duracionCPS", 365)
        .containsEntry("precioEnContrato", "4500000")
        .containsEntry("fechaFirmaCPS", "2026-04-15")
        .containsEntry("lat", "18.9")
        .containsEntry(ListingAttributes.AMENITIES_KEY, Map.of("cisterna", true))
        .doesNotContainKeys("tipoPiso", "claveCatastral");
    assertThat(ListingAttributes.writeOnly(out)).containsOnlyKeys("duracionCPS", "precioEnContrato", "fechaFirmaCPS");

    assertThatThrownBy(() -> ListingAttributes.normalize(Map.of("regaderas", "2.5"))).hasMessageContaining("entero");
    assertThatThrownBy(() -> ListingAttributes.normalize(Map.of("lat", "120"))).hasMessageContaining("rango");
    assertThatThrownBy(() -> ListingAttributes.normalize(Map.of("videoUrl", "youtube.com/x"))).hasMessageContaining("https://");
  }

  @Test
  void advisorPhotoIsBuiltFromTheCdnBase() throws Exception {
    JsonNode user =
        json.readTree(
            """
            {"id": 41388, "fotoPath": "/uploads/649/usuarios/41388/69f93973d4e2c.jpg",
             "path": "https://cdn.21online.lat/mexico/cache/awsTest1/rc/fdYnHcCG/uploads/649/usuarios/",
             "telOfna": "777 100 2000", "urlUsuarioActivo": "https://century21mexico.com/v/41388"}
            """);

    assertThat(Online21UserDirectory.photoUrl(user)).isEqualTo("https://cdn.21online.lat/mexico/cache/awsTest1/rc/fdYnHcCG/uploads/649/usuarios/41388/69f93973d4e2c.jpg");
    var u = Online21UserDirectory.toUser(user);
    assertThat(u.officePhone()).isEqualTo("777 100 2000");
    assertThat(u.profileUrl()).isEqualTo("https://century21mexico.com/v/41388");
    assertThat(Online21UserDirectory.photoUrl(json.readTree("{\"fotoPath\": null}"))).isNull();
  }

  /** Como PropertyService: los atributos del formulario más la plantilla que trajo 21Online. */
  private static ListingDraft withExtras(ListingDraft d, Map<String, Object> formExtras) {
    Map<String, Object> extras = new LinkedHashMap<>(formExtras);
    Object template = d.extras().get(ListingAttributes.FEATURES_TEMPLATE);
    if (template != null) {
      extras.put(ListingAttributes.FEATURES_TEMPLATE, template);
    }
    return new ListingDraft(
        d.operation(), d.propertyType(), d.subtype(), d.contractType(), d.status(), d.headline(), d.description(), d.salePrice(), d.rentPrice(),
        d.currency(), d.street(), d.number(), d.neighborhood(), d.municipality(), d.state(), d.postalCode(), d.hideStreet(), d.hideNumber(),
        d.hidePrice(), d.bedrooms(), d.bathrooms(), d.halfBathrooms(), d.parking(), d.landM2(), d.builtM2(), d.levels(), d.age(), d.maintenanceFee(),
        d.commission(), d.publishOnline(), d.advisorOnline21Id(), d.integratorId(), extras);
  }
}
