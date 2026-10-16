package com.c21genera.contracts.application;

import com.c21genera.contracts.domain.SignedContractUpload;
import com.c21genera.contracts.domain.SignedContractUpload.SignatureMethod;
import com.c21genera.contracts.infrastructure.SignedContractUploadRepository;
import com.c21genera.documents.DocumentsApi;
import com.c21genera.documents.DocumentsApi.IncomingFile;
import com.c21genera.expedientes.ExpedienteLifecycleApi;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.events.Actor;
import com.c21genera.shared.events.ContractEvents.SignedContractUploaded;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Control de Cambios #20: todos los contratos de C21 Genera se firman fuera del sistema (DocuSign o
 * impresos). Aquí se sube el PDF firmado al expediente, aunque el contrato no se haya generado en
 * el sistema (también contratos históricos), y el expediente queda con estatus "Firmado".
 */
@Service
@Transactional
public class SignedContractUploadService {

  private final SignedContractUploadRepository repository;
  private final DocumentsApi documentsApi;
  private final ExpedienteLifecycleApi expedienteApi;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public SignedContractUploadService(
      SignedContractUploadRepository repository,
      DocumentsApi documentsApi,
      ExpedienteLifecycleApi expedienteApi,
      ApplicationEventPublisher events,
      Clock clock) {
    this.repository = repository;
    this.documentsApi = documentsApi;
    this.expedienteApi = expedienteApi;
    this.events = events;
    this.clock = clock;
  }

  public SignedContractUpload upload(
      UUID expedienteId, List<IncomingFile> files, String signatureMethod, boolean historical, String note, Actor actor) {
    SignatureMethod method = parse(signatureMethod);
    if (files == null || files.isEmpty()) {
      throw new UnprocessableException("NO_FILES", "Selecciona el PDF del contrato firmado.");
    }
    String cleanedNote = note == null || note.isBlank() ? null : note.strip();
    UUID documentId = documentsApi.attachSignedContract(expedienteId, files, actor);
    SignedContractUpload upload =
        repository.save(
            new SignedContractUpload(expedienteId, documentId, method, historical, cleanedNote, actor.userId(), actor.name(), clock.instant()));
    expedienteApi.recordContractSignedExternally(expedienteId);
    events.publishEvent(new SignedContractUploaded(expedienteId, documentId, method.name(), historical, actor));
    return upload;
  }

  @Transactional(readOnly = true)
  public List<SignedContractUpload> uploadsOf(UUID expedienteId) {
    return repository.findByExpedienteIdOrderByUploadedAtDesc(expedienteId);
  }

  private static SignatureMethod parse(String value) {
    if (value == null || value.isBlank()) {
      throw new UnprocessableException("SIGNATURE_METHOD_REQUIRED", "Indica cómo se firmó: DocuSign o firma física (escaneado).");
    }
    try {
      return SignatureMethod.valueOf(value.strip().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new UnprocessableException("SIGNATURE_METHOD_INVALID", "Indica cómo se firmó: DocuSign o firma física (escaneado).");
    }
  }
}
