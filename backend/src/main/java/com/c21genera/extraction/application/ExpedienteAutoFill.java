package com.c21genera.extraction.application;

import com.c21genera.expedientes.ExpedienteLifecycleApi;
import com.c21genera.expedientes.ExpedienteLifecycleApi.DocumentFill;
import com.c21genera.expedientes.ExpedienteSummary;
import com.c21genera.expedientes.ExpedienteSummary.ParticipantView;
import com.c21genera.extraction.application.DocumentFieldExtractionService.DocumentObservation;
import com.c21genera.extraction.domain.ExtractedFieldObservation;
import com.c21genera.extraction.infrastructure.DataConflictRepository;
import com.c21genera.shared.domain.ReviewDecision;
import com.c21genera.shared.events.DocumentEvents.DocumentContentAssessed;
import com.c21genera.shared.events.DocumentEvents.DocumentReviewed;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Control de Cambios #7: lo que la IA leyó y ya se validó llena solo el contrato y los datos de
 * cada participante, sin volver a capturarlo. "Validado" = el documento ya se aceptó en la
 * revisión (o una persona confirmó el dato), el dato tiene confianza alta y no hay una
 * inconsistencia sin resolver sobre ese mismo dato. Solo se llena lo vacío: nunca se pisa algo
 * capturado o corregido por una persona. Misma correspondencia documento→dato que el prellenado
 * de la web (frontend/src/lib/detected-data.ts).
 */
@Service
public class ExpedienteAutoFill {

  private static final Logger log = LoggerFactory.getLogger(ExpedienteAutoFill.class);
  static final double MIN_CONFIDENCE = 0.8;

  private record Target(String kind, String key, boolean date) {}

  private static Target legal(String key) {
    return new Target("legal", key, false);
  }

  private static Target legalDate(String key) {
    return new Target("legal", key, true);
  }

  private static Target area(String key) {
    return new Target("area", key, false);
  }

  private static Target participant(String key) {
    return new Target("participant", key, false);
  }

  private static Target participantDate(String key) {
    return new Target("participant", key, true);
  }

  /** Qué dato del contrato alimenta cada campo de cada tipo de documento. */
  private static final Map<String, Map<String, List<Target>>> MAPPING = new HashMap<>();

  private static void map(String type, String field, Target... targets) {
    MAPPING.computeIfAbsent(type, t -> new HashMap<>()).put(field, List.of(targets));
  }

