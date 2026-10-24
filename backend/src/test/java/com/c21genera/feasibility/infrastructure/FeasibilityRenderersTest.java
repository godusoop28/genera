package com.c21genera.feasibility.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.feasibility.domain.PresentationData;
import com.c21genera.feasibility.domain.PresentationData.ComparableListing;
import com.c21genera.feasibility.infrastructure.FeasibilityPptxRenderer.Distance;
import com.c21genera.feasibility.infrastructure.OpenAiBackgroundClient.Citation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

/**
 * El Word y el PowerPoint se generan con datos de ejemplo y se vuelven a abrir. Con
 * -Dfeasibility.dump=&lt;carpeta&gt; además se guardan los archivos y cada lámina como PNG para
 * revisarlos a ojo.
 */
public class FeasibilityRenderersTest {

  private static final String MARKDOWN =
      """
      ## 1. FACTIBILIDAD COMPLETA

      Casa en **Vista Hermosa, Cuernavaca** con 420 m² de terreno. Factibilidad *condicionada* al ajuste de precio.

      ## 2. RESUMEN EJECUTIVO

      - Precio solicitado: $6,900,000 (fuera del rango competitivo).
      - Rango competitivo: $5,900,000 – $6,300,000.
        - Sub-punto con detalle.
      1. Primera decisión.
      2. Segunda decisión.

      ## 6. COMPARATIVOS (MUESTRA OPERATIVA)

      | ID | ESTATUS | UBICACIÓN | PRECIO | TERRENO M² | CONSTRUCCIÓN M² | PRECIO/M² | DOM | DIFERENCIA CLAVE | LECTURA OPERATIVA |
      |---|---|---|---|---|---|---|---|---|---|
      | C1 | Publicado | Vista Hermosa | $6,200,000 | 400 | 310 | $20,000 | 90 días | Alberca | [Inmuebles24](https://www.inmuebles24.com/propiedades/ejemplo-1.html) |
      | C2 | Publicado | Rancho Cortés | $5,800,000 | 380 | 290 | $20,000 | n/d | Sin alberca | Presiona el precio<br>Ver https://www.lamudi.com.mx/ejemplo-2 |

      > Los precios publicados son precios de oferta.

      ---

      ## 27. PRÓXIMO PASO

      Presentar el análisis al propietario el lunes.
      """;

