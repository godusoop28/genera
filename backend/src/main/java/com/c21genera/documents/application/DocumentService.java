package com.c21genera.documents.application;

import com.c21genera.documents.DocumentEvents.AllRequiredDocumentsApproved;
import com.c21genera.documents.DocumentEvents.DocumentReviewed;
import com.c21genera.documents.DocumentEvents.DocumentVersionUploaded;
import com.c21genera.documents.DocumentEvents.PageRef;
import com.c21genera.documents.DocumentStatus;
import com.c21genera.documents.DocumentsApi;
import com.c21genera.documents.domain.Document;
import com.c21genera.documents.domain.DocumentNotReviewableException;
import com.c21genera.documents.domain.DocumentPage;
import com.c21genera.documents.domain.DocumentReview;
import com.c21genera.documents.domain.DocumentVersion;
import com.c21genera.documents.domain.ReturnReasonCode;
import com.c21genera.documents.domain.ReviewDecision;
import com.c21genera.documents.domain.UploadedVia;
import com.c21genera.documents.infrastructure.DocumentPageRepository;
import com.c21genera.documents.infrastructure.DocumentRepository;
import com.c21genera.documents.infrastructure.DocumentReviewRepository;
import com.c21genera.documents.infrastructure.DocumentVersionRepository;
import com.c21genera.documents.infrastructure.FileValidator;
import com.c21genera.documents.infrastructure.FileValidator.ValidatedFile;
import com.c21genera.documents.infrastructure.StorageKeys;
import com.c21genera.expedientes.ExpedienteEvents.ExpedienteRequirementsChanged;
import com.c21genera.expedientes.ExpedienteLifecycleApi;
import com.c21genera.expedientes.RequiredDocumentSpec;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.storage.FileStorage;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DocumentService implements DocumentsApi {

  private final DocumentRepository documentRepository;
  private final DocumentVersionRepository versionRepository;
  private final DocumentPageRepository pageRepository;
  private final DocumentReviewRepository reviewRepository;
  private final FileStorage fileStorage;
  private final FileValidator fileValidator;
  private final ExpedienteLifecycleApi expedienteApi;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public DocumentService(
      DocumentRepository documentRepository,
      DocumentVersionRepository versionRepository,
      DocumentPageRepository pageRepository,
      DocumentReviewRepository reviewRepository,
      FileStorage fileStorage,
      FileValidator fileValidator,
      ExpedienteLifecycleApi expedienteApi,
      ApplicationEventPublisher events,
      Clock clock) {
    this.documentRepository = documentRepository;
    this.versionRepository = versionRepository;
    this.pageRepository = pageRepository;
    this.reviewRepository = reviewRepository;
    this.fileStorage = fileStorage;
    this.fileValidator = fileValidator;
    this.expedienteApi = expedienteApi;
    this.events = events;
    this.clock = clock;
  }

  /** Materializa los Document a partir de la política calculada por expedientes (ver AGENTS §21/§88). */
  @ApplicationModuleListener
  void on(ExpedienteRequirementsChanged event) {
    for (RequiredDocumentSpec spec : event.requirements()) {
      if (documentRepository.existsByExpedienteIdAndRequirementCode(event.expedienteId(), spec.requirementCode())) {
        continue;
      }
      documentRepository.save(
          new Document(event.expedienteId(), spec.requirementCode(), spec.type(), spec.participantId(), spec.required()));
    }
  }

  @Transactional(readOnly = true)
  public List<Document> documentsOf(UUID expedienteId) {
    return documentRepository.findByExpedienteId(expedienteId);
  }

  @Transactional(readOnly = true)
  public Document get(UUID documentId) {
    return documentRepository.findById(documentId).orElseThrow(() -> new NotFoundException("Documento", documentId));
  }

  @Transactional(readOnly = true)
  public List<DocumentVersion> versionsOf(UUID documentId) {
    return versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId);
  }

  @Transactional(readOnly = true)
  public DocumentVersion getVersion(UUID documentVersionId) {
    return versionRepository
        .findById(documentVersionId)
        .orElseThrow(() -> new NotFoundException("Versión de documento", documentVersionId));
  }

  @Transactional(readOnly = true)
  public DocumentVersion latestVersionOf(UUID documentId) {
    return versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId).stream()
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Versión de documento para el documento", documentId));
  }

  public record UploadedFileContent(byte[] content, String originalFilename) {}

  /** Público (fotos JPG/PNG) o interno (permite PDF). Ver AGENTS §32/§94. */
  public DocumentVersion uploadVersion(UUID documentId, List<UploadedFileContent> files, UploadedVia via) {
    Document document = get(documentId);
    int versionNumber = document.startNewVersion();
    Instant now = clock.instant();
    DocumentVersion version = new DocumentVersion(documentId, versionNumber, now, via);
    versionRepository.save(version);

    List<PageRef> pageRefs = new java.util.ArrayList<>();
    int pageNumber = 1;
    for (UploadedFileContent file : files) {
      ValidatedFile validated =
          via == UploadedVia.PUBLIC_PORTAL
              ? fileValidator.validatePublic(file.content())
              : fileValidator.validateInternal(file.content());

      String extension = StorageKeys.extensionFor(validated.detectedMimeType());
      String storageKey =
          StorageKeys.originalPageKey(document.getExpedienteId(), document.getType(), documentId, versionNumber, pageNumber, extension);

      FileStorage.StoredObjectMetadata stored =
          fileStorage.store(
              storageKey, new ByteArrayInputStream(validated.content()), validated.content().length, validated.detectedMimeType());

      pageRepository.save(
          new DocumentPage(
              version.getId(),
              pageNumber,
              stored.storageKey(),
              file.originalFilename(),
              validated.detectedMimeType(),
              stored.size(),
              stored.sha256()));
      pageRefs.add(new PageRef(pageNumber, stored.storageKey(), validated.detectedMimeType()));
      pageNumber++;
    }

    events.publishEvent(
        new DocumentVersionUploaded(document.getExpedienteId(), documentId, version.getId(), document.getType(), pageRefs));

    return version;
  }

  public Document review(UUID documentId, ReviewDecision decision, ReturnReasonCode reasonCode, String comment, UUID reviewerId) {
    Document document = get(documentId);
    DocumentVersion latest = latestVersionOf(documentId);

    if (document.getStatus() != DocumentStatus.READY_FOR_REVIEW && document.getStatus() != DocumentStatus.RETURNED
        && document.getStatus() != DocumentStatus.UPLOADED) {
      throw new DocumentNotReviewableException("El documento no está en un estado revisable (" + document.getStatus() + ").");
    }
    switch (latest.getProcessingStatus()) {
      case PROCESSING, QUEUED ->
          throw new DocumentNotReviewableException("El documento todavía se encuentra en procesamiento.");
      default -> { /* PROCESSED, QUALITY_FAILED o FAILED sí pueden revisarse manualmente */ }
    }

    reviewRepository.save(new DocumentReview(latest.getId(), decision, reasonCode, comment, reviewerId, clock.instant()));
    document.applyReview(decision);

    expedienteApi.recordUnderReview(document.getExpedienteId());
    events.publishEvent(new DocumentReviewed(document.getExpedienteId(), documentId, decision));

    if (decision == ReviewDecision.RETURNED) {
      expedienteApi.recordCorrectionsRequested(document.getExpedienteId());
    } else if (decision == ReviewDecision.ACCEPTED && allRequiredAccepted(document.getExpedienteId())) {
      expedienteApi.recordDocumentsApproved(document.getExpedienteId());
      events.publishEvent(new AllRequiredDocumentsApproved(document.getExpedienteId()));
    }

    return document;
  }

  @Override
  @Transactional(readOnly = true)
  public List<PageView> pagesOf(UUID documentVersionId) {
    return pageRepository.findByDocumentVersionIdOrderByPageNumberAsc(documentVersionId).stream()
        .map(p -> new PageView(p.getPageNumber(), p.getStorageKeyOriginal(), p.getMimeType()))
        .toList();
  }

  @Override
  public void markProcessing(UUID documentVersionId) {
    findVersion(documentVersionId).startProcessing();
  }

  @Override
  public void markProcessed(UUID documentVersionId, String pdfStorageKey, String normalizedStorageKey) {
    DocumentVersion version = findVersion(documentVersionId);
    version.completeProcessing(pdfStorageKey, normalizedStorageKey);
    Document document = documentRepository.findById(version.getDocumentId()).orElseThrow();
    document.markReadyForReview();
  }

  @Override
  public void markQualityFailed(UUID documentVersionId, String reason) {
    findVersion(documentVersionId).failQuality(reason);
  }

  @Override
  public void markFailed(UUID documentVersionId, String reason) {
    findVersion(documentVersionId).fail(reason);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean allRequiredUploaded(UUID expedienteId) {
    List<Document> docs = documentRepository.findByExpedienteId(expedienteId);
    return docs.stream().filter(Document::isRequired).allMatch(d -> d.getStatus() != DocumentStatus.PENDING);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean allRequiredAccepted(UUID expedienteId) {
    List<Document> docs = documentRepository.findByExpedienteId(expedienteId);
    List<Document> required = docs.stream().filter(Document::isRequired).toList();
    if (required.isEmpty()) {
      // Los requisitos todavía no se materializan (evento asíncrono) o el
      // expediente no tiene documentos obligatorios: no se considera completo.
      return false;
    }
    return required.stream().allMatch(d -> d.getStatus() == DocumentStatus.ACCEPTED);
  }

  private DocumentVersion findVersion(UUID documentVersionId) {
    return versionRepository.findById(documentVersionId).orElseThrow(() -> new NotFoundException("Versión de documento", documentVersionId));
  }
}