  static {
    map("DEED", "deedNumber", legal("deed.number"));
    map("DEED", "deedDate", legalDate("deed.date"));
    map("DEED", "notaryName", legal("deed.notaryName"));
    map("DEED", "notaryNumber", legal("deed.notaryNumber"));
    map("DEED", "notaryPlace", legal("deed.notaryPlace"));
    map("DEED", "publicRegistryFolio", legal("deed.registryData"));
    map("DEED", "registryDate", legalDate("deed.registryDate"));
    map("DEED", "landArea", area("landAreaM2"));
    map("DEED", "builtArea", area("builtAreaM2"));

    map("PRIVATE_CONTRACT", "sellerFullName", legal("privateContract.sellerName"));
    map("PRIVATE_CONTRACT", "buyerFullName", legal("privateContract.buyerName"));
    map("PRIVATE_CONTRACT", "contractDate", legalDate("privateContract.date"));
    map("PRIVATE_CONTRACT", "ratificationDate", legalDate("privateContract.ratificationDate"));
    map("PRIVATE_CONTRACT", "notaryName", legal("privateContract.notaryName"));
    map("PRIVATE_CONTRACT", "notaryNumber", legal("privateContract.notaryNumber"));
    map("PRIVATE_CONTRACT", "notaryPlace", legal("privateContract.notaryPlace"));
    map("PRIVATE_CONTRACT", "registryDate", legalDate("privateContract.registryDate"));
    map("PRIVATE_CONTRACT", "publicRegistryFolio", legal("privateContract.realFolio"));
    map("PRIVATE_CONTRACT", "landArea", area("landAreaM2"));

    // La boleta del RPP es la fuente del folio real (Control de Cambios #8).
    map("RPP_REGISTRATION_SLIP", "publicRegistryFolio", legal("deed.registryData"), legal("privateContract.realFolio"));
    map("RPP_REGISTRATION_SLIP", "registryDate", legalDate("privateContract.registryDate"), legalDate("deed.registryDate"));

    map("CONDOMINIUM_REGIME", "deedNumber", legal("condominium.deedNumber"));
    map("CONDOMINIUM_REGIME", "deedDate", legalDate("condominium.date"));
    map("CONDOMINIUM_REGIME", "notaryName", legal("condominium.notaryName"));
    map("CONDOMINIUM_REGIME", "notaryNumber", legal("condominium.notaryNumber"));
    map("CONDOMINIUM_REGIME", "notaryPlace", legal("condominium.notaryPlace"));
    map("CONDOMINIUM_REGIME", "registryDate", legalDate("condominium.registryDate"));
    map("CONDOMINIUM_REGIME", "regimeRegistrationFolio", legal("condominium.realFolio"));

    map("INCORPORATION_DEED", "companyType", legal("company.companyType"));
    map("INCORPORATION_DEED", "instrumentNumber", legal("company.instrumentNumber"));
    map("INCORPORATION_DEED", "instrumentDate", legalDate("company.instrumentDate"));
    map("INCORPORATION_DEED", "notaryTitle", legal("company.notaryTitle"));
    map("INCORPORATION_DEED", "notaryName", legal("company.notaryName"));
    map("INCORPORATION_DEED", "notaryNumber", legal("company.notaryNumber"));
    map("INCORPORATION_DEED", "notaryPlace", legal("company.notaryPlace"));
    map("INCORPORATION_DEED", "commerceRegistryPlace", legal("company.commerceRegistryPlace"));
    map("INCORPORATION_DEED", "mercantileFolio", legal("company.mercantileFolio"));

    map("POWER_OF_ATTORNEY", "instrumentNumber", legal("representation.instrumentNumber"));
    map("POWER_OF_ATTORNEY", "instrumentDate", legalDate("representation.instrumentDate"));
    map("POWER_OF_ATTORNEY", "notaryName", legal("representation.notaryName"));
    map("POWER_OF_ATTORNEY", "notaryNumber", legal("representation.notaryNumber"));
    map("POWER_OF_ATTORNEY", "notaryPlace", legal("representation.notaryPlace"));
    map("POWER_OF_ATTORNEY", "publicRegistryFolio", legal("representation.registryFolio"));

    map("PROPERTY_TAX", "landArea", area("landAreaM2"));
    map("PROPERTY_TAX", "builtArea", area("builtAreaM2"));
    map("CADASTRAL_PLAN", "landArea", area("landAreaM2"));
    map("CADASTRAL_PLAN", "builtArea", area("builtAreaM2"));

    // El espacio "INE" es el de la identificación oficial: puede traer un pasaporte o una tarjeta de residente.
    map("INE", "electorKey", participant("idDocumentNumber"));
    map("INE", "documentNumber", participant("idDocumentNumber"));
    map("INE", "curp", participant("curp"));
    map("INE", "birthDate", participantDate("birthDate"));
    map("INE", "address", participant("address"));
    map("INE", "nationality", participant("nationality"));
    map("PASSPORT", "passportNumber", participant("idDocumentNumber"));
    map("PASSPORT", "nationality", participant("nationality"));
    map("PASSPORT", "birthDate", participantDate("birthDate"));
    map("PASSPORT", "curp", participant("curp"));
    map("CURP", "curp", participant("curp"));
    map("CURP", "birthDate", participantDate("birthDate"));
    map("TAX_STATUS_CERTIFICATE", "rfc", participant("rfc"));
    map("TAX_STATUS_CERTIFICATE", "curp", participant("curp"));
    map("TAX_STATUS_CERTIFICATE", "address", participant("address"));
    map("PROOF_OF_ADDRESS", "address", participant("address"));
  }