  @Test
  void generatesEditableWordAndPowerPoint() throws Exception {
    PresentationData data = sample().normalized();
    Map<String, Distance> distances = new LinkedHashMap<>();
    for (ComparableListing c : data.comparables()) {
      distances.put(c.id(), new Distance(0.4 + c.id().length() * 0.7, c.id().endsWith("3")));
    }
    StaticMapRenderer map = new StaticMapRenderer(new FeasibilityProperties(null, null, 0, 0, null, 0, 0, System.getProperty("feasibility.tiles", "http://127.0.0.1:9/{z}/{x}/{y}.png"), null, null));
    List<StaticMapRenderer.Point> points = new ArrayList<>();
    for (ComparableListing c : data.comparables()) {
      points.add(new StaticMapRenderer.Point(c.id(), c.group(), c.latitude(), c.longitude(), c.id().endsWith("3")));
    }
    byte[] mapPng = map.render(new StaticMapRenderer.Point("Inmueble", "SUBJECT", 18.93, -99.225, false), points);

    byte[] pptx =
        new FeasibilityPptxRenderer()
            .render(
                new FeasibilityPptxRenderer.Deck(
                    data, "EXP-2026-0042", "24 de octubre de 2026", "Emilia Rodríguez", List.of(photo(), photo()), mapPng,
                    "https://www.google.com/maps/search/?api=1&query=18.93,-99.225", distances, true, false));
    byte[] docx =
        new FeasibilityDocxRenderer()
            .render(
                new FeasibilityDocxRenderer.Cover("Casa en Vista Hermosa", "EXP-2026-0042", "Calle Ejemplo 12, Vista Hermosa, Cuernavaca", "Emilia Rodríguez", "24 de octubre de 2026"),
                MARKDOWN,
                List.of(new Citation("Inmuebles24", "https://www.inmuebles24.com/propiedades/ejemplo-1.html")));

    try (XMLSlideShow show = new XMLSlideShow(new ByteArrayInputStream(pptx))) {
      assertThat(show.getSlides()).hasSize(15);
      assertThat(show.getPageSize().getWidth()).isEqualTo(960);
      dumpSlides(show, pptx);
    }
    try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
      assertThat(doc.getTables()).hasSize(1);
      assertThat(doc.getTables().get(0).getRows()).hasSize(3);
      assertThat(new org.apache.poi.xwpf.extractor.XWPFWordExtractor(doc).getText()).contains("RESUMEN EJECUTIVO").contains("Vista Hermosa").doesNotContain("**").doesNotContain("|---");
      assertThat(doc.getHyperlinks()).isNotEmpty();
      dump("factibilidad.docx", docx);
    }
  }

  @Test
  void schemaIsStrictEverywhere() throws Exception {
    String json = new ObjectMapper().writeValueAsString(PresentationData.SCHEMA);
    // En modo estricto cada objeto lleva additionalProperties=false y todos sus campos como requeridos.
    assertThat(json).contains("\"additionalProperties\":false").contains("\"comparables\"").contains("\"pendingItems\"");
    assertThat(PresentationData.SCHEMA.get("required")).asList().hasSize(12);
  }

  @Test
  void normalizedRenumbersComparablesByGroup() {
    PresentationData data = sample().normalized();
    assertThat(data.comparables()).extracting(ComparableListing::id).startsWith("C1", "C2").hasSize(14);
    assertThat(data.comparables().get(0).group()).isEqualTo(PresentationData.ABOVE);
    assertThat(data.comparables().get(13).group()).isEqualTo(PresentationData.BELOW);
  }

  public static PresentationData sample() {
    List<ComparableListing> comps = new ArrayList<>();
    String[] zones = {"Vista Hermosa, Cuernavaca", "Rancho Cortés, Cuernavaca", "Lomas de Cortés, Cuernavaca", "Delicias, Cuernavaca", "Palmira, Cuernavaca"};
    long[] base = {7_400_000L, 6_200_000L, 4_900_000L};
    String[] groups = {PresentationData.BELOW, PresentationData.ABOVE, PresentationData.SIMILAR};
    for (int g = 0; g < 3; g++) {
      int count = g == 0 ? 4 : 5;
      for (int i = 0; i < count; i++) {
        String group = groups[g];
        long price = (group.equals(PresentationData.ABOVE) ? base[0] : group.equals(PresentationData.SIMILAR) ? base[1] : base[2]) + i * 180_000L;
        comps.add(
            new ComparableListing(
                "X" + g + i, group, zones[i], null, price, 380.0 + i * 20, 280.0 + i * 15, "Buen estado",
                "3 recámaras, alberca, jardín y vigilancia 24 h en privada con acceso controlado", i == 2 ? null : "https://www.inmuebles24.com/propiedades/ejemplo-" + g + i + ".html",
                i != 1, 18.93 + (g - 1) * 0.01 + i * 0.004, -99.225 + (i - 2) * 0.008, PresentationData.EXACT, "Inmuebles24, 24/10/2026", ""));
      }
    }
    return new PresentationData(
        "La casa está por arriba del rango competitivo de la zona. Con un precio de salida de $6,250,000 y fotografía profesional, compite con las alternativas directas.",
        new PresentationData.PropertyContext(
            "Casa con jardín y alberca en Vista Hermosa", "Vista Hermosa, Cuernavaca, Morelos", "420 m² de terreno · 320 m² de construcción",
            "3 recámaras, 3.5 baños, estudio, cuarto de servicio", "Conservación buena; cocina original de 2008", List.of("Alberca", "Jardín", "Vigilancia"),
            List.of("Ubicación en privada con vigilancia", "Jardín amplio con alberca", "Recámara principal en planta baja"), null, null, PresentationData.APPROXIMATE),
        List.of(new PresentationData.Point("Privada con vigilancia", "El comprador familiar valora la seguridad y paga una prima por ella."), new PresentationData.Point("Jardín y alberca", "Atributo difícil de sustituir en la zona.")),
        List.of(new PresentationData.Point("Cocina original", "Puede percibirse como gasto inmediato de remodelación.")),
        new PresentationData.MarketSupply(38, 3_900_000L, 12_500_000L, 6_850_000L, 6_300_000L, "La mayor parte de la oferta comparable está entre $5.5 y $7 millones.", "Al precio solicitado queda en el tercio superior, junto a casas más nuevas.", "Inventario observado en portales el 24/10/2026; no es el total del mercado."),
        comps,
        new PresentationData.GroupNotes(
            new PresentationData.GroupNote("Construcciones más recientes y acabados actuales.", "Terrenos más chicos y sin privada."),
            new PresentationData.GroupNote("Mismo comprador objetivo y ubicación similar.", "Algunas ya bajaron de precio."),
            new PresentationData.GroupNote("Precio de entrada más bajo.", "Requieren remodelación mayor.")),
        new PresentationData.Pricing(6_900_000L, 5_900_000L, 6_400_000L, 6_250_000L, "FUERA_DE_RANGO", "Salir en $6,250,000 para competir dentro del grupo de comparables directos y conservar margen de negociación.", List.of("Los cinco comparables directos se publican entre $6.2 y $6.9 millones.", "La cocina original resta frente a casas remodeladas.")),
        new PresentationData.SalesStrategy(
            "Casa familiar segura con jardín", "Familia con crédito o recursos propios de $6 millones", List.of("Privada", "Jardín con alberca"), List.of("Fotografía profesional y video", "Portales principales y red CENTURY 21"),
            "Una casa familiar en privada con jardín y alberca, a precio de mercado.", "Casa en privada en Vista Hermosa con jardín y alberca.", "Descripción media de ejemplo.", "Descripción completa de ejemplo."),
        "Los comparables están a menos de 3 km: compiten por el mismo comprador, aunque su estado y antigüedad difieren.",
        new PresentationData.Conclusions(List.of("El precio solicitado está fuera del rango competitivo."), List.of("Firmar contrato de exclusiva", "Sesión de fotografía"), "Te invitamos a avanzar con una estrategia clara."),
        List.of("Superficie de construcción por confirmar con el predial", "Liga de C7 no verificada"));
  }

  private static byte[] photo() throws Exception {
    BufferedImage img = new BufferedImage(1600, 1100, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setPaint(new GradientPaint(0, 0, new Color(0x7A, 0x9A, 0xB8), 0, 1100, new Color(0x5C, 0x7A, 0x4A)));
    g.fillRect(0, 0, 1600, 1100);
    g.dispose();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(img, "jpg", out);
    return out.toByteArray();
  }

  private static void dumpSlides(XMLSlideShow show, byte[] pptx) throws Exception {
    String dir = System.getProperty("feasibility.dump");
    if (dir == null) {
      return;
    }
    dump("presentacion.pptx", pptx);
    int i = 1;
    for (XSLFSlide slide : show.getSlides()) {
      BufferedImage img = new BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = img.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.scale(2, 2);
      slide.draw(g);
      g.dispose();
      ImageIO.write(img, "png", Path.of(dir, "slide-%02d.png".formatted(i++)).toFile());
    }
  }

  private static void dump(String name, byte[] bytes) throws Exception {
    String dir = System.getProperty("feasibility.dump");
    if (dir != null) {
      Files.write(Path.of(dir, name), bytes);
    }
  }
}
