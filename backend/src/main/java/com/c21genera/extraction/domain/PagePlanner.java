package com.c21genera.extraction.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Decide qué páginas de un documento se le muestran a la IA y en qué orden,
 * para que la información se encuentre aunque esté en la página 60 de una
 * escritura de 80, sin mandar 80 imágenes a ciegas (ver AGENTS §38).
 *
 * <ul>
 *   <li>Si el PDF tiene texto nativo, las páginas se ordenan por relevancia
 *       (palabras clave de los campos buscados) y se manda su TEXTO; solo la
 *       primera página va además como imagen para identificar el documento.</li>
 *   <li>Si es un escaneo (sin texto), las páginas se revisan como imagen en
 *       lotes: primero el inicio (número, fecha, notario), luego el final
 *       (inscripción registral, sellos) y después el resto en orden. Quien
 *       llama se detiene en cuanto tiene todos los campos con buena confianza.</li>
 * </ul>
 * Puramente determinístico: no llama a la IA.
 */
public final class PagePlanner {

  private PagePlanner() {}

  /** Texto nativo mínimo por página para considerar el PDF "con texto" (no escaneado). */
  static final int MIN_TEXT_CHARS_PER_PAGE = 200;

  /** Lote de páginas (números desde 1) a analizar juntas. */
  public record Batch(List<Integer> pages) {}

  public record Plan(boolean textMode, List<Batch> batches, int pagesTotal) {

    public int pagesPlanned() {
      return (int) batches.stream().flatMap(b -> b.pages().stream()).distinct().count();
    }
  }

  /** Palabras que delatan en qué página está cada dato. */
  private static final Map<String, List<String>> KEYWORDS =
      Map.ofEntries(
          Map.entry("deedNumber", List.of("ESCRITURA", "INSTRUMENTO", "VOLUMEN", "NUMERO")),
          Map.entry("instrumentNumber", List.of("INSTRUMENTO", "ESCRITURA", "VOLUMEN")),
          Map.entry("deedDate", List.of("FECHA", "DIAS DEL MES", "A LOS")),
          Map.entry("instrumentDate", List.of("FECHA", "DIAS DEL MES")),
          Map.entry("notaryName", List.of("NOTARIO", "NOTARIA", "CORREDOR", "LICENCIADO")),
          Map.entry("notaryNumber", List.of("NOTARIA", "NOTARIO PUBLICO NUMERO")),
          Map.entry("notaryPlace", List.of("NOTARIA", "ESTADO DE", "MUNICIPIO")),
          Map.entry("ownerFullName", List.of("COMPRADOR", "ADQUIRENTE", "PROPIETARIO", "COMPARECE", "PARTE COMPRADORA")),
          Map.entry("propertyAddress", List.of("UBICADO", "INMUEBLE", "LOTE", "CALLE", "COLONIA", "PREDIO")),
          Map.entry("publicRegistryFolio", List.of("FOLIO REAL", "REGISTRO PUBLICO", "INSCRIPCION", "INSCRITO", "FOLIO")),
          Map.entry("registryDate", List.of("INSCRIPCION", "REGISTRO PUBLICO")),
          Map.entry("landArea", List.of("SUPERFICIE", "METROS CUADRADOS", "M2", "TERRENO")),
          Map.entry("builtArea", List.of("CONSTRUCCION", "CONSTRUIDA", "SUPERFICIE")),
          Map.entry("companyName", List.of("DENOMINACION", "RAZON SOCIAL", "SOCIEDAD")),
          Map.entry("mercantileFolio", List.of("FOLIO MERCANTIL", "REGISTRO PUBLICO DE COMERCIO")),
          Map.entry("grantorFullName", List.of("PODERDANTE", "OTORGA", "PODER")),
          Map.entry("attorneyFullName", List.of("APODERADO", "PODER")),
          Map.entry("hasLiens", List.of("GRAVAMEN", "GRAVAMENES", "HIPOTECA", "LIBRE")),
          Map.entry("sellerFullName", List.of("VENDEDOR", "PARTE VENDEDORA", "ENAJENANTE")),
          Map.entry("buyerFullName", List.of("COMPRADOR", "PARTE COMPRADORA")),
          Map.entry("heirFullNames", List.of("HEREDERO", "LEGATARIO", "HEREDEROS")),
          Map.entry("executorFullName", List.of("ALBACEA")),
          Map.entry("deceasedFullName", List.of("DE CUJUS", "AUTOR DE LA SUCESION", "FINADO", "FALLECIO")));

