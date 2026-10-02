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
 * mayúsculas, orden de nombre/apellidos, abreviaturas de domicilio
 * ("Av." = "Avenida", "Col." = "Colonia", "#120" = "No. 120") y formato de
 * números, para no llenar el expediente de falsas alarmas.
 *
 * <p>Cada hallazgo es una POSIBLE inconsistencia con severidad:
 * <ul>
 *   <li>INFO: diferencia de forma; casi seguro es el mismo dato (p. ej. una
 *       letra distinta en un folio, probable error de lectura).</li>
 *   <li>WARNING: conviene revisarla (domicilio escrito distinto, superficie
 *       que no cuadra, un copropietario que no aparece).</li>
 *   <li>CRITICAL: evidencia fuerte de que se trata de otra persona u otro
 *       inmueble (CURP totalmente distinta, nombre sin ninguna coincidencia,
 *       escritura donde no aparece ningún propietario). Es la única
 *       severidad que bloquea el envío del contrato a firma.</li>
 * </ul>
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

  /** representative: firma por el titular (apoderado o representante legal). */
  public record DeclaredParticipant(UUID id, boolean owner, String fullName, String rfc, String curp, boolean representative) {

    public DeclaredParticipant(UUID id, boolean owner, String fullName, String rfc, String curp) {
      this(id, owner, fullName, rfc, curp, false);
    }
  }

  public record DeclaredData(List<DeclaredParticipant> participants, String propertyAddress, BigDecimal landArea, BigDecimal builtArea) {}

  /** key identifica el tipo de hallazgo (para no duplicarlo y para cerrarlo si deja de existir). */
  public record Finding(String key, String description, DataConflict.Severity severity) {

    public Finding(String key, String description) {
      this(key, description, DataConflict.Severity.WARNING);
    }
  }

  private static final double AREA_TOLERANCE = 0.02;

  private static final Set<DocumentTypeCode> IDENTITY_TYPES =
      Set.of(DocumentTypeCode.INE, DocumentTypeCode.PASSPORT, DocumentTypeCode.CURP, DocumentTypeCode.TAX_STATUS_CERTIFICATE);

  private static final Set<String> ADDRESS_STOPWORDS =
      Set.copyOf(List.of(
          "CALLE", "C", "AV", "AVE", "AVENIDA", "BLVD", "BOULEVARD", "PRIV", "PRIVADA", "CERRADA", "CDA", "COL", "COLONIA", "FRACC",
          "FRACCIONAMIENTO", "NO", "NUM", "NUMERO", "EXT", "EXTERIOR", "INT", "INTERIOR", "CP", "CODIGO", "POSTAL", "MZ", "MZA",
          "MANZANA", "LT", "LOTE", "DE", "DEL", "LA", "LAS", "EL", "LOS", "Y", "EN", "S", "N", "SN", "MUNICIPIO", "MPIO", "ESTADO",
          "EDO", "MEXICO", "MOR", "MORELOS", "CUERNAVACA", "ESQ", "ESQUINA", "ENTRE", "NUM", "NO", "SIN", "NUMERO", "INTERIOR",
          "DEPTO", "DEPARTAMENTO", "TORRE", "EDIFICIO", "PISO", "ANDADOR", "RETORNO", "CALZADA", "CALZ", "CARRETERA", "CARR", "KM"));

  public static List<Finding> check(List<DocumentFacts> documents, DeclaredData declared) {
    List<Finding> findings = new ArrayList<>();
    checkIdentities(documents, declared, findings);
    checkOwners(documents, declared, findings);
    checkRepresentation(documents, declared, findings);
    checkIdentityAcrossDocuments(documents, declared, findings);
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
        // Sin ningún nombre o apellido en común es otra persona; con alguno, probablemente un nombre incompleto.
        boolean nothingInCommon = commonNameTokens(name, participant.fullName()) == 0;
        findings.add(
            new Finding(
                "identity-name:" + participant.id() + ":" + doc.type(),
                "Posible inconsistencia: el nombre en %s (\"%s\") no coincide del todo con el registrado para %s."
                    .formatted(label, name, participant.fullName()),
                nothingInCommon ? DataConflict.Severity.CRITICAL : DataConflict.Severity.WARNING));
      }
      String curp = doc.get("curp");
      if (curp != null && participant.curp() != null && !alnum(curp).equals(alnum(participant.curp()))) {
        findings.add(
            new Finding(
                "identity-curp:" + participant.id() + ":" + doc.type(),
                "Posible inconsistencia: la CURP en %s (%s) no coincide con la registrada para %s (%s)."
                    .formatted(label, curp, participant.fullName(), participant.curp()),
                codeSeverity(curp, participant.curp())));
      }
      String rfc = doc.get("rfc");
      if (rfc != null && participant.rfc() != null && !alnum(rfc).equals(alnum(participant.rfc()))) {
        findings.add(
            new Finding(
                "identity-rfc:" + participant.id(),
                "Posible inconsistencia: el RFC en %s (%s) no coincide con el registrado para %s (%s)."
                    .formatted(label, rfc, participant.fullName(), participant.rfc()),
                codeSeverity(rfc, participant.rfc())));
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
            case DEED, RPP_REGISTRATION_SLIP, PROPERTY_TAX, ADJUDICATION -> "ownerFullName";
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
        // El predial suele estar a nombre de uno solo de los copropietarios (o de un dueño anterior).
        if (missing.size() == owners.size()) {
          findings.add(
              new Finding(
                  "owners:" + doc.type(),
                  "Posible inconsistencia: el %s está a nombre de \"%s\", que no coincide con ningún propietario registrado (puede ser un dueño anterior)."
                      .formatted(label, ownersText),
                  DataConflict.Severity.WARNING));
        }
      } else if (!missing.isEmpty()) {
        // Ningún propietario registrado aparece en la escritura: probablemente es otro inmueble o la escritura equivocada.
        boolean noneFound = missing.size() == owners.size();
        findings.add(
            new Finding(
                "owners:" + doc.type(),
                "Posible inconsistencia: en %s el propietario aparece como \"%s\"; no se encontró a: %s."
                    .formatted(label, ownersText, String.join(", ", missing)),
                noneFound ? DataConflict.Severity.CRITICAL : DataConflict.Severity.WARNING));
      }
    }
  }

  /**
   * E2E 02/10: a una persona moral representada por Sofía se le cargó el poder
   * que Carlos otorgó a Daniela y no se advertía nada; el contrato habría citado
   * el poder de otra persona. El poder debe otorgarlo un propietario y a favor
   * de quien firma; el acta constitutiva debe ser de la empresa propietaria.
   */
  private static void checkRepresentation(List<DocumentFacts> documents, DeclaredData declared, List<Finding> findings) {
    List<DeclaredParticipant> owners = declared.participants().stream().filter(DeclaredParticipant::owner).toList();
    List<DeclaredParticipant> representatives = declared.participants().stream().filter(DeclaredParticipant::representative).toList();
    for (DocumentFacts doc : documents) {
      if (doc.type() == DocumentTypeCode.POWER_OF_ATTORNEY) {
        String grantor = doc.get("grantorFullName");
        if (grantor != null && !owners.isEmpty() && owners.stream().noneMatch(o -> namesMatch(o.fullName(), grantor) || nameContainedIn(o.fullName(), grantor))) {
          findings.add(
              new Finding(
                  "poa-grantor",
                  "Posible inconsistencia: el poder lo otorga \"%s\", que no es ninguno de los propietarios registrados (%s): podría ser el poder de otra persona."
                      .formatted(grantor, String.join(", ", owners.stream().map(DeclaredParticipant::fullName).toList())),
                  DataConflict.Severity.CRITICAL));
        }
        String attorney = doc.get("attorneyFullName");
        if (attorney != null && !representatives.isEmpty()
            && representatives.stream().noneMatch(r -> namesMatch(r.fullName(), attorney) || nameContainedIn(r.fullName(), attorney))) {
          findings.add(
              new Finding(
                  "poa-attorney",
                  "Posible inconsistencia: el poder es a favor de \"%s\", pero quien firmará es %s."
                      .formatted(attorney, String.join(", ", representatives.stream().map(DeclaredParticipant::fullName).toList())),
                  DataConflict.Severity.CRITICAL));
        }
      }
      if (doc.type() == DocumentTypeCode.INCORPORATION_DEED) {
        String company = doc.get("companyName");
        if (company != null && !owners.isEmpty() && owners.stream().noneMatch(o -> namesMatch(o.fullName(), company))) {
          findings.add(
              new Finding(
                  "incorporation-company",
                  "Posible inconsistencia: el acta constitutiva es de \"%s\", distinta de la empresa registrada (%s)."
                      .formatted(company, owners.getFirst().fullName()),
                  DataConflict.Severity.CRITICAL));
        }
        String representative = doc.get("legalRepresentativeFullName");
        if (representative != null && !representatives.isEmpty()
            && representatives.stream().noneMatch(r -> namesMatch(r.fullName(), representative) || nameContainedIn(r.fullName(), representative))) {
          // Puede haberse designado después por otro poder: se avisa, no se bloquea.
          findings.add(
              new Finding(
                  "incorporation-representative",
                  "Posible inconsistencia: el acta constitutiva designa a \"%s\" y quien firmará es %s; verifica que tenga poder vigente."
                      .formatted(representative, String.join(", ", representatives.stream().map(DeclaredParticipant::fullName).toList())),
                  DataConflict.Severity.WARNING));
        }
      }
    }
  }

  /**
   * E2E 02/10: de una INE algo borrosa la IA leyó la fecha de nacimiento 1986 en
   * vez de 1985 (y la CURP con 86); la constancia fiscal del mismo expediente
   * tenía la CURP correcta y nada lo advertía. Se cruzan la CURP de los
   * documentos de identidad de cada persona entre sí y la fecha de nacimiento
   * contra la fecha que lleva su CURP.
   */
  private static void checkIdentityAcrossDocuments(List<DocumentFacts> documents, DeclaredData declared, List<Finding> findings) {
    Map<UUID, String> names = new LinkedHashMap<>();
    declared.participants().forEach(p -> names.put(p.id(), p.fullName()));
    Map<UUID, Map<String, String>> curps = new LinkedHashMap<>();
    Map<UUID, Map<String, String>> birthDates = new LinkedHashMap<>();
    for (DocumentFacts doc : documents) {
      if (!IDENTITY_TYPES.contains(doc.type()) || doc.participantId() == null) {
        continue;
      }
      String label = DocumentTypeLabels.of(doc.type());
      if (doc.get("curp") != null) {
        curps.computeIfAbsent(doc.participantId(), k -> new LinkedHashMap<>()).put(label, doc.get("curp"));
      }
      if (doc.get("birthDate") != null) {
        birthDates.computeIfAbsent(doc.participantId(), k -> new LinkedHashMap<>()).put(label, doc.get("birthDate"));
      }
    }
    curps.forEach(
        (participant, byDoc) -> {
          List<String> distinct = byDoc.values().stream().map(DocumentConsistencyChecker::alnum).distinct().toList();
          if (distinct.size() > 1) {
            boolean close = true;
            for (int i = 1; i < distinct.size(); i++) {
              close &= editDistance(distinct.getFirst(), distinct.get(i)) <= 2;
            }
            findings.add(
                new Finding(
                    "identity-curp-docs:" + participant,
                    "Posible inconsistencia: la CURP de %s se leyó distinta en sus documentos: %s."
                        .formatted(names.getOrDefault(participant, "un participante"), describe(byDoc)),
                    close ? DataConflict.Severity.WARNING : DataConflict.Severity.CRITICAL));
          }
        });
    birthDates.forEach(
        (participant, byDoc) -> {
          Map<String, String> participantCurps = curps.getOrDefault(participant, Map.of());
          for (var birth : byDoc.entrySet()) {
            String yymmdd = yymmdd(birth.getValue());
            if (yymmdd == null) {
              continue;
            }
            for (var curp : participantCurps.entrySet()) {
              String digits = alnum(curp.getValue());
              if (digits.length() >= 10 && !digits.substring(4, 10).equals(yymmdd)) {
                findings.add(
                    new Finding(
                        "identity-birthdate:" + participant + ":" + birth.getKey(),
                        "Posible inconsistencia: la fecha de nacimiento de %s leída en %s (%s) no coincide con la de su CURP en %s (%s)."
                            .formatted(names.getOrDefault(participant, "un participante"), birth.getKey(), birth.getValue(), curp.getKey(), curp.getValue()),
                        DataConflict.Severity.WARNING));
                break;
              }
            }
          }
        });
  }

  /** "1985-03-14" o "14/03/1985" -> "850314"; null si no se reconoce. */
  static String yymmdd(String date) {
    Matcher iso = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})").matcher(date.strip());
    if (iso.find()) {
      return iso.group(1).substring(2) + iso.group(2) + iso.group(3);
    }
    Matcher dmy = Pattern.compile("^(\\d{1,2})/(\\d{1,2})/(\\d{4})").matcher(date.strip());
    if (dmy.find()) {
      return dmy.group(3).substring(2) + "%02d%02d".formatted(Integer.parseInt(dmy.group(2)), Integer.parseInt(dmy.group(1)));
    }
    return null;
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
            // Nunca CRITICAL: las escrituras suelen describir el inmueble por lote y manzana, no por calle y número.
            findings.add(
                new Finding(
                    "address:" + doc.type(),
                    "Posible inconsistencia: el domicilio del inmueble en %s (\"%s\") está escrito distinto al registrado (\"%s\")."
                        .formatted(DocumentTypeLabels.of(doc.type()), address, declared.propertyAddress()),
                    addressOverlap(address, declared.propertyAddress()) >= 0.4 ? DataConflict.Severity.INFO : DataConflict.Severity.WARNING));
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
    List<String> normalized = byDocument.values().stream().map(DocumentConsistencyChecker::alnum).distinct().toList();
    if (normalized.size() > 1) {
      // Una o dos letras distintas suelen ser un error de lectura; más, un dato realmente distinto.
      boolean nearlyEqual = true;
      for (int i = 1; i < normalized.size(); i++) {
        nearlyEqual &= editDistance(normalized.getFirst(), normalized.get(i)) <= 2;
      }
      findings.add(
          new Finding(
              key,
              "Posible inconsistencia: %s distinta entre documentos: %s.".formatted(label, describe(byDocument)),
              nearlyEqual ? DataConflict.Severity.INFO : DataConflict.Severity.WARNING));
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
      findings.add(new Finding(key, "Posible inconsistencia: %s distinta: %s.".formatted(label, describe(raw)), DataConflict.Severity.WARNING));
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

  static int commonNameTokens(String a, String b) {
    Set<String> common = new HashSet<>(nameTokens(a));
    common.retainAll(nameTokens(b));
    return common.size();
  }

  /** CURP/RFC: hasta 2 caracteres distintos es probable error de lectura (WARNING); más, otra persona (CRITICAL). */
  static DataConflict.Severity codeSeverity(String a, String b) {
    return editDistance(alnum(a), alnum(b)) <= 2 ? DataConflict.Severity.WARNING : DataConflict.Severity.CRITICAL;
  }

  /** Proporción de palabras significativas del domicilio en común (0 a 1). */
  static double addressOverlap(String a, String b) {
    Set<String> ta = addressTokens(a);
    Set<String> tb = addressTokens(b);
    if (ta.isEmpty() || tb.isEmpty()) {
      return 0;
    }
    Set<String> common = new HashSet<>(ta);
    common.retainAll(tb);
    return (double) common.size() / Math.min(ta.size(), tb.size());
  }

  static int editDistance(String a, String b) {
    int[] previous = new int[b.length() + 1];
    int[] current = new int[b.length() + 1];
    for (int j = 0; j <= b.length(); j++) {
      previous[j] = j;
    }
    for (int i = 1; i <= a.length(); i++) {
      current[0] = i;
      for (int j = 1; j <= b.length(); j++) {
        int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
        current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
      }
      int[] swap = previous;
      previous = current;
      current = swap;
    }
    return previous[b.length()];
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
