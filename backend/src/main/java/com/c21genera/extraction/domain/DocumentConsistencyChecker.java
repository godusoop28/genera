package com.c21genera.extraction.domain;

import com.c21genera.shared.domain.DocumentTypeCode;
import com.c21genera.shared.domain.DocumentTypeLabels;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prueba de consistencia entre documentos y contra los datos capturados
 * (retroalimentación 25/09, hallazgo 15). Determinística, sin IA: compara
 * los campos ya extraídos (o confirmados por el staff) de la versión vigente
 * de cada documento.
 *
 * <p>Compara:
 * <ul>
 *   <li>Nombre, CURP y RFC de cada participante contra sus identificaciones
 *       y su constancia fiscal.</li>
 *   <li>Que todos los propietarios registrados aparezcan en la escritura /
 *       contrato privado / boleta del RPP, y al menos uno en el predial.</li>
 *   <li>Domicilio del inmueble en cada documento contra el capturado.</li>
 *   <li>Clave catastral (predial vs. plano catastral) y folio real
 *       (escritura / contrato privado vs. boleta del RPP).</li>
 *   <li>Superficie de terreno y de construcción entre documentos y contra lo
 *       capturado para el Anexo A.</li>
 * </ul>
 * Nunca decide cuál valor es el correcto: solo reporta la diferencia para
 * que una persona la revise. Las comparaciones son tolerantes a acentos,
 * mayúsculas, orden de nombre/apellidos, abreviaturas de domicilio y
 * formato de números, para no llenar el expediente de falsas alarmas.
 */
public final class DocumentConsistencyChecker {

  private DocumentConsistencyChecker() {}

  /** Campos vigentes de un documento (valor confirmado por staff si existe; si no, el detectado). */
  public record DocumentFacts(UUID documentId, DocumentTypeCode type, UUID participantId, Map<String, String> fields) {

    String get(String field) {
      String value = fields.get(field);
      return value == null || value.isBlank() ? null : value.strip();
    }
  }

  public record DeclaredParticipant(UUID id, boolean owner, String fullName, String rfc, String curp) {}

  public record DeclaredData(List<DeclaredParticipant> participants, String propertyAddress, BigDecimal landArea, BigDecimal builtArea) {}

  /** key identifica el tipo de hallazgo (para no duplicarlo y para cerrarlo si deja de existir). */
  public record Finding(String key, String description) {}

  private static final double AREA_TOLERANCE = 0.02;

  private static final Set<DocumentTypeCode> IDENTITY_TYPES =
      Set.of(DocumentTypeCode.INE, DocumentTypeCode.PASSPORT, DocumentTypeCode.CURP, DocumentTypeCode.TAX_STATUS_CERTIFICATE);

  private static final Set<String> ADDRESS_STOPWORDS =
      Set.of(
          "CALLE", "C", "AV", "AVE", "AVENIDA", "BLVD", "BOULEVARD", "PRIV", "PRIVADA", "CERRADA", "CDA", "COL", "COLONIA", "FRACC",
          "FRACCIONAMIENTO", "NO", "NUM", "NUMERO", "EXT", "EXTERIOR", "INT", "INTERIOR", "CP", "CODIGO", "POSTAL", "MZ", "MZA",
          "MANZANA", "LT", "LOTE", "DE", "DEL", "LA", "LAS", "EL", "LOS", "Y", "EN", "S", "N", "SN", "MUNICIPIO", "MPIO", "ESTADO",
          "EDO", "MEXICO", "MOR", "MORELOS", "CUERNAVACA");

  public static List<Finding> check(List<DocumentFacts> documents, DeclaredData declared) {
    List<Finding> findings = new ArrayList<>();
    checkIdentities(documents, declared, findings);
    checkOwners(documents, declared, findings);
    checkAddresses(documents, declared, findings);
    checkSameValue(documents, "cadastral-key", "Clave catastral", "cadastralKey", Set.of(DocumentTypeCode.PROPERTY_TAX, DocumentTypeCode.CADASTRAL_PLAN), findings);
    checkSameValue(
        documents,
        "registry-folio",
        "Folio real / datos registrales",
        "publicRegistryFolio",
        Set.of(DocumentTypeCode.DEED, DocumentTypeCode.PRIVATE_CONTRACT, DocumentTypeCode.RPP_REGISTRATION_SLIP),
        findings);
    checkArea(documents, "land-area", "Superficie de terreno", "landArea", declared.landArea(), findings);
    checkArea(documents, "built-area", "Superficie de construcción", "builtArea", declared.builtArea(), findings);
    return findings;
  }

  // ---------------------------------------------------------------------

