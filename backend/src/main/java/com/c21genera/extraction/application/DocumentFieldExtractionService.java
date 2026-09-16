package com.c21genera.extraction.application;

import com.c21genera.extraction.domain.CrossDocumentValidator;
import com.c21genera.extraction.domain.DataConflict;
import com.c21genera.extraction.domain.DocumentFieldSchemas;
import com.c21genera.extraction.domain.ExtractedFieldObservation;
import com.c21genera.extraction.domain.FieldOrigin;
import com.c21genera.extraction.domain.StructuredExtractionProvider;
import com.c21genera.extraction.domain.StructuredExtractionProvider.ExtractionResult;
import com.c21genera.extraction.domain.StructuredExtractionProvider.FieldResult;
import com.c21genera.extraction.ExtractionApi;
import com.c21genera.extraction.infrastructure.DataConflictRepository;
import com.c21genera.extraction.infrastructure.ExtractedFieldObservationRepository;
import com.c21genera.shared.storage.FileStorage;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta la extracción de campos de un PDF ya procesado y la detección de
 * conflictos entre documentos del mismo expediente (ver AGENTS §38-41).
 */
@Service
public class DocumentFieldExtractionService implements ExtractionApi {

  private final ExtractedFieldObservationRepository observationRepository;
  private final DataConflictRepository conflictRepository;
  private final StructuredExtractionProvider provider;
  private final FileStorage fileStorage;
  private final Clock clock;

  public DocumentFieldExtractionService(
      ExtractedFieldObservationRepository observationRepository,
      DataConflictRepository conflictRepository,
      StructuredExtractionProvider provider,
      FileStorage fileStorage,
      Clock clock) {
    this.observationRepository = observationRepository;
    this.conflictRepository = conflictRepository;
    this.provider = provider;
    this.fileStorage = fileStorage;
    this.clock = clock;
  }

  @Transactional
  public void extract(ExtractDocumentFieldsPayload payload) {
    List<String> fieldNames = DocumentFieldSchemas.fieldsFor(payload.type());
    if (fieldNames.isEmpty()) {
      return;
    }

    byte[] pdfBytes = readAll(payload.pdfStorageKey());
    ExtractionResult result = provider.extract(payload.type(), pdfBytes, fieldNames);

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

    if (!result.fields().isEmpty()) {
      detectConflicts(payload.expedienteId(), now);
    }
  }

  @Transactional
  public ExtractedFieldObservation confirm(UUID observationId, String confirmedValue) {
    ExtractedFieldObservation observation =
        observationRepository
            .findById(observationId)
            .orElseThrow(() -> new com.c21genera.shared.domain.NotFoundException("Observación extraída", observationId));
    observation.confirm(confirmedValue, clock.instant());
    return observation;
  }

  @Transactional(readOnly = true)
  public List<ExtractedFieldObservation> observationsOfDocument(UUID documentId) {
    return observationRepository.findByDocumentIdOrderByFieldNameAsc(documentId);
  }

  @Transactional(readOnly = true)
  public List<DataConflict> conflictsOfExpediente(UUID expedienteId) {
    return conflictRepository.findByExpedienteIdOrderByDetectedAtDesc(expedienteId);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasUnresolvedConflicts(UUID expedienteId) {
    return conflictRepository.existsByExpedienteIdAndResolvedFalse(expedienteId);
  }

  public DataConflict resolveConflict(UUID conflictId, UUID resolvedByUserId, String note) {
    DataConflict conflict =
        conflictRepository
            .findById(conflictId)
            .orElseThrow(() -> new com.c21genera.shared.domain.NotFoundException("Conflicto de datos", conflictId));
    conflict.resolve(resolvedByUserId, note, clock.instant());
    return conflict;
  }

  private void detectConflicts(UUID expedienteId, Instant now) {
    List<ExtractedFieldObservation> observations = observationRepository.findByExpedienteIdOrderByFieldNameAsc(expedienteId);
    List<CrossDocumentValidator.FieldValue> values =
        observations.stream()
            .map(
                o ->
                    new CrossDocumentValidator.FieldValue(
                        o.getFieldName(),
                        o.getConfirmedValue() != null ? o.getConfirmedValue() : o.getDetectedValue(),
                        "documento " + o.getDocumentId()))
            .toList();

    for (CrossDocumentValidator.DetectedConflict conflict : CrossDocumentValidator.validate(values)) {
      if (!conflictRepository.existsByExpedienteIdAndFieldNameAndResolvedFalse(expedienteId, conflict.fieldName())) {
        conflictRepository.save(new DataConflict(expedienteId, conflict.fieldName(), conflict.description(), now));
      }
    }
  }

  private byte[] readAll(String storageKey) {
    try (InputStream in = fileStorage.get(storageKey)) {
      return in.readAllBytes();
    } catch (Exception e) {
      throw new IllegalStateException("No se pudo leer el PDF procesado " + storageKey, e);
    }
  }
}