  /** Si dos documentos traen el mismo dato, gana el de mayor rango. */
  private static final List<String> PRIORITY =
      List.of(
          "RPP_REGISTRATION_SLIP",
          "DEED",
          "PRIVATE_CONTRACT",
          "CONDOMINIUM_REGIME",
          "INCORPORATION_DEED",
          "POWER_OF_ATTORNEY",
          "INE",
          "PASSPORT",
          "TAX_STATUS_CERTIFICATE",
          "CURP",
          "PROPERTY_TAX",
          "CADASTRAL_PLAN",
          "PROOF_OF_ADDRESS");

  private static final List<String> MONTHS =
      List.of("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre");

  private final DocumentFieldExtractionService extraction;
  private final DataConflictRepository conflicts;
  private final ExpedienteLifecycleApi expedientes;

  public ExpedienteAutoFill(DocumentFieldExtractionService extraction, DataConflictRepository conflicts, ExpedienteLifecycleApi expedientes) {
    this.extraction = extraction;
    this.conflicts = conflicts;
    this.expedientes = expedientes;
  }

  /** Al aceptar un documento, sus datos ya están revisados: se llevan al expediente. */
  @ApplicationModuleListener
  void on(DocumentReviewed event) {
    if (event.decision() == ReviewDecision.ACCEPTED) {
      fillQuietly(event.expedienteId());
    }
  }

  /** Reprocesar un documento ya aceptado puede leer datos que antes faltaban. */
  @ApplicationModuleListener
  void on(DocumentContentAssessed event) {
    fillQuietly(event.expedienteId());
  }

  private void fillQuietly(UUID expedienteId) {
    try {
      fill(expedienteId);
    } catch (RuntimeException e) {
      // Nunca bloquea la revisión: el usuario puede capturar el dato a mano.
      log.warn("No se pudo completar el expediente {} con lo leído en sus documentos: {}", expedienteId, e.toString());
    }
  }

  /** Llena lo vacío con lo validado. Devuelve cuántos datos se llenaron. */
  @Transactional
  public int fill(UUID expedienteId) {
    ExpedienteSummary summary = expedientes.getSummary(expedienteId);
    UUID ownerId =
        summary.participants().stream()
            .filter(p -> "OWNER".equals(p.role()))
            .min(Comparator.comparingInt(ParticipantView::ordinal))
            .map(ParticipantView::id)
            .orElse(null);
    Set<String> conflicted =
        conflicts.findByExpedienteIdAndResolvedFalse(expedienteId).stream().map(c -> c.getFieldName()).collect(Collectors.toSet());
    List<DocumentObservation> validated =
        extraction.observationsOfExpediente(expedienteId).stream().filter(o -> isValidated(o, conflicted)).toList();
    DocumentFill fill = build(validated, ownerId);
    if (fill.legal().isEmpty() && fill.landAreaM2() == null && fill.builtAreaM2() == null && fill.participants().isEmpty()) {
      return 0;
    }
    return expedientes.completeFromDocuments(expedienteId, fill);
  }

  private static boolean isValidated(DocumentObservation o, Set<String> conflicted) {
    ExtractedFieldObservation obs = o.observation();
    if (conflicted.contains(obs.getFieldName())) {
      return false;
    }
    if (obs.getConfirmedValue() != null && !obs.getConfirmedValue().isBlank()) {
      return true;
    }
    String value = obs.getDetectedValue();
    // Control de Cambios #19: con varios valores posibles (antecedentes), una persona elige antes de usarlo.
    if (!obs.getAlternatives().isEmpty()) {
      return false;
    }
    return "ACCEPTED".equals(o.document().status())
        && value != null
        && !value.contains("?")
        && obs.getConfidence() != null
        && obs.getConfidence() >= MIN_CONFIDENCE;
  }

