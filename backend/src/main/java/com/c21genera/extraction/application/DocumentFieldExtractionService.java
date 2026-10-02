package com.c21genera.extraction.application;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.documents.DocumentsApi.RequirementStatusView;
import com.c21genera.expedientes.ExpedienteLifecycleApi;
import com.c21genera.expedientes.ExpedienteLifecycleApi.ManualClientDataView;
import com.c21genera.expedientes.ExpedienteSummary;
import com.c21genera.extraction.ExtractionApi;
import com.c21genera.extraction.ExtractionApi.ConflictView;
import com.c21genera.extraction.domain.DataConflict;
import com.c21genera.extraction.domain.DocumentConsistencyChecker;
import com.c21genera.extraction.domain.DocumentConsistencyChecker.DeclaredData;
import com.c21genera.extraction.domain.DocumentConsistencyChecker.DeclaredParticipant;
import com.c21genera.extraction.domain.DocumentConsistencyChecker.DocumentFacts;
import com.c21genera.extraction.domain.DocumentConsistencyChecker.Finding;
import com.c21genera.extraction.domain.DocumentFieldSchemas;
import com.c21genera.extraction.domain.ExtractedFieldObservation;
import com.c21genera.extraction.domain.FieldOrigin;
import com.c21genera.extraction.domain.StructuredExtractionProvider;
import com.c21genera.extraction.domain.StructuredExtractionProvider.ContentAssessment;
import com.c21genera.extraction.domain.StructuredExtractionProvider.ExtractionResult;
import com.c21genera.extraction.domain.StructuredExtractionProvider.FieldResult;
import com.c21genera.extraction.infrastructure.DataConflictRepository;
import com.c21genera.extraction.infrastructure.ExtractedFieldObservationRepository;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.events.DocumentEvents.DocumentContentAssessed;
import com.c21genera.shared.events.ExpedienteEvents.ExpedienteDataCorrected;
import com.c21genera.shared.storage.FileStorage;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orquesta, para cada versión de documento ya procesada: la revisión de
 * contenido con IA (¿es el documento solicitado?, ¿es legible?), la
 * extracción de campos, y la prueba de consistencia entre todos los
 * documentos del expediente y los datos capturados (ver AGENTS §38-41).
 */
@Service
public class DocumentFieldExtractionService implements ExtractionApi {

  private final ExtractedFieldObservationRepository observationRepository;
  private final DataConflictRepository conflictRepository;
  private final StructuredExtractionProvider provider;
  private final FileStorage fileStorage;
  private final DocumentsApi documentsApi;
  private final ExpedienteLifecycleApi expedienteApi;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final TransactionTemplate transactions;