  /**
   * @param pageTexts texto nativo de cada página (índice 0 = página 1); null
   *     o vacío si el PDF no tiene texto
   * @param batchSize páginas por lote en modo imagen
   * @param maxPages tope de páginas a analizar (para acotar costo y tiempo)
   */
  public static Plan plan(int pagesTotal, List<String> pageTexts, List<String> fieldNames, boolean longDocument, int batchSize, int maxPages) {
    if (pagesTotal <= 0) {
      return new Plan(false, List.of(), 0);
    }
    int budget = Math.max(1, Math.min(pagesTotal, maxPages));
    if (!longDocument) {
      // Identificaciones, recibos, actas: casi nunca más de unas pocas páginas.
      return new Plan(false, List.of(new Batch(range(1, Math.min(pagesTotal, Math.max(batchSize, 4))))), pagesTotal);
    }
    if (hasNativeText(pageTexts, pagesTotal)) {
      List<Integer> ranked = rankByRelevance(pageTexts, fieldNames);
      return new Plan(true, List.of(new Batch(ranked.subList(0, Math.min(ranked.size(), budget)))), pagesTotal);
    }
    return new Plan(false, imageBatches(pagesTotal, batchSize, budget), pagesTotal);
  }

  static boolean hasNativeText(List<String> pageTexts, int pagesTotal) {
    if (pageTexts == null || pageTexts.isEmpty()) {
      return false;
    }
    long withText = pageTexts.stream().filter(t -> t != null && t.strip().length() >= MIN_TEXT_CHARS_PER_PAGE).count();
    return withText >= Math.max(1, Math.round(pagesTotal * 0.6));
  }

  /** Inicio, final y luego el resto en orden; sin repetir páginas y sin pasar del presupuesto. */
  static List<Batch> imageBatches(int pagesTotal, int batchSize, int budget) {
    LinkedHashSet<Integer> order = new LinkedHashSet<>();
    order.addAll(range(1, Math.min(pagesTotal, batchSize)));
    int tail = Math.max(1, batchSize / 2);
    order.addAll(range(Math.max(1, pagesTotal - tail + 1), pagesTotal));
    order.addAll(range(1, pagesTotal));
    List<Integer> pages = new ArrayList<>(order).subList(0, Math.min(order.size(), budget));

    List<Batch> batches = new ArrayList<>();
    // El primer lote es el inicio del documento; el segundo, el final.
    List<Integer> first = pages.stream().filter(p -> p <= batchSize).toList();
    batches.add(new Batch(first));
    List<Integer> rest = pages.stream().filter(p -> p > batchSize).toList();
    List<Integer> lastPages = rest.stream().filter(p -> p > pagesTotal - tail).toList();
    if (!lastPages.isEmpty()) {
      batches.add(new Batch(lastPages));
    }
    List<Integer> middle = rest.stream().filter(p -> p <= pagesTotal - tail).toList();
    for (int i = 0; i < middle.size(); i += batchSize) {
      batches.add(new Batch(middle.subList(i, Math.min(middle.size(), i + batchSize))));
    }
    return batches;
  }

  /** Páginas de mayor a menor relevancia; la primera página siempre va primero (identifica el documento). */
  static List<Integer> rankByRelevance(List<String> pageTexts, List<String> fieldNames) {
    Set<String> keywords = new LinkedHashSet<>();
    for (String field : fieldNames) {
      keywords.addAll(KEYWORDS.getOrDefault(field, List.of()));
    }
    List<Integer> pages = new ArrayList<>(IntStream.rangeClosed(1, pageTexts.size()).boxed().toList());
    double[] scores = new double[pageTexts.size() + 1];
    for (int page : pages) {
      String text = plain(pageTexts.get(page - 1));
      double score = 0;
      for (String keyword : keywords) {
        score += count(text, keyword);
      }
      scores[page] = score;
    }
    pages.sort(Comparator.comparingDouble((Integer p) -> -scores[p]).thenComparingInt(p -> p));
    pages.remove(Integer.valueOf(1));
    pages.addFirst(1);
    return pages;
  }

  private static int count(String text, String keyword) {
    int count = 0;
    int index = text.indexOf(keyword);
    while (index >= 0 && count < 20) {
      count++;
      index = text.indexOf(keyword, index + keyword.length());
    }
    return count;
  }

  private static String plain(String value) {
    if (value == null) {
      return "";
    }
    return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
  }

  private static List<Integer> range(int from, int to) {
    return from > to ? List.of() : IntStream.rangeClosed(from, to).boxed().toList();
  }
}