  static DocumentFill build(List<DocumentObservation> observations, UUID ownerId) {
    Map<String, String> legal = new LinkedHashMap<>();
    Map<String, BigDecimal> areas = new HashMap<>();
    Map<UUID, Map<String, String>> participants = new LinkedHashMap<>();
    Set<String> sources = new LinkedHashSet<>();
    Map<UUID, String> idKinds = new HashMap<>();
    Map<UUID, String> idCountries = new HashMap<>();
    Set<UUID> ineHolders = new LinkedHashSet<>();

    for (DocumentObservation o : observations) {
      UUID pid = o.document().participantId() != null ? o.document().participantId() : ownerId;
      if (!"INE".equals(o.document().type().name()) || pid == null) {
        continue;
      }
      String v = valueOf(o.observation());
      if ("identificationType".equals(o.observation().getFieldName()) && v != null) {
        idKinds.put(pid, identificationKind(v));
      }
      if ("issuingCountry".equals(o.observation().getFieldName()) && v != null) {
        idCountries.put(pid, v);
      }
    }

    List<DocumentObservation> sorted = new ArrayList<>(observations);
    sorted.sort(
        Comparator.comparing((DocumentObservation o) -> o.observation().getConfirmedValue() == null)
            .thenComparingInt(o -> rank(o.document().type().name())));
    for (DocumentObservation o : sorted) {
      String type = o.document().type().name();
      List<Target> targets = MAPPING.getOrDefault(type, Map.of()).get(o.observation().getFieldName());
      String raw = valueOf(o.observation());
      if (targets == null || raw == null) {
        continue;
      }
      for (Target target : targets) {
        String value = raw;
        if (target.date()) {
          value = normalizeDate(raw);
          if (value == null) {
            continue;
          }
        }
        switch (target.kind()) {
          case "legal" -> {
            if (legal.putIfAbsent(target.key(), value) == null) {
              sources.add(o.document().displayName());
            }
          }
          case "area" -> {
            BigDecimal area = normalizeArea(raw);
            if (area != null && areas.putIfAbsent(target.key(), area) == null) {
              sources.add(o.document().displayName());
            }
          }
          default -> {
            UUID pid = o.document().participantId() != null ? o.document().participantId() : ownerId;
            if (pid == null) {
              continue;
            }
            Map<String, String> bucket = participants.computeIfAbsent(pid, k -> new LinkedHashMap<>());
            if (bucket.containsKey(target.key())) {
              continue;
            }
            bucket.put(target.key(), value);
            sources.add(o.document().displayName());
            if ("idDocumentNumber".equals(target.key())) {
              String kind = "INE".equals(type) ? idKinds.getOrDefault(pid, "INE") : "PASSPORT".equals(type) ? "PASAPORTE" : null;
              if ("INE".equals(kind)) {
                bucket.putIfAbsent("idDocumentType", "INE");
                bucket.putIfAbsent("idDocumentIssuer", "Instituto Nacional Electoral");
                ineHolders.add(pid);
              } else if ("PASAPORTE".equals(kind) && "PASSPORT".equals(type)) {
                bucket.putIfAbsent("idDocumentType", "PASAPORTE");
                bucket.putIfAbsent("idDocumentIssuer", "Secretaría de Relaciones Exteriores");
              } else if (kind != null && !"OTRA".equals(kind)) {
                // Otra identificación oficial en el espacio de la INE: no implica nacionalidad mexicana.
                bucket.putIfAbsent("idDocumentType", kind);
                String country = idCountries.get(pid);
                if (country != null) {
                  bucket.putIfAbsent("idDocumentIssuer", "Gobierno de " + country);
                }
              }
            }
          }
        }
      }
    }
    // La credencial del INE solo se expide a ciudadanos mexicanos (Control de Cambios #9); un
    // pasaporte u otra identificación emitida por México también lo indica.
    for (UUID pid : ineHolders) {
      participants.computeIfAbsent(pid, k -> new LinkedHashMap<>()).putIfAbsent("nationality", "Mexicana");
    }
    idCountries.forEach(
        (pid, country) -> {
          if (isMexico(country) && participants.containsKey(pid)) {
            participants.get(pid).putIfAbsent("nationality", "Mexicana");
          }
        });
    return new DocumentFill(legal, areas.get("landAreaM2"), areas.get("builtAreaM2"), participants, String.join(", ", sources));
  }

