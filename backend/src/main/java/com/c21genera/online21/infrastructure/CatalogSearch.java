package com.c21genera.online21.infrastructure;

import com.c21genera.online21.PropertyCatalog.CatalogProperty;
import com.c21genera.online21.PropertyCatalog.Operation;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Búsqueda por texto sobre el índice local de propiedades. Pura (sin red)
 * para probarla. Puntúa cada palabra de la consulta según dónde aparece
 * (clave > colonia/municipio > tipo > título > descripción) y pondera las
 * palabras raras más que las comunes (IDF), como el catálogo de Inova. Los
 * resultados son CANDIDATAS: el bot nunca afirma cuál vio el cliente sin que
 * lo confirme.
 */
final class CatalogSearch {

  private static final Set<String> STOPWORDS =
      Set.of(
          "de", "del", "la", "las", "el", "los", "en", "y", "o", "a", "un", "una", "con", "por", "para", "que", "me", "mi", "busco",
          "quiero", "algo", "cerca", "zona", "col", "colonia", "hola", "precio", "info", "informacion", "sobre", "esta", "este", "propiedad",
          "venta", "renta", "rentar", "comprar", "vender", "hasta", "millones", "millon", "mil", "pesos", "mxn");

  private static final Pattern MONEY = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(millones|millon|mdp|m\\b|mil|k\\b)?");
  private static final Pattern KEY = Pattern.compile("\\b(\\d{6,7})(?:--\\d+)?\\b");
  private static final Pattern INTERNAL_KEY = Pattern.compile("\\b([A-Za-z]{2,3})[\\s-]?(\\d{1,3}[A-Za-z]?)\\b");

  private CatalogSearch() {}

  static List<CatalogProperty> search(List<CatalogProperty> index, String query, Operation operation, int limit) {
    List<CatalogProperty> pool =
        index.stream().filter(p -> operation == Operation.ANY || (operation == Operation.RENT) == "renta".equalsIgnoreCase(p.operation())).toList();
    if (query == null || query.isBlank() || pool.isEmpty()) {
      return List.of();
    }

    // Referencia exacta (id o clave): gana sobre cualquier coincidencia de texto.
    for (String ref : references(query)) {
      for (CatalogProperty p : pool) {
        if (matchesReference(p, ref)) {
          return List.of(p);
        }
      }
    }

    BigDecimal budget = budgetOf(query);
    List<String> tokens = tokens(query);
    if (tokens.isEmpty() && budget == null) {
      return List.of();
    }
    Map<String, Integer> df = new HashMap<>();
    for (CatalogProperty p : pool) {
      for (String t : new HashSet<>(tokens(haystack(p)))) {
        df.merge(t, 1, Integer::sum);
      }
    }
    int n = pool.size();
    List<Scored> scored = new ArrayList<>();
    for (CatalogProperty p : pool) {
      String place = normalize(join(p.neighborhood(), p.municipality(), p.state()));
      String type = normalize(p.subtype());
      String title = normalize(p.title());
      String description = normalize(p.description());
      double score = 0;
      int hits = 0;
      for (String t : tokens) {
        double idf = Math.log(1.0 + (double) n / (1 + df.getOrDefault(t, 0)));
        double w = 0;
        if (containsWord(place, t)) {
          w = 3;
        } else if (containsWord(type, t)) {
          w = 2;
        } else if (containsWord(title, t)) {
          w = 1.5;
        } else if (containsWord(description, t)) {
          w = 0.5;
        }
        if (w > 0) {
          hits++;
          score += w * idf;
        }
      }
      if (budget != null && p.price() != null) {
        // Dentro del presupuesto suma; muy por encima resta.
        if (p.price().compareTo(budget) <= 0) {
          score += 1.5;
        } else if (p.price().compareTo(budget.multiply(BigDecimal.valueOf(1.15))) > 0) {
          score -= 3;
        }
      }
      if (hits > 0 || (tokens.isEmpty() && budget != null && score > 0)) {
        scored.add(new Scored(p, score));
      }
    }
    return scored.stream()
        .filter(s -> s.score() > 0)
        .sorted(Comparator.comparingDouble(Scored::score).reversed())
        .limit(Math.max(1, limit))
        .map(Scored::property)
        .toList();
  }

  /** Ids (6–7 dígitos, con o sin "--oficina") y claves internas ("GN 14") mencionados en el texto. */
  static List<String> references(String text) {
    Set<String> refs = new LinkedHashSet<>();
    if (text == null) {
      return List.of();
    }
    Matcher m = KEY.matcher(text);
    while (m.find()) {
      refs.add(m.group(1));
    }
    Matcher k = INTERNAL_KEY.matcher(text);
    while (k.find()) {
      String prefix = k.group(1).toUpperCase(Locale.ROOT);
      if (!STOPWORDS.contains(prefix.toLowerCase(Locale.ROOT))) {
        refs.add(prefix + " " + k.group(2).toUpperCase(Locale.ROOT));
      }
    }
    return List.copyOf(refs);
  }

  static boolean matchesReference(CatalogProperty p, String reference) {
    String ref = reference.trim();
    if (ref.matches("\\d{6,7}(--\\d+)?")) {
      return Long.toString(p.id()).equals(ref.replaceAll("--.*", ""));
    }
    return p.internalKey() != null && compactKey(p.internalKey()).equals(compactKey(ref));
  }

  private static String compactKey(String key) {
    return key.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
  }

  static BigDecimal budgetOf(String query) {
    String q = normalize(query);
    if (!q.matches(".*(millon|mdp|mil|hasta|presupuesto|\\$).*")) {
      return null;
    }
    Matcher m = MONEY.matcher(q);
    BigDecimal best = null;
    while (m.find()) {
      BigDecimal value = new BigDecimal(m.group(1).replace(",", "."));
      String unit = m.group(2);
      if (unit != null && (unit.startsWith("millon") || unit.equals("mdp") || unit.equals("m"))) {
        value = value.multiply(BigDecimal.valueOf(1_000_000));
      } else if (unit != null && (unit.equals("mil") || unit.equals("k"))) {
        value = value.multiply(BigDecimal.valueOf(1_000));
      }
      if (value.compareTo(BigDecimal.valueOf(1_000)) >= 0 && (best == null || value.compareTo(best) > 0)) {
        best = value;
      }
    }
    return best;
  }

  static List<String> tokens(String text) {
    List<String> out = new ArrayList<>();
    for (String t : normalize(text).split("[^a-z0-9]+")) {
      if (t.length() >= 3 && !STOPWORDS.contains(t) && !t.matches("\\d+")) {
        out.add(t.endsWith("s") && t.length() > 4 ? t.substring(0, t.length() - 1) : t);
      }
    }
    return out;
  }

  static String normalize(String text) {
    if (text == null) {
      return "";
    }
    return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
  }

  private static boolean containsWord(String haystack, String token) {
    if (haystack.isEmpty()) {
      return false;
    }
    for (String w : haystack.split("[^a-z0-9]+")) {
      String stem = w.endsWith("s") && w.length() > 4 ? w.substring(0, w.length() - 1) : w;
      if (stem.equals(token)) {
        return true;
      }
    }
    return false;
  }

  private static String haystack(CatalogProperty p) {
    return join(p.neighborhood(), p.municipality(), p.state(), p.subtype(), p.title(), p.description());
  }

  private static String join(String... parts) {
    StringBuilder sb = new StringBuilder();
    for (String part : parts) {
      if (part != null) {
        sb.append(part).append(' ');
      }
    }
    return sb.toString();
  }

  private record Scored(CatalogProperty property, double score) {}
}
