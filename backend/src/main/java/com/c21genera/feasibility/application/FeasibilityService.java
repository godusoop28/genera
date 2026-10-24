package com.c21genera.feasibility.application;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.documents.DocumentsApi.CurrentDocumentView;
import com.c21genera.expedientes.ExpedienteLifecycleApi;
import com.c21genera.expedientes.ExpedienteLifecycleApi.ManualClientDataView;
import com.c21genera.expedientes.ExpedienteLifecycleApi.PropertyProfile;
import com.c21genera.expedientes.ExpedienteSummary;
import com.c21genera.expedientes.OperationType;
import com.c21genera.extraction.ExtractionApi;
import com.c21genera.extraction.ExtractionApi.DocumentFieldView;
import com.c21genera.feasibility.domain.FeasibilityPromptVersion.PromptKey;
import com.c21genera.feasibility.domain.FeasibilityReport;
import com.c21genera.feasibility.domain.FeasibilityReport.Status;
import com.c21genera.feasibility.domain.PresentationData;
import com.c21genera.feasibility.domain.PresentationData.ComparableListing;
import com.c21genera.feasibility.infrastructure.FeasibilityDocxRenderer;
import com.c21genera.feasibility.infrastructure.FeasibilityPptxRenderer;
import com.c21genera.feasibility.infrastructure.FeasibilityPptxRenderer.Distance;
import com.c21genera.feasibility.infrastructure.FeasibilityProperties;
import com.c21genera.feasibility.infrastructure.FeasibilityReportRepository;
import com.c21genera.feasibility.infrastructure.Geocoder;
import com.c21genera.feasibility.infrastructure.Geocoder.LatLng;
import com.c21genera.feasibility.infrastructure.OpenAiBackgroundClient;
import com.c21genera.feasibility.infrastructure.OpenAiBackgroundClient.Citation;
import com.c21genera.feasibility.infrastructure.OpenAiBackgroundClient.ResponseState;
import com.c21genera.feasibility.infrastructure.StaticMapRenderer;
import com.c21genera.shared.domain.ConflictException;
import com.c21genera.shared.domain.DocumentTypeCode;
import com.c21genera.shared.domain.DocumentTypeLabels;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.storage.FileStorage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Factibilidad comercial con IA a partir de lo que entregó el cliente.
 *
 * <ol>
 *   <li>{@link #request}: guarda la solicitud (precio, notas y fotos del asesor) en ANALYZING.
 *   <li>{@link FeasibilityPoller} llama a {@link #advance} cada pocos segundos: envía la etapa 1
 *       (prompt "Analista de Factibilidad 3.0" con los PDFs del inmueble, los datos leídos de los
 *       documentos y búsqueda web), luego la etapa 2 (prompt "Presentación de factibilidad", con la
 *       factibilidad como fuente principal y salida JSON) y al final arma el Word y el PowerPoint.
 * </ol>
 *
 * Las peticiones a la IA corren en segundo plano en OpenAI: el servidor solo consulta su estado,
 * así no ocupa el único hilo de trabajos durante los minutos que tarda el análisis.
 */
@Service
public class FeasibilityService {

  private static final Logger log = LoggerFactory.getLogger(FeasibilityService.class);
  private static final ZoneId MX = ZoneId.of("America/Mexico_City");
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-MX"));
  private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  private static final String PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
  static final int MAX_PHOTOS = 10;

  /** Documentos del inmueble que se adjuntan como PDF, en orden de utilidad para el análisis. */
  private static final List<DocumentTypeCode> ATTACH_ORDER =
      List.of(
          DocumentTypeCode.PROPERTY_TAX,
          DocumentTypeCode.CADASTRAL_PLAN,
          DocumentTypeCode.APPRAISAL,
          DocumentTypeCode.LAND_USE,
          DocumentTypeCode.CONDOMINIUM_REGIME,
          DocumentTypeCode.LIEN_CERTIFICATE,
          DocumentTypeCode.MORTGAGE,
          DocumentTypeCode.LEASE_AGREEMENT,
          DocumentTypeCode.DEED,
          DocumentTypeCode.PRIVATE_CONTRACT,
          DocumentTypeCode.ADJUDICATION);

  /** Datos leídos que describen al inmueble (no a las personas). */
  private static final Set<String> PROPERTY_FIELDS =
      Set.of(
          "propertyAddress", "cadastralKey", "landArea", "builtArea", "undividedLandArea", "undividedPercentage", "unitNumber", "paidPeriod",
          "appraisalDate", "appraisedValue", "permittedUse", "issueDate", "issuingAuthority", "hasLiens", "deedDate", "registryDate",
          "amount", "isCancelled", "rentAmount", "startDate", "endDate", "contractDate");

  private final FeasibilityReportRepository repository;
  private final ExpedienteLifecycleApi expedientes;
  private final DocumentsApi documents;
  private final ExtractionApi extraction;
  private final FileStorage fileStorage;
  private final OpenAiBackgroundClient ai;
  private final FeasibilityProperties properties;
  private final Geocoder geocoder;
  private final StaticMapRenderer mapRenderer;
  private final FeasibilityDocxRenderer docxRenderer;
  private final FeasibilityPromptService prompts;
  private final FeasibilityPptxRenderer pptxRenderer;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public FeasibilityService(
      FeasibilityReportRepository repository,
      ExpedienteLifecycleApi expedientes,
      DocumentsApi documents,
      ExtractionApi extraction,
      FileStorage fileStorage,
      OpenAiBackgroundClient ai,
      FeasibilityProperties properties,
      Geocoder geocoder,
      StaticMapRenderer mapRenderer,
      FeasibilityDocxRenderer docxRenderer,
      FeasibilityPptxRenderer pptxRenderer,
      FeasibilityPromptService prompts,
      ObjectMapper objectMapper,
      Clock clock) {
    this.repository = repository;
    this.expedientes = expedientes;
    this.documents = documents;
    this.extraction = extraction;
    this.fileStorage = fileStorage;
    this.ai = ai;
    this.properties = properties;
    this.geocoder = geocoder;
    this.mapRenderer = mapRenderer;
    this.docxRenderer = docxRenderer;
    this.pptxRenderer = pptxRenderer;
    this.prompts = prompts;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  public boolean available() {
    return ai.configured() && model() != null && !model().isBlank();
  }

  /** Foto que subió el asesor (ya validada como imagen por el controlador). */
  public record Photo(String filename, byte[] content) {}

  /**
   * Lo que el asesor captura al pedir la factibilidad; se guarda tal cual en input_json.
   * operation: SALE (venta) o RENT (renta); askingPrice es el precio de venta o la renta mensual.
   */
  public record RequestInput(BigDecimal askingPrice, String notes, int photoCount, OperationType operation) {

    public boolean rent() {
      return operation == OperationType.RENT;
    }
  }

  /** operation null = la operación del expediente. */
  public FeasibilityReport request(
      UUID expedienteId, OperationType operation, BigDecimal askingPrice, String notes, List<Photo> photos, UUID userId, String userName) {
    if (!available()) {
      throw new UnprocessableException("AI_NOT_CONFIGURED", "La inteligencia artificial no está configurada en el servidor; no se puede generar la factibilidad.");
    }
    if (repository.existsByExpedienteIdAndStatusIn(expedienteId, List.of(Status.ANALYZING, Status.PRESENTING))) {
      throw new ConflictException("FEASIBILITY_IN_PROGRESS", "Ya se está generando una factibilidad para este expediente. Espera a que termine.");
    }
    if (photos.size() > MAX_PHOTOS) {
      throw new UnprocessableException("TOO_MANY_PHOTOS", "Se aceptan como máximo " + MAX_PHOTOS + " fotografías.");
    }
    expedientes.getSummary(expedienteId); // 404 si no existe
    int version = repository.findFirstByExpedienteIdOrderByVersionNumberDesc(expedienteId).map(r -> r.getVersionNumber() + 1).orElse(1);
    List<String> photoKeys = new ArrayList<>();
    for (int i = 0; i < photos.size(); i++) {
      byte[] jpeg = normalizePhoto(photos.get(i).content(), 2000);
      String key = "expedientes/%s/feasibility/v%d/foto-%d.jpg".formatted(expedienteId, version, i + 1);
      photoKeys.add(fileStorage.store(key, new ByteArrayInputStream(jpeg), jpeg.length, "image/jpeg").storageKey());
    }
    OperationType op = operation != null ? operation : expedientes.getPropertyProfile(expedienteId).operationType();
    RequestInput input = new RequestInput(askingPrice, notes == null ? null : notes.strip(), photos.size(), op == null ? OperationType.SALE : op);
    FeasibilityReport report = new FeasibilityReport(expedienteId, version, userId, userName, json(input), json(photoKeys), clock.instant());
    return repository.save(report);
  }

  public List<FeasibilityReport> listOf(UUID expedienteId) {
    return repository.findByExpedienteIdOrderByVersionNumberDesc(expedienteId);
  }

  public FeasibilityReport get(UUID reportId) {
    return repository.findById(reportId).orElseThrow(() -> new NotFoundException("Factibilidad", reportId));
  }

  public List<FeasibilityReport> inProgress() {
    return repository.findByStatusInOrderByRequestedAtAsc(List.of(Status.ANALYZING, Status.PRESENTING));
  }

  /** Un paso de la máquina de estados; nunca lanza: un error deja la factibilidad en FAILED con el motivo. */
  public void advance(FeasibilityReport report) {
    try {
      if (report.getAiResponseId() == null) {
        if (report.getStatus() == Status.ANALYZING) {
          submitAnalysis(report);
        } else {
          fail(report, "Se perdió la petición de la presentación; vuelve a generar la factibilidad.");
        }
        return;
      }
      if (Duration.between(report.getStageStartedAt(), clock.instant()).compareTo(properties.stageTimeout()) > 0) {
        ai.cancel(report.getAiResponseId());
        fail(report, "La IA tardó más de " + properties.stageTimeout().toMinutes() + " minutos; vuelve a intentarlo.");
        return;
      }
      ResponseState state = ai.get(report.getAiResponseId());
      if (!state.finished()) {
        return;
      }
      if (!state.completed() || state.text().isBlank()) {
        fail(report, "La IA no completó " + (report.getStatus() == Status.ANALYZING ? "el análisis" : "la presentación") + ": " + (state.error() != null ? state.error() : state.status()));
        return;
      }
      if (report.getStatus() == Status.ANALYZING) {
        analysisDone(report, state);
      } else {
        presentationDone(report, state);
      }
    } catch (OpenAiBackgroundClient.AiException e) {
      // Red o 5xx al consultar: se reintenta en la siguiente vuelta hasta el tiempo máximo de la etapa.
      log.warn("Factibilidad {}: error temporal con la IA: {}", report.getId(), e.getMessage());
      if (report.getAiResponseId() == null) {
        fail(report, e.getMessage());
      }
    } catch (Exception | OutOfMemoryError e) {
      log.error("Factibilidad {} falló", report.getId(), e);
      fail(report, "No se pudo completar la factibilidad: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
    }
  }

  // ------------------------------------------------------------------ etapa 1: análisis

  private void submitAnalysis(FeasibilityReport report) {
    UUID expedienteId = report.getExpedienteId();
    RequestInput input = read(report.getInputJson(), RequestInput.class);
    List<String> fileIds = new ArrayList<>();
    List<String> attached = new ArrayList<>();
    List<String> skipped = new ArrayList<>();
    long total = 0;
    List<CurrentDocumentView> current = documents.currentDocumentsOf(expedienteId);
    for (DocumentTypeCode type : ATTACH_ORDER) {
      for (CurrentDocumentView doc : current) {
        if (!type.name().equals(doc.type())) {
          continue;
        }
        String label = nonBlank(doc.name(), DocumentTypeLabels.of(type));
        if (doc.pdfStorageKey() == null) {
          skipped.add(label + " (todavía se está procesando)");
          continue;
        }
        byte[] pdf = readLimited(doc.pdfStorageKey(), properties.maxFileBytes());
        if (pdf == null || total + pdf.length > properties.maxTotalFileBytes()) {
          skipped.add(label + " (archivo demasiado grande para adjuntarlo; se usan los datos ya leídos de él)");
          continue;
        }
        total += pdf.length;
        fileIds.add(ai.uploadFile(type.name().toLowerCase(Locale.ROOT) + ".pdf", pdf, "application/pdf"));
        attached.add(label);
      }
    }
    String context = context(expedienteId, input, attached, skipped);

    List<Object> content = new ArrayList<>();
    content.add(Map.of("type", "input_text", "text", context));
    fileIds.forEach(id -> content.add(Map.of("type", "input_file", "file_id", id)));
    content.addAll(photoInputs(report));

    Map<String, Object> body = baseRequest(properties.maxOutputTokensReport());
    FeasibilityPromptService.Instructions instructions = prompts.instructions(PromptKey.ANALYST);
    body.put("instructions", instructions.text() + FeasibilityPrompts.operationRules(input.rent()));
    report.usedAnalystPrompt(instructions.version());
    body.put("input", List.of(Map.of("role", "user", "content", content)));
    String responseId = ai.createInBackground(body);
    report.analysisSubmitted(responseId, model(), json(fileIds));
    repository.save(report);
    log.info("Factibilidad {}: análisis enviado ({} documentos adjuntos, {} fotos)", report.getId(), fileIds.size(), input.photoCount());
  }

  private void analysisDone(FeasibilityReport report, ResponseState state) {
    releaseFiles(report);
    RequestInput input = read(report.getInputJson(), RequestInput.class);
    String context = context(report.getExpedienteId(), input, List.of(), List.of());
    List<Object> content = new ArrayList<>();
    content.add(
        Map.of(
            "type",
            "input_text",
            "text",
            context + "\n\n# FACTIBILIDAD INMOBILIARIA (FUENTE PRINCIPAL)\n\n" + state.text()));
    content.addAll(photoInputs(report));
    Map<String, Object> body = baseRequest(properties.maxOutputTokensPresentation());
    FeasibilityPromptService.Instructions instructions = prompts.instructions(PromptKey.PRESENTATION);
    body.put("instructions", instructions.text() + FeasibilityPrompts.operationRules(input.rent()));
    report.usedPresentationPrompt(instructions.version());
    body.put("input", List.of(Map.of("role", "user", "content", content)));
    body.put("text", Map.of("format", Map.of("type", "json_schema", "name", "presentacion_factibilidad", "schema", PresentationData.SCHEMA, "strict", true)));
    String responseId = ai.createInBackground(body);
    report.analysisCompleted(state.text(), json(state.citations()), responseId, clock.instant());
    repository.save(report);
    log.info("Factibilidad {}: análisis listo, presentación enviada", report.getId());
  }

  // ------------------------------------------------------------------ etapa 2: presentación y archivos

  private void presentationDone(FeasibilityReport report, ResponseState state) throws IOException {
    PresentationData data = objectMapper.readValue(state.text(), PresentationData.class).normalized();
    ExpedienteSummary summary = expedientes.getSummary(report.getExpedienteId());
    PropertyProfile profile = expedientes.getPropertyProfile(report.getExpedienteId());
    boolean rent = read(report.getInputJson(), RequestInput.class).rent();

    // Ubicaciones: el inmueble por su domicilio; los comparables por sus coordenadas publicadas o su colonia.
    LatLng subject = locateSubject(summary, data);
    boolean subjectApproximate = subject != null && !subject.precise();
    if (subject != null && data.property() != null) {
      var p = data.property();
      data =
          data.withProperty(
              new PresentationData.PropertyContext(
                  p.title(), p.location(), p.surfaces(), p.distribution(), p.condition(), p.amenities(), p.highlights(), subject.lat(), subject.lng(),
                  subjectApproximate ? PresentationData.APPROXIMATE : PresentationData.EXACT));
    }
    LatLng anchor = marketAnchor(data);
    List<ComparableListing> located = new ArrayList<>();
    for (ComparableListing c : PresentationData.safe(data.comparables())) {
      located.add(locate(c, subject, anchor));
    }
    data = data.withComparables(located);
    List<String> unlocated = located.stream().filter(c -> !c.located()).map(c -> c.id() + " (" + nonBlank(c.zone(), "sin zona") + ")").toList();
    if (!unlocated.isEmpty()) {
      List<String> pending = new ArrayList<>(PresentationData.safe(data.pendingItems()));
      pending.add("Sin ubicación verificable para el mapa: " + String.join(", ", unlocated) + ". Confirmar colonia o dirección del anuncio.");
      data = data.withPendingItems(pending);
    }
    if (subject == null) {
      List<String> pending = new ArrayList<>(PresentationData.safe(data.pendingItems()));
      pending.add("No se pudo ubicar el inmueble en el mapa con su domicilio; las distancias no se calcularon. Revisar el domicilio del expediente.");
      data = data.withPendingItems(pending);
    }

    Map<String, Distance> distances = new LinkedHashMap<>();
    List<StaticMapRenderer.Point> points = new ArrayList<>();
    for (ComparableListing c : located) {
      if (!c.located()) {
        continue;
      }
      boolean approximate = PresentationData.APPROXIMATE.equals(c.locationPrecision());
      points.add(new StaticMapRenderer.Point(c.id(), c.group(), c.latitude(), c.longitude(), approximate));
      if (subject != null) {
        distances.put(c.id(), new Distance(Geocoder.distanceKm(subject.lat(), subject.lng(), c.latitude(), c.longitude()), approximate || subjectApproximate));
      }
    }
    StaticMapRenderer.Point subjectPoint = subject == null ? null : new StaticMapRenderer.Point("Inmueble", "SUBJECT", subject.lat(), subject.lng(), subjectApproximate);
    byte[] map = mapRenderer.render(subjectPoint, points);
    String mapsUrl =
        subject != null
            ? "https://www.google.com/maps/search/?api=1&query=" + subject.lat() + "," + subject.lng()
            : summary.propertyAddress() != null ? "https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(summary.propertyAddress(), StandardCharsets.UTF_8) : null;

    List<byte[]> photos = loadPhotos(report);
    String dateText = DATE.format(report.getRequestedAt().atZone(MX));
    String advisor = profile.assignedAdvisorName();

    byte[] pptx =
        pptxRenderer.render(
            new FeasibilityPptxRenderer.Deck(data, summary.folio(), dateText, advisor, photos, map, mapsUrl, distances, subject != null, subjectApproximate, rent));
    photos = null; // libera memoria antes del Word
    List<Citation> sources = mergeSources(read(report.getSourcesJson(), new TypeReference<List<Citation>>() {}), state.citations(), data);
    byte[] docx =
        docxRenderer.render(
            new FeasibilityDocxRenderer.Cover(data.property() == null ? null : data.property().title(), summary.folio(), summary.propertyAddress(), advisor, dateText, rent),
            report.getReportMarkdown(),
            sources);

    String prefix = "expedientes/%s/feasibility/v%d/".formatted(report.getExpedienteId(), report.getVersionNumber());
    String base = "factibilidad-" + summary.folio().replaceAll("[^A-Za-z0-9-]", "");
    String docxKey = fileStorage.store(prefix + base + ".docx", new ByteArrayInputStream(docx), docx.length, DOCX).storageKey();
    String pptxKey = fileStorage.store(prefix + "presentacion-" + base + ".pptx", new ByteArrayInputStream(pptx), pptx.length, PPTX).storageKey();
    report.completed(json(data), json(sources), docxKey, pptxKey, clock.instant());
    repository.save(report);
    log.info("Factibilidad {}: lista ({} comparables, {} en el mapa)", report.getId(), located.size(), points.size());
  }

  /** Máximo para considerar que un punto pertenece al mercado analizado (Morelos y alrededores). */
  private static final double MARKET_RADIUS_KM = 90;

  /**
   * El inmueble: su domicilio (y variantes limpias), luego las coordenadas o la ubicación que dio la
   * IA. Un resultado lejos de la zona de la oficina (p. ej. una colonia homónima en otro estado) se
   * descarta salvo que los comparables también estén por allá.
   */
  private LatLng locateSubject(ExpedienteSummary summary, PresentationData data) {
    var p = data.property();
    List<LatLng> candidates = new ArrayList<>();
    geocoder.locate(summary.propertyAddress()).ifPresent(candidates::add);
    if (p != null && p.latitude() != null && p.longitude() != null && !PresentationData.NO_LOCATION.equals(p.locationPrecision())) {
      candidates.add(new LatLng(p.latitude(), p.longitude(), PresentationData.EXACT.equals(p.locationPrecision())));
    }
    if (p != null) {
      geocoder.locate(p.location()).map(l -> new LatLng(l.lat(), l.lng(), false)).ifPresent(candidates::add);
    }
    LatLng anchor = marketAnchor(data);
    return candidates.stream()
        .filter(c -> Geocoder.distanceKm(anchor.lat(), anchor.lng(), c.lat(), c.lng()) < MARKET_RADIUS_KM)
        .findFirst()
        .orElse(candidates.isEmpty() ? null : candidates.get(0));
  }

  /** Centro del mercado: la mediana de las coordenadas que dio la IA para los comparables, o Cuernavaca. */
  private static LatLng marketAnchor(PresentationData data) {
    List<double[]> points =
        PresentationData.safe(data.comparables()).stream()
            .filter(c -> c.latitude() != null && c.longitude() != null)
            .map(c -> new double[] {c.latitude(), c.longitude()})
            .toList();
    if (points.size() < 3) {
      return new LatLng(Geocoder.HOME_LAT, Geocoder.HOME_LNG, false);
    }
    double[] lats = points.stream().mapToDouble(x -> x[0]).sorted().toArray();
    double[] lngs = points.stream().mapToDouble(x -> x[1]).sorted().toArray();
    LatLng median = new LatLng(lats[lats.length / 2], lngs[lngs.length / 2], false);
    // Si la IA dio coordenadas disparatadas, mejor la oficina.
    return Geocoder.distanceKm(median.lat(), median.lng(), Geocoder.HOME_LAT, Geocoder.HOME_LNG) < 400 ? median : new LatLng(Geocoder.HOME_LAT, Geocoder.HOME_LNG, false);
  }

  /**
   * Un comparable: coordenadas publicadas (EXACTA) si son verosímiles; si no, su dirección o su
   * colonia geolocalizadas (APROXIMADA); si no, el centro aproximado de la colonia que dio la IA.
   * Verosímil = a menos de 90 km del inmueble (o del centro del mercado si el inmueble no se ubicó).
   * Sin nada que ubicar: sin marcador, y queda anotado como pendiente.
   */
  private ComparableListing locate(ComparableListing c, LatLng subject, LatLng anchor) {
    LatLng reference = subject != null ? subject : anchor;
    java.util.function.Predicate<LatLng> plausible = l -> Geocoder.distanceKm(reference.lat(), reference.lng(), l.lat(), l.lng()) < MARKET_RADIUS_KM;
    LatLng published = c.latitude() != null && c.longitude() != null && !PresentationData.NO_LOCATION.equals(c.locationPrecision()) ? new LatLng(c.latitude(), c.longitude(), false) : null;
    if (published != null && PresentationData.EXACT.equals(c.locationPrecision()) && plausible.test(published)) {
      return c.withLocation(published.lat(), published.lng(), PresentationData.EXACT);
    }
    for (String query : new String[] {c.address(), c.zone()}) {
      if (query == null || query.isBlank()) {
        continue;
      }
      var found = geocoder.locate(query).filter(plausible);
      if (found.isPresent()) {
        return c.withLocation(found.get().lat(), found.get().lng(), PresentationData.APPROXIMATE);
      }
    }
    if (published != null && plausible.test(published)) {
      return c.withLocation(published.lat(), published.lng(), PresentationData.APPROXIMATE);
    }
    return c.withLocation(null, null, PresentationData.NO_LOCATION);
  }

  private static List<Citation> mergeSources(List<Citation> first, List<Citation> second, PresentationData data) {
    Map<String, Citation> all = new LinkedHashMap<>();
    for (List<Citation> list : List.of(first == null ? List.<Citation>of() : first, second)) {
      list.forEach(c -> all.putIfAbsent(c.url(), c));
    }
    for (ComparableListing c : PresentationData.safe(data.comparables())) {
      if (c.url() != null && !c.url().isBlank()) {
        all.putIfAbsent(c.url(), new Citation(c.id() + " · " + nonBlank(c.zone(), "") + " · " + nonBlank(c.source(), ""), c.url()));
      }
    }
    return new ArrayList<>(all.values());
  }

  // ------------------------------------------------------------------ contexto para la IA

  /** Todo lo que el sistema sabe del inmueble, sin datos personales del propietario. */
  String context(UUID expedienteId, RequestInput input, List<String> attached, List<String> skipped) {
    ExpedienteSummary s = expedientes.getSummary(expedienteId);
    ManualClientDataView m = expedientes.getManualData(expedienteId);
    PropertyProfile p = expedientes.getPropertyProfile(expedienteId);
    StringBuilder b = new StringBuilder();
    b.append("# DATOS DEL INMUEBLE PARA LA FACTIBILIDAD\n\n");
    b.append("Fecha de consulta: ").append(DATE.format(clock.instant().atZone(MX))).append(". Oficina: CENTURY 21 Genera, Cuernavaca, Morelos.\n\n");
    b.append("## Expediente\n");
    line(b, "Folio", s.folio());
    line(b, "Clave del inmueble", p.propertyKey());
    line(b, "Tipo de inmueble", joinNonBlank(" / ", p.categoryName(), p.subtypeName(), p.typeDetail()));
    line(b, "Operación a analizar", input.rent() ? "RENTA (arrendamiento mensual)" : "VENTA");
    line(b, "Domicilio del inmueble", s.propertyAddress());
    line(b, "Referencia del asesor", p.propertyReference());
    line(b, "Régimen de condominio", s.condominiumRegime() ? "Sí" : "No");
    line(b, "Situación jurídica declarada", s.declaredLegalStatus() == null ? null : s.declaredLegalStatus().name().replace('_', ' ').toLowerCase(Locale.ROOT));
    line(b, "Forma de acreditar la propiedad", s.accreditationType() == null ? null : s.accreditationType().name());

    b.append("\n## Datos capturados por el asesor\n");
    BigDecimal asking = input.askingPrice() != null ? input.askingPrice() : m.authorizedPrice();
    if (input.rent()) {
      // El precio autorizado del contrato es de venta: en renta solo cuenta lo que capture el asesor.
      line(b, "Renta mensual solicitada por el propietario (MXN)", input.askingPrice() == null ? null : input.askingPrice().toPlainString());
    } else {
      line(b, "Precio de venta solicitado por el propietario (MXN)", asking == null ? null : asking.toPlainString());
    }
    line(b, "Superficie de terreno (m²)", m.landAreaM2() == null ? null : m.landAreaM2().toPlainString());
    line(b, "Superficie de construcción (m²)", m.builtAreaM2() == null ? null : m.builtAreaM2().toPlainString());
    line(b, "Recámaras", m.bedrooms() == null ? null : m.bedrooms().toString());
    line(b, "Baños", m.bathrooms() == null ? null : m.bathrooms().toString());
    line(b, "Estacionamientos", m.parkingSpots() == null ? null : m.parkingSpots().toString());
    line(b, "Estado de conservación", m.conservationStatus());
    line(b, "Servicios disponibles", m.availableServices());
    line(b, "Características relevantes", m.relevantFeatures());

    List<DocumentFieldView> fields =
        extraction.documentFieldsOf(expedienteId).stream()
            .filter(f -> PROPERTY_FIELDS.contains(f.fieldName()) || isPropertyExtra(f))
            .toList();
    if (!fields.isEmpty()) {
      b.append("\n## Datos leídos de los documentos del expediente\n");
      Map<String, List<DocumentFieldView>> byDocument = fields.stream().collect(Collectors.groupingBy(f -> nonBlank(f.documentName(), f.documentType()), LinkedHashMap::new, Collectors.toList()));
      byDocument.forEach(
          (doc, list) -> {
            b.append("### ").append(doc).append('\n');
            list.forEach(f -> b.append("- ").append(f.fieldName()).append(": ").append(f.value()).append(f.confirmed() ? " (confirmado por el staff)" : " (leído por IA)").append('\n'));
          });
    }
    if (!attached.isEmpty()) {
      b.append("\n## Documentos del inmueble adjuntos en PDF\n");
      attached.forEach(a -> b.append("- ").append(a).append('\n'));
    }
    if (!skipped.isEmpty()) {
      b.append("\n## Documentos no adjuntados\n");
      skipped.forEach(a -> b.append("- ").append(a).append('\n'));
    }
    if (input.photoCount() > 0) {
      b.append("\n## Fotografías\nSe adjuntan ").append(input.photoCount()).append(" fotografías reales del inmueble tomadas por el asesor.\n");
    } else {
      b.append("\n## Fotografías\nNo se adjuntaron fotografías del inmueble.\n");
    }
    if (input.notes() != null && !input.notes().isBlank()) {
      b.append("\n## Notas del asesor para esta factibilidad\n").append(input.notes()).append('\n');
    }
    return b.toString();
  }

  private static boolean isPropertyExtra(DocumentFieldView f) {
    if (!f.fieldName().startsWith("extra.")) {
      return false;
    }
    String key = f.fieldName().toLowerCase(Locale.ROOT);
    boolean personal = key.contains("nombre") || key.contains("name") || key.contains("curp") || key.contains("rfc") || key.contains("electoral") || key.contains("teléfono") || key.contains("correo");
    boolean propertyDoc = ATTACH_ORDER.stream().anyMatch(t -> t.name().equals(f.documentType()));
    return propertyDoc && !personal;
  }

  private Map<String, Object> baseRequest(int maxOutputTokens) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", model());
    body.put("tools", List.of(Map.of("type", "web_search", "search_context_size", "high", "user_location", Map.of("type", "approximate", "country", "MX", "region", "Morelos"))));
    body.put("max_output_tokens", maxOutputTokens);
    if (properties.reasoningEffort() != null && !properties.reasoningEffort().isBlank()) {
      body.put("reasoning", Map.of("effort", properties.reasoningEffort()));
    }
    return body;
  }

  private String model() {
    return properties.model() != null && !properties.model().isBlank() ? properties.model() : ai.defaultModel();
  }

  /** Las fotos del asesor reducidas para la IA (bastan 1024 px para ver estado y acabados). */
  private List<Object> photoInputs(FeasibilityReport report) {
    List<Object> inputs = new ArrayList<>();
    for (byte[] photo : loadPhotos(report)) {
      byte[] small = normalizePhoto(photo, 1024);
      inputs.add(Map.of("type", "input_image", "image_url", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(small), "detail", "low"));
    }
    return inputs;
  }

  private List<byte[]> loadPhotos(FeasibilityReport report) {
    List<String> keys = report.getPhotoKeys() == null ? List.of() : read(report.getPhotoKeys(), new TypeReference<List<String>>() {});
    List<byte[]> photos = new ArrayList<>();
    for (String key : keys) {
      try (InputStream in = fileStorage.get(key)) {
        photos.add(in.readAllBytes());
      } catch (Exception e) {
        log.warn("Factibilidad {}: no se pudo leer la foto {}", report.getId(), key);
      }
    }
    return photos;
  }

  /** null si el archivo supera el límite. */
  private byte[] readLimited(String key, long max) {
    try (InputStream in = fileStorage.get(key)) {
      byte[] bytes = in.readNBytes((int) max + 1);
      return bytes.length > max ? null : bytes;
    } catch (IOException e) {
      log.warn("No se pudo leer {} para la factibilidad: {}", key, e.getMessage());
      return null;
    }
  }

  private void releaseFiles(FeasibilityReport report) {
    if (report.getAiFileIds() == null) {
      return;
    }
    read(report.getAiFileIds(), new TypeReference<List<String>>() {}).forEach(ai::deleteFile);
    report.aiFilesReleased();
  }

  private void fail(FeasibilityReport report, String reason) {
    try {
      releaseFiles(report);
    } catch (RuntimeException ignored) {
      // Lo importante es dejar registrado el fallo.
    }
    report.failed(reason, clock.instant());
    repository.save(report);
    log.warn("Factibilidad {} falló: {}", report.getId(), reason);
  }

  /** JPEG RGB de hasta maxSide px por lado (las fotos de celular llegan en HEIC/PNG/JPEG enormes). */
  static byte[] normalizePhoto(byte[] image, int maxSide) {
    try {
      BufferedImage src = ImageIO.read(new ByteArrayInputStream(image));
      if (src == null) {
        throw new UnprocessableException("INVALID_PHOTO", "Una de las fotografías no se pudo leer como imagen (usa JPG o PNG).");
      }
      double scale = Math.min(1.0, maxSide / (double) Math.max(src.getWidth(), src.getHeight()));
      int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
      int h = Math.max(1, (int) Math.round(src.getHeight() * scale));
      BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = out.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.setColor(java.awt.Color.WHITE);
      g.fillRect(0, 0, w, h);
      g.drawImage(src, 0, 0, w, h, null);
      g.dispose();
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      ImageIO.write(out, "jpg", bytes);
      return bytes.toByteArray();
    } catch (IOException e) {
      throw new UnprocessableException("INVALID_PHOTO", "Una de las fotografías no se pudo leer como imagen (usa JPG o PNG).");
    }
  }

  private static void line(StringBuilder b, String label, String value) {
    b.append("- ").append(label).append(": ").append(value == null || value.isBlank() ? "Dato pendiente por confirmar" : value.strip()).append('\n');
  }

  private static String joinNonBlank(String sep, String... values) {
    return java.util.Arrays.stream(values).filter(v -> v != null && !v.isBlank()).collect(Collectors.joining(sep));
  }

  private static String nonBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private <T> T read(String json, Class<T> type) {
    try {
      return objectMapper.readValue(json, type);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private <T> T read(String json, TypeReference<T> type) {
    if (json == null) {
      return null;
    }
    try {
      return objectMapper.readValue(json, type);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
