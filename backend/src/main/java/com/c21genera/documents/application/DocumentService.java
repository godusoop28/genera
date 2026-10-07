package com.c21genera.documents.application;

import com.c21genera.shared.events.DocumentEvents.DocumentFileMoved;
import com.c21genera.shared.events.DocumentEvents.DocumentReprocessRequested;
import com.c21genera.documents.DocumentStatus;
import com.c21genera.documents.DocumentsApi;
import com.c21genera.documents.ProcessingStatus;
import com.c21genera.documents.domain.Document;
import com.c21genera.documents.domain.DocumentNotReviewableException;
import com.c21genera.documents.domain.DocumentPage;
import com.c21genera.documents.domain.DocumentReview;
import com.c21genera.documents.domain.DocumentVersion;
import com.c21genera.documents.domain.ReceptionNotReadyException;
import com.c21genera.documents.domain.ReturnReasonCode;
import com.c21genera.documents.domain.UploadedVia;
import com.c21genera.documents.infrastructure.DocumentPageRepository;
import com.c21genera.documents.infrastructure.DocumentRepository;
import com.c21genera.documents.infrastructure.DocumentReviewRepository;
import com.c21genera.documents.infrastructure.DocumentVersionRepository;
import com.c21genera.documents.infrastructure.ExpedienteDocumentsLock;
import com.c21genera.documents.infrastructure.FileValidator;
import com.c21genera.documents.infrastructure.FileValidator.ValidatedFile;
import com.c21genera.documents.infrastructure.StorageKeys;
import com.c21genera.shared.config.AiProperties;
import com.c21genera.shared.domain.ConflictException;
import com.c21genera.shared.domain.NotFoundException;
import com.c21genera.shared.domain.RequiredDocumentSpec;
import com.c21genera.shared.domain.ReviewDecision;
import com.c21genera.shared.domain.UnprocessableException;
import com.c21genera.shared.events.Actor;
import com.c21genera.shared.events.DocumentEvents.AllRequiredDocumentsApproved;
import com.c21genera.shared.events.DocumentEvents.AllRequiredDocumentsUploaded;
import com.c21genera.shared.events.DocumentEvents.DocumentApplicabilityChanged;
import com.c21genera.shared.events.DocumentEvents.DocumentContentAssessed;
import com.c21genera.shared.events.DocumentEvents.DocumentReviewed;
import com.c21genera.shared.events.DocumentEvents.DocumentVersionUploaded;
import com.c21genera.shared.events.DocumentEvents.PageRef;
import com.c21genera.shared.events.DocumentEvents.ReceptionSigned;
import com.c21genera.shared.events.DocumentEvents.RequiredDocumentsReopened;
import com.c21genera.shared.events.ExpedienteEvents.ExpedienteRequirementsChanged;
import com.c21genera.shared.storage.FileStorage;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DocumentService implements DocumentsApi {

  /** Una justificación de excepción o de "No aplica" debe explicar algo, no solo "ok". */
  static final int MIN_JUSTIFICATION_LENGTH = 15;

  private final DocumentRepository documentRepository;
  private final DocumentVersionRepository versionRepository;
  private final DocumentPageRepository pageRepository;
  private final DocumentReviewRepository reviewRepository;
  private final FileStorage fileStorage;
  private final FileValidator fileValidator;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final ExpedienteDocumentsLock documentsLock;

  /**
   * Tiempo máximo que se espera la revisión automática antes de permitir
   * revisar a mano (cubre los reintentos del job de extracción); después, si
   * la IA no respondió, la versión queda marcada con aiCheckFailed.
   */
  private static final java.time.Duration AI_CHECK_GRACE = java.time.Duration.ofMinutes(20);

  private final boolean aiEnabled;

  public DocumentService(
      DocumentRepository documentRepository,
      DocumentVersionRepository versionRepository,
      DocumentPageRepository pageRepository,
      DocumentReviewRepository reviewRepository,
      FileStorage fileStorage,
      FileValidator fileValidator,
      ApplicationEventPublisher events,
      Clock clock,
      AiProperties aiProperties,
      ExpedienteDocumentsLock documentsLock) {
    this.documentRepository = documentRepository;
    this.versionRepository = versionRepository;
    this.pageRepository = pageRepository;
    this.reviewRepository = reviewRepository;
    this.fileStorage = fileStorage;
    this.fileValidator = fileValidator;
    this.events = events;
    this.clock = clock;
    this.aiEnabled = aiProperties.enabled();
    this.documentsLock = documentsLock;
  }

  /**
   * Materializa los Document a partir de la política calculada por
   * expedientes (ver AGENTS §21/§88). Idempotente: si el requisito ya
   * existía, actualiza su bandera de obligatorio; si dejó de figurar en la
   * política (p. ej. se cambió de escritura a contrato privado, o se eliminó
   * un copropietario), deja de ser obligatorio pero conserva su historial.
   */
  @ApplicationModuleListener
  void on(ExpedienteRequirementsChanged event) {
    Completeness before = completenessOf(event.expedienteId());
    List<Document> existing = documentRepository.findByExpedienteId(event.expedienteId());
    Set<String> currentCodes = event.requirements().stream().map(RequiredDocumentSpec::requirementCode).collect(Collectors.toSet());

    for (RequiredDocumentSpec spec : event.requirements()) {
      existing.stream()
          .filter(d -> d.getRequirementCode().equals(spec.requirementCode()))
          .findFirst()
          .ifPresentOrElse(
              d -> d.updateRequired(spec.required()),
              () ->
                  documentRepository.save(
                      new Document(
                          event.expedienteId(), spec.requirementCode(), spec.type(), spec.participantId(), spec.required())));
    }
    for (Document d : existing) {
      if (!currentCodes.contains(d.getRequirementCode())) {
        d.updateRequired(false);
      }
    }
    documentRepository.flush();
    publishCompletenessChanges(event.expedienteId(), before);
  }

  /** Resultado de la revisión de contenido con IA (ver extraction). */
  @ApplicationModuleListener
  void on(DocumentContentAssessed event) {
    versionRepository
        .findById(event.documentVersionId())
        .ifPresent(
            v ->
                v.recordAiAssessment(
                    event.matchesExpectedType(),
                    event.legible(),
                    event.detectedDocumentKind(),
                    event.observations(),
                    event.checkFailed(),
                    clock.instant(),
                    new DocumentVersion.AiDetails(
                        event.warnings(), event.pagesAnalyzed(), event.pagesTotal(), event.fieldsExpected(), event.fieldsFound())));
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
  public Optional<DocumentVersion> latestVersion(UUID documentId) {
    return versionRepository.findByDocumentIdOrderByVersionNumberDesc(documentId).stream().findFirst();
  }

  @Transactional(readOnly = true)
  public DocumentVersion getVersion(UUID documentVersionId) {
    return versionRepository
        .findById(documentVersionId)
        .orElseThrow(() -> new NotFoundException("Versión de documento", documentVersionId));
  }

  @Transactional(readOnly = true)
  public DocumentVersion latestVersionOf(UUID documentId) {
    return latestVersion(documentId).orElseThrow(() -> new NotFoundException("Versión de documento para el documento", documentId));
  }

  @Transactional(readOnly = true)
  public List<DocumentReview> reviewsOf(UUID documentId) {
    List<UUID> versionIds = versionsOf(documentId).stream().map(DocumentVersion::getId).toList();
    return versionIds.isEmpty() ? List.of() : reviewRepository.findByDocumentVersionIdInOrderByReviewedAtDesc(versionIds);
  }

  /**
   * Archivo subido. source se puede abrir varias veces (p. ej. el MultipartFile,
   * que Tomcat ya guardó en disco): el contenido nunca se carga entero en
   * memoria (E2E 02/10: un PDF de 38.9 MB ocupaba 39 MB de heap en la carga).
   */
  public record UploadedFileContent(long size, String originalFilename, org.springframework.core.io.InputStreamSource source) {

    public static UploadedFileContent of(byte[] content, String originalFilename) {
      return new UploadedFileContent(content.length, originalFilename, new org.springframework.core.io.ByteArrayResource(content));
    }
  }

  private static byte[] readHead(UploadedFileContent file) {
    try (java.io.InputStream in = file.source().getInputStream()) {
      return in.readNBytes(FileValidator.HEAD_BYTES);
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException("No se pudo leer el archivo subido", e);
    }
  }

  /** Público o interno: fotos JPG/PNG o PDF (según app.upload). Ver AGENTS §32/§94. */
  public DocumentVersion uploadVersion(UUID documentId, List<UploadedFileContent> files, UploadedVia via, Actor actor) {
    Document document = get(documentId);
    if (files == null || files.isEmpty()) {
      throw new UnprocessableException("NO_FILES", "Selecciona al menos un archivo.");
    }
    if (via == UploadedVia.PUBLIC_PORTAL && document.getStatus() == DocumentStatus.ACCEPTED) {
      throw new ConflictException("DOCUMENT_ALREADY_ACCEPTED", "Este documento ya fue aceptado; no es necesario volver a cargarlo.");
    }
    Completeness before = completenessOf(document.getExpedienteId());
    String previousStatus = document.getStatus().name();
    int versionNumber = document.startNewVersion();
    Instant now = clock.instant();
    DocumentVersion version =
        new DocumentVersion(documentId, versionNumber, now, via, actor.isStaff() ? actor.userId() : null, actor.isStaff() ? actor.name() : null);
    versionRepository.save(version);

    List<PageRef> pageRefs = new ArrayList<>();
    int pageNumber = 1;
    for (UploadedFileContent file : files) {
      byte[] head = readHead(file);
      String mimeType =
          via == UploadedVia.PUBLIC_PORTAL
              ? fileValidator.validatePublic(file.size(), head)
              : fileValidator.validateInternal(file.size(), head);

      String extension = StorageKeys.extensionFor(mimeType);
      String storageKey =
          StorageKeys.originalPageKey(document.getExpedienteId(), document.getType(), documentId, versionNumber, pageNumber, extension);

      FileStorage.StoredObjectMetadata stored;
      try (java.io.InputStream content = file.source().getInputStream()) {
        stored = fileStorage.store(storageKey, content, file.size(), mimeType);
      } catch (java.io.IOException e) {
        throw new java.io.UncheckedIOException("No se pudo leer el archivo subido", e);
      }

      pageRepository.save(
          new DocumentPage(
              version.getId(),
              pageNumber,
              stored.storageKey(),
              file.originalFilename(),
              mimeType,
              stored.size(),
              stored.sha256()));
      pageRefs.add(new PageRef(pageNumber, stored.storageKey(), mimeType));
      pageNumber++;
    }

    events.publishEvent(
        new DocumentVersionUploaded(
            document.getExpedienteId(),
            documentId,
            version.getId(),
            document.getType(),
            pageRefs,
            document.getParticipantId(),
            actor,
            previousStatus));

    documentRepository.flush();
    publishCompletenessChanges(document.getExpedienteId(), before);
    return version;
  }

  /**
   * "Reprocesar con IA": vuelve a ejecutar el procesamiento de la versión
   * vigente sin que el cliente la suba de nuevo. Si ya tiene PDF solo se repite
   * la extracción (o todo, con full=true); si antes no pasó la calidad o
   * falló, se reprocesa desde los archivos originales con las reglas actuales.
   * El estado del documento (p. ej. ACEPTADO) no cambia.
   */
  public DocumentVersion reprocess(UUID documentId, boolean full, Actor actor) {
    Document document = get(documentId);
    DocumentVersion latest =
        latestVersion(documentId).orElseThrow(() -> new UnprocessableException("NO_FILE", "Este documento todavía no tiene un archivo cargado."));
    if (latest.getProcessingStatus() == ProcessingStatus.QUEUED || latest.getProcessingStatus() == ProcessingStatus.PROCESSING) {
      throw new ConflictException("ALREADY_PROCESSING", "Este archivo ya se está procesando; espera a que termine.");
    }
    String pdfKey = null;
    if (!full && latest.getProcessingStatus() == ProcessingStatus.PROCESSED && latest.getStorageKeyPdf() != null) {
      latest.clearAiAssessment();
      pdfKey = latest.getStorageKeyPdf();
    } else {
      latest.resetForReprocessing();
    }
    events.publishEvent(
        new DocumentReprocessRequested(document.getExpedienteId(), documentId, latest.getId(), document.getType(), pdfKey, actor));
    return latest;
  }

  /** Reprocesa todos los archivos del expediente que no se estén procesando ya (p. ej. tras un cambio de reglas). */
  public int reprocessAll(UUID expedienteId, Actor actor) {
    int count = 0;
    for (Document document : documentRepository.findByExpedienteId(expedienteId)) {
      if (document.getCurrentVersionNumber() == 0 || document.getStatus() == DocumentStatus.NOT_APPLICABLE) {
        continue;
      }
      DocumentVersion latest = latestVersion(document.getId()).orElse(null);
      if (latest == null
          || latest.getProcessingStatus() == ProcessingStatus.QUEUED
          || latest.getProcessingStatus() == ProcessingStatus.PROCESSING) {
        continue;
      }
      reprocess(document.getId(), false, actor);
      count++;
    }
    return count;
  }

  /**
   * "Cambiar tipo de documento": el archivo vigente de un requisito se mueve a
   * otro requisito del mismo expediente (p. ej. un predial que se cargó como
   * acta de matrimonio). Las páginas originales se reutilizan (no se vuelve a
   * subir nada), el destino recibe una versión nueva que se procesa y extrae
   * con su tipo correcto, y el origen vuelve a pendiente.
   */
  public Document moveCurrentFile(UUID sourceDocumentId, UUID targetDocumentId, Actor actor) {
    if (sourceDocumentId.equals(targetDocumentId)) {
      throw new UnprocessableException("SAME_DOCUMENT", "Elige un documento distinto.");
    }
    Document source = get(sourceDocumentId);
    Document target = get(targetDocumentId);
    if (!source.getExpedienteId().equals(target.getExpedienteId())) {
      throw new UnprocessableException("DIFFERENT_EXPEDIENTE", "Solo se puede mover a un documento del mismo expediente.");
    }
    if (target.getStatus() == DocumentStatus.ACCEPTED) {
      throw new ConflictException("DOCUMENT_ALREADY_ACCEPTED", "El documento destino ya está aceptado.");
    }
    if (target.getStatus() == DocumentStatus.NOT_APPLICABLE) {
      target.requestAgain();
    }
    DocumentVersion latest =
        latestVersion(sourceDocumentId).orElseThrow(() -> new UnprocessableException("NO_FILE", "Este documento no tiene archivo que mover."));
    List<DocumentPage> pages = pageRepository.findByDocumentVersionIdOrderByPageNumberAsc(latest.getId());

    Completeness before = completenessOf(source.getExpedienteId());
    String previousStatus = target.getStatus().name();
    int versionNumber = target.startNewVersion();
    DocumentVersion moved =
        new DocumentVersion(
            targetDocumentId,
            versionNumber,
            clock.instant(),
            latest.getUploadedVia(),
            latest.getUploadedByUserId(),
            latest.getUploadedByName());
    versionRepository.save(moved);
    List<PageRef> pageRefs = new ArrayList<>();
    for (DocumentPage page : pages) {
      pageRepository.save(
          new DocumentPage(
              moved.getId(),
              page.getPageNumber(),
              page.getStorageKeyOriginal(),
              page.getOriginalFilename(),
              page.getMimeType(),
              page.getSize(),
              page.getSha256()));
      pageRefs.add(new PageRef(page.getPageNumber(), page.getStorageKeyOriginal(), page.getMimeType()));
    }
    source.detachCurrentFile();

    events.publishEvent(
        new DocumentFileMoved(source.getExpedienteId(), sourceDocumentId, source.getType(), targetDocumentId, target.getType(), actor));
    events.publishEvent(
        new DocumentVersionUploaded(
            target.getExpedienteId(), targetDocumentId, moved.getId(), target.getType(), pageRefs, target.getParticipantId(), actor, previousStatus));
    documentRepository.flush();
    publishCompletenessChanges(source.getExpedienteId(), before);
    return target;
  }

  public record ReviewCommand(
      UUID documentId,
      ReviewDecision decision,
      ReturnReasonCode reasonCode,
      String comment,
      String overrideJustification,
      boolean mayOverride,
      Actor actor) {}

  /**
   * Aceptar exige que la versión vigente no tenga alertas (calidad
   * insuficiente, archivo sin procesar, o la IA indicó que no corresponde al
   * documento solicitado o que es ilegible). Con alertas solo se puede
   * aceptar por excepción: con el permiso DOCUMENT_QUALITY_OVERRIDE y una
   * justificación, que queda registrada junto con el usuario responsable.
   * Devolver o rechazar exige un motivo, que el cliente verá en su liga.
   */
  public Document review(ReviewCommand command) {
    Document document = get(command.documentId());
    DocumentVersion latest = latestVersionOf(command.documentId());

    if (document.getStatus() != DocumentStatus.READY_FOR_REVIEW
        && document.getStatus() != DocumentStatus.UPLOADED
        && document.getStatus() != DocumentStatus.RETURNED) {
      throw new DocumentNotReviewableException(
          "Este documento no se puede revisar en su estado actual. Solo se revisan documentos cargados y pendientes de revisión.");
    }
    switch (latest.getProcessingStatus()) {
      case PROCESSING, QUEUED ->
          throw new DocumentNotReviewableException("El documento todavía se está procesando; espera unos segundos e intenta de nuevo.");
      default -> {
        /* PROCESSED, QUALITY_FAILED o FAILED sí pueden revisarse manualmente */
      }
    }
    if (command.decision() == ReviewDecision.ACCEPTED
        && aiEnabled
        && latest.getProcessingStatus() == ProcessingStatus.PROCESSED
        && latest.getAiAssessedAt() == null
        && latest.getUploadedAt().isAfter(clock.instant().minus(AI_CHECK_GRACE))) {
      throw new DocumentNotReviewableException(
          "La revisión automática del contenido todavía está en curso; espera unos segundos y actualiza antes de aceptarlo.");
    }

    String overrideJustification = null;
    String overriddenIssues = null;
    String comment = blankToNull(command.comment());

    if (command.decision() == ReviewDecision.ACCEPTED) {
      List<String> issues = latest.blockingIssues();
      if (!issues.isEmpty()) {
        String justification = blankToNull(command.overrideJustification());
        if (!command.mayOverride()) {
          throw new UnprocessableException(
              "DOCUMENT_NEEDS_OVERRIDE",
              "No se puede aceptar este archivo: "
                  + String.join("; ", issues)
                  + ". Devuélvelo al cliente o pide a un director o administrador que autorice la excepción.");
        }
        if (justification == null || justification.length() < MIN_JUSTIFICATION_LENGTH) {
          throw new UnprocessableException(
              "OVERRIDE_JUSTIFICATION_REQUIRED",
              "Para aceptar por excepción explica por qué el archivo es válido pese a las alertas (mínimo "
                  + MIN_JUSTIFICATION_LENGTH
                  + " caracteres): "
                  + String.join("; ", issues)
                  + ".");
        }
        overrideJustification = justification;
        overriddenIssues = String.join("; ", issues);
      }
    } else {
      if (command.reasonCode() == null) {
        throw new UnprocessableException("REASON_REQUIRED", "Selecciona el motivo; el cliente lo verá para saber qué corregir.");
      }
      if (command.reasonCode() == ReturnReasonCode.OTHER && comment == null) {
        throw new UnprocessableException("COMMENT_REQUIRED", "Describe el motivo en el comentario; el cliente lo verá para saber qué corregir.");
      }
    }

    Instant now = clock.instant();
    reviewRepository.save(
        new DocumentReview(
            latest.getId(),
            command.decision(),
            command.reasonCode(),
            comment,
            command.actor().userId(),
            now,
            overrideJustification,
            overriddenIssues));
    document.applyReview(command.decision(), command.reasonCode(), comment, now);

    // expedientes escucha este evento para decidir su propia transición de
    // estado (UNDER_REVIEW / CORRECTIONS_REQUESTED); documents no lo comanda
    // directamente, para evitar una dependencia cíclica entre módulos.
    events.publishEvent(
        new DocumentReviewed(
            document.getExpedienteId(),
            document.getId(),
            document.getType(),
            command.decision(),
            command.actor().userId(),
            command.actor(),
            command.reasonCode() == null ? null : command.reasonCode().name(),
            comment,
            overrideJustification));

    if (command.decision() == ReviewDecision.ACCEPTED && allRequiredAccepted(document.getExpedienteId())) {
      events.publishEvent(new AllRequiredDocumentsApproved(document.getExpedienteId()));
    }

    return document;
  }

  public Document markNotApplicable(UUID documentId, String justification, Actor actor) {
    Document document = get(documentId);
    Completeness before = completenessOf(document.getExpedienteId());
    String cleaned = blankToNull(justification);
    if (cleaned == null || cleaned.length() < MIN_JUSTIFICATION_LENGTH) {
      throw new UnprocessableException(
          "JUSTIFICATION_REQUIRED",
          "Explica por qué este documento no aplica a este expediente (mínimo " + MIN_JUSTIFICATION_LENGTH + " caracteres).");
    }
    document.markNotApplicable(cleaned, actor.userId(), clock.instant());
    events.publishEvent(
        new DocumentApplicabilityChanged(document.getExpedienteId(), documentId, document.getType(), true, cleaned, actor));
    documentRepository.flush();
    publishCompletenessChanges(document.getExpedienteId(), before);
    return document;
  }

  public Document requestAgain(UUID documentId, Actor actor) {
    Document document = get(documentId);
    Completeness before = completenessOf(document.getExpedienteId());
    document.requestAgain();
    events.publishEvent(new DocumentApplicabilityChanged(document.getExpedienteId(), documentId, document.getType(), false, null, actor));
    documentRepository.flush();
    publishCompletenessChanges(document.getExpedienteId(), before);
    return document;
  }

  /** Foto del avance documental, para avisar solo cuando realmente cambia (y no reenviar correos). */
  private record Completeness(boolean hasRequired, boolean hasPendingRequired, boolean allUploaded, boolean allAccepted) {}

  private Completeness completenessOf(UUID expedienteId) {
    // Serializa por expediente el cálculo antes/después: ver ExpedienteDocumentsLock.
    documentsLock.lock(expedienteId);
    List<Document> required = documentRepository.findByExpedienteId(expedienteId).stream().filter(Document::isRequired).toList();
    return new Completeness(
        !required.isEmpty(),
        required.stream().anyMatch(d -> d.getStatus() == DocumentStatus.PENDING),
        !required.isEmpty() && required.stream().allMatch(Document::isSatisfiedForSubmission),
        !required.isEmpty() && required.stream().allMatch(Document::isSatisfiedForApproval));
  }

  /**
   * Publica solo las transiciones: se completó la carga, se completó la
   * aprobación, o apareció un obligatorio sin cargar (p. ej. tras agregar un
   * copropietario), en cuyo caso el expediente debe volver a pedir documentos.
   */
  private void publishCompletenessChanges(UUID expedienteId, Completeness before) {
    Completeness after = completenessOf(expedienteId);
    if (after.hasPendingRequired() && !before.hasPendingRequired() && before.hasRequired()) {
      events.publishEvent(new RequiredDocumentsReopened(expedienteId));
    }
    if (after.allUploaded() && !before.allUploaded()) {
      events.publishEvent(new AllRequiredDocumentsUploaded(expedienteId));
    }
    if (after.allAccepted() && !before.allAccepted()) {
      events.publishEvent(new AllRequiredDocumentsApproved(expedienteId));
    }
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
  public void markProcessed(
      UUID documentVersionId, String pdfStorageKey, String normalizedStorageKey, String qualityLevel, List<String> qualityWarnings) {
    DocumentVersion version = findVersion(documentVersionId);
    version.completeProcessing(pdfStorageKey, normalizedStorageKey, qualityLevel, qualityWarnings);
    Document document = documentRepository.findById(version.getDocumentId()).orElseThrow();
    document.markReadyForReview();
  }

  /**
   * La versión no pasó la calidad automática: igual queda lista para que el
   * staff la vea (y la devuelva, o la acepte por excepción justificada), y el
   * cliente ve el motivo en su liga para volver a tomar la foto.
   */
  @Override
  public void markQualityFailed(UUID documentVersionId, String reason) {
    DocumentVersion version = findVersion(documentVersionId);
    version.failQuality(reason);
    documentRepository.findById(version.getDocumentId()).ifPresent(Document::markReadyForReview);
  }

  @Override
  public void markFailed(UUID documentVersionId, String reason) {
    DocumentVersion version = findVersion(documentVersionId);
    version.fail(reason);
    documentRepository.findById(version.getDocumentId()).ifPresent(Document::markReadyForReview);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean allRequiredUploaded(UUID expedienteId) {
    List<Document> docs = documentRepository.findByExpedienteId(expedienteId);
    return docs.stream().filter(Document::isRequired).allMatch(Document::isSatisfiedForSubmission);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean allRequiredAccepted(UUID expedienteId) {
    List<Document> required = documentRepository.findByExpedienteId(expedienteId).stream().filter(Document::isRequired).toList();
    if (required.isEmpty()) {
      // Los requisitos todavía no se materializan (evento asíncrono) o el
      // expediente no tiene documentos obligatorios: no se considera completo.
      return false;
    }
    return required.stream().allMatch(Document::isSatisfiedForApproval);
  }

  @Override
  @Transactional(readOnly = true)
  public List<RequirementStatusView> requirementStatusOf(UUID expedienteId) {
    return documentRepository.findByExpedienteId(expedienteId).stream()
        .map(
            d ->
                new RequirementStatusView(
                    d.getId(), d.getType(), d.getParticipantId(), d.isRequired(), d.getStatus().name(),
                    latestVersion(d.getId()).map(DocumentVersion::getId).orElse(null)))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Map<UUID, DocumentProgressView> documentProgressOf(Collection<UUID> expedienteIds) {
    if (expedienteIds.isEmpty()) {
      return Map.of();
    }
    Set<DocumentStatus> received = Set.of(DocumentStatus.UPLOADED, DocumentStatus.READY_FOR_REVIEW, DocumentStatus.ACCEPTED);
    return documentRepository.findByExpedienteIdIn(expedienteIds).stream()
        .filter(d -> d.isRequired() && d.getStatus() != DocumentStatus.NOT_APPLICABLE)
        .collect(
            Collectors.groupingBy(
                Document::getExpedienteId,
                Collectors.collectingAndThen(
                    Collectors.toList(),
                    docs ->
                        new DocumentProgressView(
                            docs.size(),
                            (int) docs.stream().filter(d -> received.contains(d.getStatus())).count(),
                            (int) docs.stream().filter(d -> d.getStatus() == DocumentStatus.ACCEPTED).count()))));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> currentVersionIdOf(UUID documentId) {
    return latestVersion(documentId).map(DocumentVersion::getId);
  }

  private DocumentVersion findVersion(UUID documentVersionId) {
    return versionRepository.findById(documentVersionId).orElseThrow(() -> new NotFoundException("Versión de documento", documentVersionId));
  }

  /** Confirmación explícita de staff, distinta de "todos aceptados" (ver AGENTS §87). */
  public void signReception(UUID expedienteId, Actor actor) {
    if (!allRequiredAccepted(expedienteId)) {
      throw new ReceptionNotReadyException(expedienteId);
    }
    events.publishEvent(new ReceptionSigned(expedienteId, actor.userId(), actor));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<String> currentAcceptedPdfStorageKey(UUID documentId) {
    Document document = get(documentId);
    if (document.getStatus() != DocumentStatus.ACCEPTED) {
      return Optional.empty();
    }
    return Optional.ofNullable(latestVersionOf(documentId).getStorageKeyPdf());
  }

  @Override
  @Transactional(readOnly = true)
  public List<UUID> documentIdsOf(UUID expedienteId) {
    return documentRepository.findByExpedienteId(expedienteId).stream().map(Document::getId).toList();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<UUID> expedienteIdOfDocument(UUID documentId) {
    return documentRepository.findById(documentId).map(Document::getExpedienteId);
  }

  @Override
  @Transactional(readOnly = true)
  public List<AcceptedDocumentView> acceptedDocumentsOf(UUID expedienteId) {
    // pdfStorageKey queda null cuando el staff aceptó manualmente una versión
    // que nunca generó PDF (p. ej. anuló un QUALITY_FAILED): sigue siendo
    // "aceptado" pero no hay archivo que adjuntar todavía.
    return documentRepository.findByExpedienteId(expedienteId).stream()
        .filter(d -> d.getStatus() == DocumentStatus.ACCEPTED)
        .map(
            d ->
                new AcceptedDocumentView(
                    d.getId(), d.getType().name(), d.getParticipantId(), currentAcceptedPdfStorageKey(d.getId()).orElse(null)))
        .toList();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