  private static String valueOf(ExtractedFieldObservation o) {
    String v = o.getConfirmedValue() != null && !o.getConfirmedValue().isBlank() ? o.getConfirmedValue() : o.getDetectedValue();
    return v == null || v.isBlank() ? null : v.strip();
  }

  private static int rank(String type) {
    int i = PRIORITY.indexOf(type);
    return i < 0 ? PRIORITY.size() : i;
  }

  private static String plain(String s) {
    return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toUpperCase(java.util.Locale.ROOT);
  }

  /** INE, PASAPORTE, FM2_RESIDENTE, CEDULA_PROFESIONAL u OTRA, según lo que leyó la IA. */
  static String identificationKind(String raw) {
    String text = plain(raw);
    if (Pattern.compile("(^|[^A-Z])(INE|IFE)([^A-Z]|$)|CREDENCIAL PARA VOTAR|ELECTORAL").matcher(text).find()) {
      return "INE";
    }
    if (text.contains("PASAPORTE") || text.contains("PASSPORT")) {
      return "PASAPORTE";
    }
    if (text.contains("RESIDENTE") || text.contains("FM2") || text.contains("FM3")) {
      return "FM2_RESIDENTE";
    }
    if (text.contains("CEDULA PROFESIONAL")) {
      return "CEDULA_PROFESIONAL";
    }
    return "OTRA";
  }

  private static boolean isMexico(String country) {
    return plain(country).strip().matches("MEXICO|ESTADOS UNIDOS MEXICANOS|MEX");
  }

  /** AAAA-MM-DD, DD/MM/AAAA o "15 de marzo de 2010" → AAAA-MM-DD; null si no se reconoce. */
  static String normalizeDate(String raw) {
    String text = raw.strip().toLowerCase(java.util.Locale.ROOT);
    Matcher m = Pattern.compile("^(\\d{4})-(\\d{1,2})-(\\d{1,2})").matcher(text);
    if (m.find()) {
      return valid(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
    }
    m = Pattern.compile("^(\\d{1,2})[/.-](\\d{1,2})[/.-](\\d{4})$").matcher(text);
    if (m.find()) {
      return valid(Integer.parseInt(m.group(3)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(1)));
    }
    m = Pattern.compile("(\\d{1,2})\\s+de\\s+([a-záéíóú]+)\\s+(?:de|del)\\s+(\\d{4})").matcher(text);
    if (m.find()) {
      int month = MONTHS.indexOf(Normalizer.normalize(m.group(2), Normalizer.Form.NFD).replaceAll("\\p{M}", "")) + 1;
      return valid(Integer.parseInt(m.group(3)), month, Integer.parseInt(m.group(1)));
    }
    return null;
  }

  private static String valid(int y, int m, int d) {
    if (y <= 1800 || y >= 2200 || m < 1 || m > 12 || d < 1 || d > 31) {
      return null;
    }
    try {
      return java.time.LocalDate.of(y, m, d).toString();
    } catch (java.time.DateTimeException e) {
      return null;
    }
  }

  /** "1,250.50 m2" → 1250.50; null si no hay número. */
  static BigDecimal normalizeArea(String raw) {
    Matcher m = Pattern.compile("\\d+(?:\\.\\d+)?").matcher(raw.replaceAll(",(?=\\d{3}\\b)", ""));
    if (!m.find()) {
      return null;
    }
    BigDecimal area = new BigDecimal(m.group());
    return area.signum() > 0 ? area : null;
  }
}
