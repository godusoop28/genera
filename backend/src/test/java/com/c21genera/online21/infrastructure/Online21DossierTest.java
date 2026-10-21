package com.c21genera.online21.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.online21.PropertyDossier.Advisor;
import com.c21genera.online21.PropertyDossier.Details;
import com.c21genera.online21.PropertyDossier.Field;
import com.c21genera.online21.PropertyDossier.PortalListing;
import com.c21genera.online21.PropertyDossier.SheetVariant;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** Detalle de propiedades de 21Online: campos, portales, seguimientos y ficha técnica. */
class Online21DossierTest {

  private final ObjectMapper json = new ObjectMapper();

  private JsonNode property() throws Exception {
    return json.readTree(
        """
        {"id": 614854, "idAfiliados": 649, "clave": "GM 20", "status": "enPromocion", "tipoOperacion": "venta",
         "tipoPropiedad": "habitacional", "subtipoPropiedad": "casa", "precioVenta": "4990000.00", "moneda": "MXN",
         "comision": "2.50", "fechaFirmaCPS": "2026-04-15T00:00:00+00:00", "duracionCPS": 365, "calle": "Barrio de la Ventana",
         "numero": "21", "colonia": "Las Fincas", "municipio": "Jiutepec", "estado": "Morelos", "recamaras": "4", "banios": "4.0",
         "mediosBanios": "1", "m2T": "330.00", "m2C": "280.00", "alberca": true, "aceptaCreditos": "1", "observacionesOfna": null,
         "idClientesVendedor": "/api/clientes/99", "precioCierre": "123",
         "caracteristicasJSON": "[\\"Jardín\\", {\\"nombre\\": \\"Terraza\\"}]",
         "encabezado": "Casa renovada en Las Fincas", "descripcion": "Casa renovada distribuida en tres niveles.\\nCon alberca."}
        """);
  }

  @Test
  void generalFieldsHaveSpanishLabelsAndNeverTheOwner() throws Exception {
    List<Field> fields = Online21Dossier.fields(property());

    assertThat(fields).extracting(Field::label).contains("Clave", "Estatus", "Precio de venta", "Comisión", "Recámaras", "Alberca", "Amenidades");
    assertThat(fields).filteredOn(f -> f.label().equals("Estatus")).extracting(Field::value).containsExactly("En promoción");
    assertThat(fields).filteredOn(f -> f.label().equals("Comisión")).extracting(Field::value).containsExactly("2.5%");
    assertThat(fields).filteredOn(f -> f.label().equals("Alberca")).extracting(Field::value).containsExactly("Sí");
    assertThat(fields).filteredOn(f -> f.label().equals("Fecha de firma del contrato")).extracting(Field::value).containsExactly("2026-04-15");
    assertThat(fields).filteredOn(f -> f.label().equals("Amenidades")).extracting(Field::value).containsExactly("Jardín, Terraza");
    assertThat(fields).extracting(Field::value).noneMatch(v -> v.contains("/api/clientes"));
    assertThat(fields).extracting(Field::label).doesNotContain("Observaciones de la oficina");
  }

  @Test
  void portalLinkIsTakenFromWhereverTwentyOneOnlineKeepsIt() throws Exception {
    PortalListing ml =
        Online21Dossier.portalOf(
            json.readTree(
                """
                {"nombrePortal": "mercadolibre", "activo": true, "status": "publicado", "nombre1": "ID", "extra1": "MLM12345",
                 "json": "{\\"permalink\\":\\"https:\\\\/\\\\/casa.mercadolibre.com.mx\\\\/MLM-12345-casa\\"}", "conError": false}
                """));
    assertThat(ml.portal()).isEqualTo("Mercado Libre");
    assertThat(ml.active()).isTrue();
    assertThat(ml.url()).isEqualTo("https://casa.mercadolibre.com.mx/MLM-12345-casa");
    assertThat(ml.reference()).isEqualTo("ID: MLM12345");

    PortalListing failed =
        Online21Dossier.portalOf(json.readTree("{\"nombrePortal\": \"inmuebles24\", \"activo\": \"0\", \"conError\": \"1\", \"mensajeError\": \"Falta foto\"}"));
    assertThat(failed.portal()).isEqualTo("Inmuebles24");
    assertThat(failed.active()).isFalse();
    assertThat(failed.withError()).isTrue();
    assertThat(failed.url()).isNull();
  }

  @Test
  void followUpsMatchStreetNumberAndNeighborhood() throws Exception {
    JsonNode links =
        json.readTree("[{\"idPropiedades\": {\"calle\": \"Barrio de la Ventana\", \"numero\": \"21\", \"colonia\": \"Las Fincas\"}}]");
    assertThat(Online21Dossier.sameProperty(links, "barrio de la ventana", "21", "Las Fincas")).isTrue();
    assertThat(Online21Dossier.sameProperty(links, "Barrio de la Ventana", "22", "Las Fincas")).isFalse();
    assertThat(Online21Dossier.sameProperty(null, "Barrio de la Ventana", "21", "Las Fincas")).isFalse();
  }

  @Test
  void technicalSheetIsAPdfWithTheVariantRules() throws Exception {
    JsonNode n = property();
    Details details =
        new Details(
            Online21ListingMapper.toListing(n),
            Online21Dossier.fields(n),
            List.of(),
            List.of(),
            null,
            null,
            null,
            null,
            null,
            null,
            new Advisor(41388L, "Guadalupe Cruz Molina", "lupitacruz@c21genera.com", "+527778005300", "7774540198", null));
    PropertySheetPdf renderer = new PropertySheetPdf();

    String withAdvisor = text(renderer.render(details, SheetVariant.ADVISOR));
    assertThat(withAdvisor).contains("Casa renovada en Las Fincas", "$4,990,000", "Guadalupe Cruz Molina", "Recámaras", "Con alberca.");

    String noContact = text(renderer.render(details, SheetVariant.NO_CONTACT));
    assertThat(noContact).contains("Casa renovada en Las Fincas").doesNotContain("Guadalupe Cruz Molina", "7774540198");

    String noLogo = text(renderer.render(details, SheetVariant.NO_LOGO));
    assertThat(noLogo).doesNotContain("CENTURY 21 Genera", "Guadalupe");
  }

  private static String text(byte[] pdf) throws Exception {
    assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
    try (PDDocument doc = Loader.loadPDF(pdf)) {
      return new PDFTextStripper().getText(doc);
    }
  }
}