  private static void checkIdentities(List<DocumentFacts> documents, DeclaredData declared, List<Finding> findings) {
    Map<UUID, DeclaredParticipant> byId = new LinkedHashMap<>();
    declared.participants().forEach(p -> byId.put(p.id(), p));

    for (DocumentFacts doc : documents) {
      if (!IDENTITY_TYPES.contains(doc.type()) || doc.participantId() == null) {
        continue;
      }
      DeclaredParticipant participant = byId.get(doc.participantId());
      if (participant == null) {
        continue;
      }
      String label = DocumentTypeLabels.of(doc.type());
      String name = doc.get("fullName");
      if (name != null && !namesMatch(name, participant.fullName())) {
        findings.add(
            new Finding(
                "identity-name:" + participant.id() + ":" + doc.type(),
                "El nombre en %s (\"%s\") no coincide con el registrado para %s.".formatted(label, name, participant.fullName())));
      }
      String curp = doc.get("curp");
      if (curp != null && participant.curp() != null && !alnum(curp).equals(alnum(participant.curp()))) {
        findings.add(
            new Finding(
                "identity-curp:" + participant.id() + ":" + doc.type(),
                "La CURP en %s (%s) no coincide con la registrada para %s (%s)."
                    .formatted(label, curp, participant.fullName(), participant.curp())));
      }
      String rfc = doc.get("rfc");
      if (rfc != null && participant.rfc() != null && !alnum(rfc).equals(alnum(participant.rfc()))) {
        findings.add(
            new Finding(
                "identity-rfc:" + participant.id(),
                "El RFC en %s (%s) no coincide con el registrado para %s (%s)."
                    .formatted(label, rfc, participant.fullName(), participant.rfc())));
      }
    }
  }

  private static void checkOwners(List<DocumentFacts> documents, DeclaredData declared, List<Finding> findings) {
    List<DeclaredParticipant> owners = declared.participants().stream().filter(DeclaredParticipant::owner).toList();
    if (owners.isEmpty()) {
      return;
    }
    for (DocumentFacts doc : documents) {
      String field =
          switch (doc.type()) {
            case DEED, RPP_REGISTRATION_SLIP, PROPERTY_TAX -> "ownerFullName";
            case PRIVATE_CONTRACT -> "buyerFullName";
            default -> null;
          };
      if (field == null) {
        continue;
      }
      String ownersText = doc.get(field);
      if (ownersText == null) {
        continue;
      }
      String label = DocumentTypeLabels.of(doc.type());
      List<String> missing = owners.stream().filter(o -> !nameContainedIn(o.fullName(), ownersText)).map(DeclaredParticipant::fullName).toList();

      if (doc.type() == DocumentTypeCode.PROPERTY_TAX) {
        // El predial suele estar a nombre de uno solo de los copropietarios.
        if (missing.size() == owners.size()) {
          findings.add(
              new Finding(
                  "owners:" + doc.type(),
                  "El %s está a nombre de \"%s\", que no coincide con ningún propietario registrado.".formatted(label, ownersText)));
        }
      } else if (!missing.isEmpty()) {
        findings.add(
            new Finding(
                "owners:" + doc.type(),
                "En %s el propietario aparece como \"%s\"; no se encontró a: %s."
                    .formatted(label, ownersText, String.join(", ", missing))));
      }
    }
  }

  private static void checkAddresses(List<DocumentFacts> documents, DeclaredData declared, List<Finding> findings) {
    if (declared.propertyAddress() == null || declared.propertyAddress().isBlank()) {
      return;
    }
    for (DocumentFacts doc : documents) {
      switch (doc.type()) {
        case DEED, PROPERTY_TAX, CADASTRAL_PLAN, RPP_REGISTRATION_SLIP, CONDOMINIUM_REGIME, PRIVATE_CONTRACT -> {
          String address = doc.get("propertyAddress");
          if (address != null && !addressesMatch(address, declared.propertyAddress())) {
            findings.add(
                new Finding(
                    "address:" + doc.type(),
                    "El domicilio del inmueble en %s (\"%s\") no coincide con el registrado (\"%s\")."
                        .formatted(DocumentTypeLabels.of(doc.type()), address, declared.propertyAddress())));
          }
        }
        default -> {
          /* domicilios personales: no tienen por qué coincidir con el del inmueble */
        }
      }
    }
  }

  private static void checkSameValue(
      List<DocumentFacts> documents, String key, String label, String field, Set<DocumentTypeCode> types, List<Finding> findings) {
    Map<String, String> byDocument = new LinkedHashMap<>();
    for (DocumentFacts doc : documents) {
      if (types.contains(doc.type()) && doc.get(field) != null) {
        byDocument.put(DocumentTypeLabels.of(doc.type()), doc.get(field));
      }
    }
    Set<String> normalized = new HashSet<>();
    byDocument.values().forEach(v -> normalized.add(alnum(v)));
    if (normalized.size() > 1) {
      findings.add(new Finding(key, "%s distinta entre documentos: %s.".formatted(label, describe(byDocument))));
    }
  }

