package com.c21genera.extraction.application;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.documents.DocumentsApi.RequirementStatusView;
import com.c21genera.expedientes.ExpedienteLifecycleApi;
import com.c21genera.expedientes.ExpedienteLifecycleApi.ManualClientDataView;
import com.c21genera.expedientes.ExpedienteSummary;
import com.c21genera.extraction.ExtractionApi;
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

    byte[] pdfBytes = readAll(payload.pdfStorageKey());
    // Siempre se llama al proveedor, aunque el tipo no tenga campos que
    // extraer: la verificación de que el archivo corresponde al documento
    // solicitado aplica a todos los tipos.
    ExtractionResult result = provider.extract(payload.type(), pdfBytes, fieldNames);

    transactions.executeWithoutResult(status -> saveResult(payload, result));
  }

  private void saveResult(ExtractDocumentFieldsPayload payload, ExtractionResult result) {
    Instant now = clock.instant();
    for (FieldResult field : result.fields()) {
      observationRepository.save(
          new ExtractedFieldObservation(
              payload.expedienteId(),
              payload.documentId(),
              payload.documentVersionId(),
              field.fieldName(),
              field.value(),
              FieldOrigin.AI_EXTRACTED,
              field.confidence(),
              now));
    }

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
            false));

    runConsistencyCheck(payload.expedienteId());
  }

  /**
   * Se agotaron los reintentos: el archivo queda marcado "sin revisión
   * automática" (nunca como aprobado), de modo que solo se pueda aceptar
   * con una autorización de excepción o después de volver a cargarlo.
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
            true));
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
    return latestVersionOnly(observationRepository.findByDocumentIdOrderByFieldNameAsc(documentId));
  }

  /** Datos detectados en la versión vigente de cada documento del expediente (sin rechazados ni "No aplica"). */
  public record DocumentObservation(ExtractedFieldObservation observation, RequirementStatusView document) {}

  @Transactional(readOnly = true)
  public List<DocumentObservation> observationsOfExpediente(UUID expedienteId) {
    Map<UUID, RequirementStatusView> documents =
        documentsApi.requirementStatusOf(expedienteId).stream()
            .filter(d -> !"NOT_APPLICABLE".equals(d.status()) && !"REJECTED".equals(d.status()))
            .collect(Collectors.toMap(RequirementStatusView::documentId, d -> d));
    return latestVersionOnly(observationRepository.findByExpedienteIdOrderByFieldNameAsc(expedienteId)).stream()
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
    return conflictRepository.existsByExpedienteIdAndResolvedFalse(expedienteId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<String> unresolvedConflictDescriptions(UUID expedienteId) {
    return conflictRepository.findByExpedienteIdAndResolvedFalse(expedienteId).stream().map(DataConflict::getDescription).toList();
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
        latestVersionOnly(observationRepository.findByExpedienteIdOrderByFieldNameAsc(expedienteId)).stream()
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
                .map(p -> new DeclaredParticipant(p.id(), p.isOwner(), p.fullName(), p.rfc(), p.curp()))
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
              existing -> existing.refreshDescription(finding.description()),
              () -> conflictRepository.save(new DataConflict(expedienteId, finding.key(), finding.description(), now)));
    }
    open.values().stream().filter(c -> !currentKeys.contains(c.getFieldName())).forEach(c -> c.closeBecauseDataNowMatches(now));

    return conflictRepository.findByExpedienteIdOrderByDetectedAtDesc(expedienteId);
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

  private byte[] readAll(String storageKey) {
    try (InputStream in = fileStorage.get(storageKey)) {
      return in.readAllBytes();
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo leer el PDF procesado " + storageKey, e);
    }
  }
}