  public DocumentFieldExtractionService(
      ExtractedFieldObservationRepository observationRepository,
      DataConflictRepository conflictRepository,
      StructuredExtractionProvider provider,
      FileStorage fileStorage,
      DocumentsApi documentsApi,
      ExpedienteLifecycleApi expedienteApi,
      ApplicationEventPublisher events,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.observationRepository = observationRepository;
    this.conflictRepository = conflictRepository;
    this.provider = provider;
    this.fileStorage = fileStorage;
    this.documentsApi = documentsApi;
    this.expedienteApi = expedienteApi;
    this.events = events;
    this.clock = clock;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  /**
   * La llamada a la IA (puede tardar hasta un par de minutos) se hace fuera
   * de cualquier transacción para no retener una conexión de base de datos;
   * solo el guardado del resultado es transaccional.
   *
   * @throws StructuredExtractionProvider.AiUnavailableException para que el job se reintente
   */
  public void extract(ExtractDocumentFieldsPayload payload) {
    List<String> fieldNames = DocumentFieldSchemas.fieldsFor(payload.type());

    // El PDF se baja a un archivo temporal y se analiza desde el disco: cargarlo en memoria ocupaba su tamaño
    // completo más una copia transitoria (E2E 02/10: 496/512 MB con un PDF de 38.9 MB).
    java.nio.file.Path pdf = downloadToTempFile(payload.pdfStorageKey());
    ExtractionResult result;
    try {
      // Siempre se llama al proveedor, aunque el tipo no tenga campos que
      // extraer: la verificación de que el archivo corresponde al documento
      // solicitado aplica a todos los tipos.
      result = provider.extract(payload.type(), pdf, fieldNames);
    } finally {
      deleteQuietly(pdf);
    }

    transactions.executeWithoutResult(status -> saveResult(payload, result));
  }

  /**
   * Guarda TODO lo leído (también si el archivo parece otro documento, o si
   * solo se leyó una parte): una extracción parcial es válida y una confianza
   * baja significa "revisar", no "descartar". Si la versión ya se había
   * procesado antes (reproceso), lo detectado antes se reemplaza, pero un dato
   * que el staff confirmó o corrigió nunca se pisa.
   */
  void saveResult(ExtractDocumentFieldsPayload payload, ExtractionResult extracted) {
    ExtractionResult result = guardIdentifierFormats(extracted);
    Instant now = clock.instant();
    observationRepository.deleteByDocumentVersionIdAndConfirmedValueIsNull(payload.documentVersionId());
    observationRepository.flush();
    Set<String> confirmed =
        observationRepository.findByDocumentVersionId(payload.documentVersionId()).stream()
            .map(ExtractedFieldObservation::getFieldName)
            .collect(Collectors.toSet());

    for (FieldResult field : result.fields()) {
      if (field.value() == null || field.value().isBlank() || confirmed.contains(field.fieldName())) {
        continue;
      }
      observationRepository.save(
          new ExtractedFieldObservation(
              payload.expedienteId(),
              payload.documentId(),
              payload.documentVersionId(),
              truncate(field.fieldName(), 160),
              field.value(),
              FieldOrigin.AI_EXTRACTED,
              field.confidence(),
              now,
              field.page()));
    }

    List<String> schema = DocumentFieldSchemas.fieldsFor(payload.type());
    Set<String> found = result.fields().stream().map(FieldResult::fieldName).collect(Collectors.toSet());
    found.addAll(confirmed);
    int fieldsFound = (int) schema.stream().filter(found::contains).count();

    ContentAssessment assessment = result.assessment() != null ? result.assessment() : ContentAssessment.unknown();
    events.publishEvent(
        new DocumentContentAssessed(
            payload.expedienteId(),
            payload.documentId(),
            payload.documentVersionId(),
            payload.type(),
            assessment.matchesExpectedType(),
            assessment.legible(),
            assessment.detectedDocumentKind(),
            assessment.observations(),
            false,
            result.warnings(),
            result.pagesAnalyzed(),
            result.pagesTotal(),
            schema.size(),
            fieldsFound));

    runConsistencyCheck(payload.expedienteId());
  }

  /**
   * Una CURP, RFC o clave de elector con formato inválido nunca se presenta como
   * confiable, venga del proveedor que venga: confianza máxima 0.3 ("revisar") y
   * un aviso para el revisor (E2E 02/10).
   */
  static ExtractionResult guardIdentifierFormats(ExtractionResult result) {
    List<FieldResult> fields = new ArrayList<>();
    List<String> warnings = new ArrayList<>(result.warnings());
    for (FieldResult f : result.fields()) {
      if (f.value() != null && f.value().contains("?")) {
        // La IA marcó con "?" un carácter que no distinguió: nunca es un dato confiable.
        fields.add(new FieldResult(f.fieldName(), f.value(), Math.min(f.confidence(), 0.3), f.page()));
        warnings.add("Revisa " + DocumentFieldSchemas.describe(List.of(f.fieldName())).getFirst() + ": tiene caracteres que no se distinguieron (\"" + f.value() + "\")");
        continue;
      }
      var problem = com.c21genera.extraction.domain.FieldFormats.problem(f.fieldName(), f.value());
      if (problem.isPresent()) {
        fields.add(new FieldResult(f.fieldName(), f.value(), Math.min(f.confidence(), 0.3), f.page()));
        warnings.add("Revisa " + DocumentFieldSchemas.describe(List.of(f.fieldName())).getFirst() + ": " + problem.get());
      } else {
        fields.add(f);
      }
    }
    return new ExtractionResult(fields, result.assessment(), warnings, result.pagesAnalyzed(), result.pagesTotal());
  }

  private static String truncate(String value, int max) {
    return value.length() <= max ? value : value.substring(0, max);
  }

  /**
   * Se agotaron los reintentos: el archivo queda marcado "sin revisión
   * automática" (nunca como aprobado). Es una advertencia para el revisor,
   * que puede verificarlo visualmente o reprocesarlo con IA más tarde.
   */
  @Transactional
  public void recordCheckFailed(ExtractDocumentFieldsPayload payload) {
    events.publishEvent(
        new DocumentContentAssessed(
            payload.expedienteId(),
            payload.documentId(),
            payload.documentVersionId(),
            payload.type(),
            null,
            null,
            null,
            "No se pudo hacer la revisión automática del contenido: el servicio de inteligencia artificial no respondió.",
            true,
            List.of(),
            null,
            null,
            null,
            null));
  }

  @Transactional
  public ExtractedFieldObservation confirm(UUID observationId, String confirmedValue) {
    ExtractedFieldObservation observation = getObservation(observationId);
    observation.confirm(confirmedValue, clock.instant());
    runConsistencyCheck(observation.getExpedienteId());
    return observation;
  }

  @Transactional(readOnly = true)
  public ExtractedFieldObservation getObservation(UUID observationId) {
    return observationRepository.findById(observationId).orElseThrow(() -> new NotFoundException("Observación extraída", observationId));
  }

  @Transactional(readOnly = true)
  public List<ExtractedFieldObservation> observationsOfDocument(UUID documentId) {
    // Solo lo leído en la versión VIGENTE: si la última carga no se pudo leer, no se muestran como
    // actuales los datos de una carga anterior (E2E 02/10).
    List<ExtractedFieldObservation> all = observationRepository.findByDocumentIdOrderByFieldNameAsc(documentId);
    return documentsApi
        .currentVersionIdOf(documentId)
        .map(current -> all.stream().filter(o -> o.getDocumentVersionId().equals(current)).toList())
        .orElseGet(() -> latestVersionOnly(all));
  }

  /** Datos detectados en la versión vigente de cada documento del expediente (sin rechazados ni "No aplica"). */
  public record DocumentObservation(ExtractedFieldObservation observation, RequirementStatusView document) {}

  @Transactional(readOnly = true)
  public List<DocumentObservation> observationsOfExpediente(UUID expedienteId) {
    Map<UUID, RequirementStatusView> documents =
        documentsApi.requirementStatusOf(expedienteId).stream()
            .filter(d -> !"NOT_APPLICABLE".equals(d.status()) && !"REJECTED".equals(d.status()))
            .collect(Collectors.toMap(RequirementStatusView::documentId, d -> d));
    return currentVersionsOnly(observationRepository.findByExpedienteIdOrderByFieldNameAsc(expedienteId), documents).stream()
        .filter(o -> documents.containsKey(o.getDocumentId()))
        .map(o -> new DocumentObservation(o, documents.get(o.getDocumentId())))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<DataConflict> conflictsOfExpediente(UUID expedienteId) {
    return conflictRepository.findByExpedienteIdOrderByDetectedAtDesc(expedienteId);
  }

  @Transactional(readOnly = true)
  public DataConflict getConflict(UUID conflictId) {
    return conflictRepository.findById(conflictId).orElseThrow(() -> new NotFoundException("Conflicto de datos", conflictId));
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasUnresolvedConflicts(UUID expedienteId) {
    return conflictRepository.findByExpedienteIdAndResolvedFalse(expedienteId).stream().anyMatch(DataConflict::isCritical);
  }

  @Override
  @Transactional(readOnly = true)
  public List<String> unresolvedConflictDescriptions(UUID expedienteId) {
    return conflictRepository.findByExpedienteIdAndResolvedFalse(expedienteId).stream()
        .filter(DataConflict::isCritical)
        .map(DataConflict::getDescription)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<ConflictView> unresolvedConflicts(UUID expedienteId) {
    return conflictRepository.findByExpedienteIdAndResolvedFalse(expedienteId).stream()
        .map(c -> new ConflictView(c.getSeverity(), c.getDescription()))
        .toList();
  }

  @Transactional
  public DataConflict resolveConflict(UUID conflictId, UUID resolvedByUserId, String note) {
    DataConflict conflict = getConflict(conflictId);
    conflict.resolve(resolvedByUserId, note, clock.instant());
    return conflict;
  }

  /** Si se corrige un dato capturado (nombre, domicilio, superficies...) la comparación se repite. */
  @ApplicationModuleListener
  void on(ExpedienteDataCorrected event) {
    runConsistencyCheck(event.expedienteId());
  }

  /**
   * Compara la versión vigente de cada documento contra los demás y contra
   * los datos capturados. Registra cada diferencia nueva, actualiza la
   * descripción de las que siguen, y cierra las que ya no existen (porque
   * alguien corrigió el dato o cargó el documento correcto).
   */
  @Transactional
  public List<DataConflict> runConsistencyCheck(UUID expedienteId) {
    Map<UUID, RequirementStatusView> documents =
        documentsApi.requirementStatusOf(expedienteId).stream()
            .filter(d -> !"NOT_APPLICABLE".equals(d.status()) && !"REJECTED".equals(d.status()))
            .collect(Collectors.toMap(RequirementStatusView::documentId, d -> d));

    Map<UUID, List<ExtractedFieldObservation>> byDocument =
        currentVersionsOnly(observationRepository.findByExpedienteIdOrderByFieldNameAsc(expedienteId), documents).stream()
            .collect(Collectors.groupingBy(ExtractedFieldObservation::getDocumentId, LinkedHashMap::new, Collectors.toList()));

    List<DocumentFacts> facts = new ArrayList<>();
    byDocument.forEach(
        (documentId, observations) -> {
          RequirementStatusView doc = documents.get(documentId);
          if (doc == null) {
            return;
          }
          Map<String, String> fields = new HashMap<>();
          for (ExtractedFieldObservation o : observations) {
            fields.put(o.getFieldName(), o.getConfirmedValue() != null ? o.getConfirmedValue() : o.getDetectedValue());
          }
          facts.add(new DocumentFacts(documentId, doc.type(), doc.participantId(), fields));
        });

    ExpedienteSummary summary = expedienteApi.getSummary(expedienteId);
    ManualClientDataView manual = expedienteApi.getManualData(expedienteId);
    DeclaredData declared =
        new DeclaredData(
            summary.participants().stream()
                .map(
                    p ->
                        new DeclaredParticipant(
                            p.id(), p.isOwner(), p.fullName(), p.rfc(), p.curp(), "ATTORNEY".equals(p.role()) || "LEGAL_REPRESENTATIVE".equals(p.role())))
                .toList(),
            summary.propertyAddress(),
            manual.landAreaM2(),
            manual.builtAreaM2());

    List<Finding> findings = DocumentConsistencyChecker.check(facts, declared);
    Instant now = clock.instant();

    Map<String, DataConflict> open =
        conflictRepository.findByExpedienteIdAndResolvedFalse(expedienteId).stream()
            .collect(Collectors.toMap(DataConflict::getFieldName, c -> c, (a, b) -> a));
    Set<String> currentKeys = findings.stream().map(Finding::key).collect(Collectors.toSet());

    for (Finding finding : findings) {
      Optional.ofNullable(open.get(finding.key()))
          .ifPresentOrElse(
              existing -> existing.refresh(finding.description(), finding.severity()),
              () -> conflictRepository.save(new DataConflict(expedienteId, finding.key(), finding.description(), finding.severity(), now)));
    }
    open.values().stream().filter(c -> !currentKeys.contains(c.getFieldName())).forEach(c -> c.closeBecauseDataNowMatches(now));

    return conflictRepository.findByExpedienteIdOrderByDetectedAtDesc(expedienteId);
  }

  /** De cada documento, solo lo leído en su versión vigente (si se conoce; si no, la más reciente con datos). */
  private static List<ExtractedFieldObservation> currentVersionsOnly(
      List<ExtractedFieldObservation> observations, Map<UUID, RequirementStatusView> documents) {
    return latestVersionOnly(observations).stream()
        .filter(
            o -> {
              RequirementStatusView doc = documents.get(o.getDocumentId());
              return doc == null || doc.currentVersionId() == null || doc.currentVersionId().equals(o.getDocumentVersionId());
            })
        .toList();
  }

  /** De cada documento, solo cuentan las observaciones de su versión más reciente. */
  private static List<ExtractedFieldObservation> latestVersionOnly(List<ExtractedFieldObservation> observations) {
    Map<UUID, UUID> latestVersionByDocument = new HashMap<>();
    observations.stream()
        .sorted(Comparator.comparing(ExtractedFieldObservation::getCreatedAt))
        .forEach(o -> latestVersionByDocument.put(o.getDocumentId(), o.getDocumentVersionId()));
    return observations.stream()
        .filter(o -> o.getDocumentVersionId().equals(latestVersionByDocument.get(o.getDocumentId())))
        .toList();
  }

  private java.nio.file.Path downloadToTempFile(String storageKey) {
    try {
      return com.c21genera.shared.pdf.PdfFiles.toTempFile(fileStorage.get(storageKey), ".pdf");
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo leer el PDF procesado " + storageKey, e);
    }
  }

  private static void deleteQuietly(java.nio.file.Path file) {
    try {
      java.nio.file.Files.deleteIfExists(file);
    } catch (java.io.IOException ignored) {
      // Archivo temporal: el sistema lo limpia si no se pudo borrar.
    }
  }
}