  private static void checkArea(
      List<DocumentFacts> documents, String key, String label, String field, BigDecimal declaredValue, List<Finding> findings) {
    Map<String, BigDecimal> values = new LinkedHashMap<>();
    Map<String, String> raw = new LinkedHashMap<>();
    if (declaredValue != null) {
      values.put("capturada en el expediente", declaredValue);
      raw.put("capturada en el expediente", declaredValue.stripTrailingZeros().toPlainString() + " m²");
    }
    for (DocumentFacts doc : documents) {
      String value = doc.get(field);
      BigDecimal parsed = parseNumber(value);
      if (parsed != null && parsed.signum() > 0) {
        values.put(DocumentTypeLabels.of(doc.type()), parsed);
        raw.put(DocumentTypeLabels.of(doc.type()), value);
      }
    }
    if (values.size() < 2) {
      return;
    }
    BigDecimal min = values.values().stream().min(BigDecimal::compareTo).orElseThrow();
    BigDecimal max = values.values().stream().max(BigDecimal::compareTo).orElseThrow();
    double difference = max.subtract(min).doubleValue();
    if (difference > 1.0 && difference / max.doubleValue() > AREA_TOLERANCE) {
      findings.add(new Finding(key, "%s distinta: %s.".formatted(label, describe(raw))));
    }
  }

  // ---------------------------------------------------------------------
  // Normalización
  // ---------------------------------------------------------------------

  static boolean namesMatch(String a, String b) {
    Set<String> ta = nameTokens(a);
    Set<String> tb = nameTokens(b);
    if (ta.isEmpty() || tb.isEmpty()) {
      return true;
    }
    Set<String> common = new HashSet<>(ta);
    common.retainAll(tb);
    int smaller = Math.min(ta.size(), tb.size());
    return common.size() >= Math.min(2, smaller) && (double) common.size() / smaller >= 0.8;
  }

  /** ¿El nombre de la persona aparece dentro de un texto que puede listar a varios propietarios? */
  static boolean nameContainedIn(String personName, String text) {
    Set<String> person = nameTokens(personName);
    Set<String> haystack = nameTokens(text);
    if (person.isEmpty()) {
      return true;
    }
    long present = person.stream().filter(haystack::contains).count();
    return present >= Math.min(2, person.size()) && (double) present / person.size() >= 0.8;
  }

  static boolean addressesMatch(String a, String b) {
    Set<String> ta = addressTokens(a);
    Set<String> tb = addressTokens(b);
    Set<String> numbersA = numbers(a);
    Set<String> numbersB = numbers(b);
    if (!numbersA.isEmpty() && !numbersB.isEmpty()) {
      Set<String> commonNumbers = new HashSet<>(numbersA);
      commonNumbers.retainAll(numbersB);
      if (commonNumbers.isEmpty()) {
        return false;
      }
    }
    if (ta.isEmpty() || tb.isEmpty()) {
      return true;
    }
    Set<String> common = new HashSet<>(ta);
    common.retainAll(tb);
    return (double) common.size() / Math.min(ta.size(), tb.size()) >= 0.6;
  }

  private static Set<String> nameTokens(String value) {
    Set<String> tokens = new HashSet<>();
    for (String token : plain(value).split(" ")) {
      if (token.length() > 1 && !Set.of("DE", "DEL", "LA", "LAS", "LOS", "Y", "SA", "CV", "RL", "SAPI").contains(token)) {
        tokens.add(token);
      }
    }
    return tokens;
  }

  private static Set<String> addressTokens(String value) {
    Set<String> tokens = new HashSet<>();
    for (String token : plain(value).split(" ")) {
      if (token.length() > 1 && !ADDRESS_STOPWORDS.contains(token) && !token.chars().allMatch(Character::isDigit)) {
        tokens.add(token);
      }
    }
    return tokens;
  }

  private static final Pattern NUMBER = Pattern.compile("\\d+");

  /** Números "de domicilio" (número exterior, lote...), sin el código postal. */
  private static Set<String> numbers(String value) {
    Set<String> result = new HashSet<>();
    Matcher m = NUMBER.matcher(plain(value));
    while (m.find()) {
      String n = m.group().replaceFirst("^0+(?=\\d)", "");
      if (n.length() < 5) {
        result.add(n);
      }
    }
    return result;
  }

  private static String plain(String value) {
    if (value == null) {
      return "";
    }
    String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    return withoutAccents.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9Ñ ]", " ").replaceAll("\\s+", " ").strip();
  }

  private static String alnum(String value) {
    return plain(value).replace(" ", "");
  }

  static BigDecimal parseNumber(String value) {
    if (value == null) {
      return null;
    }
    Matcher m = Pattern.compile("\\d[\\d,]*(\\.\\d+)?").matcher(value);
    if (!m.find()) {
      return null;
    }
    try {
      return new BigDecimal(m.group().replace(",", ""));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String describe(Map<String, String> byDocument) {
    List<String> parts = new ArrayList<>();
    byDocument.forEach((label, value) -> parts.add(label + ": \"" + value + "\""));
    return String.join("; ", parts);
  }
}
