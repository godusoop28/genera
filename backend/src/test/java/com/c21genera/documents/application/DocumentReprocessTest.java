package com.c21genera.documents.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.c21genera.documents.DocumentStatus;
import com.c21genera.documents.ProcessingStatus;
import com.c21genera.documents.domain.Document;
import com.c21genera.documents.domain.DocumentPage;
import com.c21genera.documents.domain.DocumentVersion;
import com.c21genera.documents.domain.UploadedVia;
import com.c21genera.documents.infrastructure.DocumentPageRepository;
import com.c21genera.documents.infrastructure.DocumentRepository;
import com.c21genera.documents.infrastructure.DocumentReviewRepository;
import com.c21genera.documents.infrastructure.DocumentVersionRepository;
import com.c21genera.documents.infrastructure.ExpedienteDocumentsLock;
import com.c21genera.documents.infrastructure.FileValidator;
import com.c21genera.shared.config.AiProperties;
import com.c21genera.shared.domain.ConflictException;
import com.c21genera.shared.domain.DocumentTypeCode;
import com.c21genera.shared.events.Actor;
import com.c21genera.shared.events.DocumentEvents.DocumentFileMoved;
import com.c21genera.shared.events.DocumentEvents.DocumentReprocessRequested;
import com.c21genera.shared.events.DocumentEvents.DocumentVersionUploaded;
import com.c21genera.shared.storage.FileStorage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** "Reprocesar con IA" y "Cambiar tipo de documento" sin que el cliente vuelva a subir nada. */
class DocumentReprocessTest {

  private final DocumentRepository documents = mock(DocumentRepository.class);
  private final DocumentVersionRepository versions = mock(DocumentVersionRepository.class);
  private final DocumentPageRepository pages = mock(DocumentPageRepository.class);
  private final List<Object> published = new ArrayList<>();
  private DocumentService service;
  private final UUID expedienteId = UUID.randomUUID();
  private final Actor staff = mock(Actor.class);
  private final ExpedienteDocumentsLock documentsLock = mock(ExpedienteDocumentsLock.class);

  @BeforeEach
  void setUp() {
    ApplicationEventPublisher events = published::add;
    service =
        new DocumentService(
            documents, versions, pages, mock(DocumentReviewRepository.class), mock(FileStorage.class), mock(FileValidator.class), events,
            Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC),
            new AiProperties(false, "openai", "", "", "", Duration.ofSeconds(30), 0, 0, 0),
            documentsLock);
  }

  private Document documentWithVersion(DocumentTypeCode type, DocumentVersion version) {
    Document document = new Document(expedienteId, type.name().toLowerCase(), type, null, true);
    document.startNewVersion();
    when(documents.findById(document.getId())).thenReturn(Optional.of(document));
    when(versions.findByDocumentIdOrderByVersionNumberDesc(document.getId())).thenReturn(version == null ? List.of() : List.of(version));
    when(versions.findByDocumentIdAndRemovedAtIsNullOrderByVersionNumberDesc(document.getId()))
        .thenReturn(version == null ? List.of() : List.of(version));
    return document;
  }

  @Test
  void removingTheFileOfAnAcceptedDocumentLeavesItPendingToUploadAgain() {
    DocumentVersion version = new DocumentVersion(UUID.randomUUID(), 1, Instant.now(), UploadedVia.PUBLIC_PORTAL, null, null);
    version.completeProcessing("doc.pdf", "m.json");
    Document deed = documentWithVersion(DocumentTypeCode.DEED, version);
    deed.markReadyForReview();
    deed.applyReview(com.c21genera.shared.domain.ReviewDecision.ACCEPTED, null, null, Instant.now());
    when(documents.findByExpedienteId(expedienteId)).thenReturn(List.of(deed));

    service.removeFile(deed.getId(), staff);

    assertThat(deed.getStatus()).isEqualTo(DocumentStatus.PENDING);
    assertThat(deed.getLastReviewDecision()).isNull();
    // No se borra: queda marcada para auditoría y deja de ser la vigente.
    assertThat(version.isRemoved()).isTrue();
    assertThat(published)
        .anySatisfy(e -> assertThat(e).isInstanceOf(com.c21genera.shared.events.DocumentEvents.DocumentFileRemoved.class))
        .anySatisfy(e -> assertThat(e).isInstanceOf(com.c21genera.shared.events.DocumentEvents.RequiredDocumentsReopened.class));
  }

  @Test
  void aDocumentWithoutFileHasNothingToRemove() {
    Document deed = documentWithVersion(DocumentTypeCode.DEED, null);

    assertThatThrownBy(() -> service.removeFile(deed.getId(), staff))
        .isInstanceOf(com.c21genera.shared.domain.UnprocessableException.class);
  }

  @Test
  void onlyCustomDocumentsCanBeDeleted() {
    Document deed = documentWithVersion(DocumentTypeCode.DEED, null);
    Document custom = Document.custom(expedienteId, "Carta de no adeudo", null);
    when(documents.findById(custom.getId())).thenReturn(Optional.of(custom));

    assertThatThrownBy(() -> service.deleteCustomDocument(deed.getId(), staff)).isInstanceOf(ConflictException.class);
    service.deleteCustomDocument(custom.getId(), staff);

    org.mockito.Mockito.verify(documents).delete(custom);
  }

  @Test
  void staffCanUploadOverANotApplicableDocument() {
    Document acta = documentWithVersion(DocumentTypeCode.MARRIAGE_CERTIFICATE, null);
    acta.markNotApplicable("Soltero", null, Instant.now());
    when(versions.save(any())).thenAnswer(inv -> inv.getArgument(0));
    FileStorage storage = mock(FileStorage.class);
    FileValidator validator = mock(FileValidator.class);
    when(validator.validateInternal(org.mockito.ArgumentMatchers.anyLong(), any())).thenReturn("application/pdf");
    when(storage.store(any(), any(), org.mockito.ArgumentMatchers.anyLong(), any()))
        .thenReturn(new FileStorage.StoredObjectMetadata("k", 3, "sha"));
    service =
        new DocumentService(
            documents, versions, pages, mock(DocumentReviewRepository.class), storage, validator, published::add,
            Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC),
            new AiProperties(false, "openai", "", "", "", Duration.ofSeconds(30), 0, 0, 0),
            documentsLock);

    service.uploadVersion(acta.getId(), List.of(DocumentService.UploadedFileContent.of(new byte[] {1, 2, 3}, "acta.pdf")), UploadedVia.INTERNAL, staff);

    assertThat(acta.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
  }

  @Test
  void aProcessedVersionOnlyRepeatsTheAiExtraction() {
    DocumentVersion version = new DocumentVersion(UUID.randomUUID(), 1, Instant.now(), UploadedVia.PUBLIC_PORTAL, null, null);
    version.completeProcessing("expedientes/x/document.pdf", "manifest.json");
    version.recordAiAssessment(null, null, null, "El servicio no respondió", true, Instant.now());
    Document ine = documentWithVersion(DocumentTypeCode.INE, version);

    service.reprocess(ine.getId(), false, staff);

    assertThat(published).singleElement().isInstanceOfSatisfying(DocumentReprocessRequested.class, e -> {
      assertThat(e.pdfStorageKey()).isEqualTo("expedientes/x/document.pdf");
      assertThat(e.documentVersionId()).isEqualTo(version.getId());
    });
    // El resultado anterior deja de contar mientras llega el nuevo.
    assertThat(version.getAiAssessedAt()).isNull();
    assertThat(version.warnings()).isEmpty();
  }

  @Test
  void aVersionRejectedByTheOldQualityRulesIsReprocessedFromTheOriginalFiles() {
    DocumentVersion version = new DocumentVersion(UUID.randomUUID(), 1, Instant.now(), UploadedVia.PUBLIC_PORTAL, null, null);
    version.failQuality("La foto debe tomarse en posición vertical (el documento salió acostado)");
    Document acta = documentWithVersion(DocumentTypeCode.MARRIAGE_CERTIFICATE, version);

    service.reprocess(acta.getId(), false, staff);

    assertThat(version.getProcessingStatus()).isEqualTo(ProcessingStatus.QUEUED);
    assertThat(published).singleElement().isInstanceOfSatisfying(DocumentReprocessRequested.class, e -> assertThat(e.pdfStorageKey()).isNull());
  }

  @Test
  void aFileThatIsStillProcessingCannotBeReprocessedTwice() {
    DocumentVersion version = new DocumentVersion(UUID.randomUUID(), 1, Instant.now(), UploadedVia.PUBLIC_PORTAL, null, null);
    Document deed = documentWithVersion(DocumentTypeCode.DEED, version);

    assertThatThrownBy(() -> service.reprocess(deed.getId(), false, staff)).isInstanceOf(ConflictException.class);
  }

  @Test
  void completenessIsEvaluatedUnderThePerExpedienteLock() {
    DocumentVersion version = new DocumentVersion(UUID.randomUUID(), 1, Instant.now(), UploadedVia.PUBLIC_PORTAL, null, null);
    version.completeProcessing("doc.pdf", "m.json");
    Document marriage = documentWithVersion(DocumentTypeCode.MARRIAGE_CERTIFICATE, version);
    Document predial = new Document(expedienteId, "predial", DocumentTypeCode.PROPERTY_TAX, null, true);
    when(documents.findById(predial.getId())).thenReturn(Optional.of(predial));
    when(documents.findByExpedienteId(expedienteId)).thenReturn(List.of(marriage, predial));
    when(pages.findByDocumentVersionIdOrderByPageNumberAsc(version.getId())).thenReturn(List.of());
    when(versions.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.moveCurrentFile(marriage.getId(), predial.getId(), staff);

    // Sin el candado, cargas simultáneas no veían las otras y nunca se publicaba "todos cargados" (E2E 02/10).
    org.mockito.Mockito.verify(documentsLock, org.mockito.Mockito.atLeastOnce()).lock(expedienteId);
  }

  @Test
  void aPredialUploadedAsMarriageCertificateIsMovedWithoutReuploading() {
    DocumentVersion version = new DocumentVersion(UUID.randomUUID(), 1, Instant.now(), UploadedVia.PUBLIC_PORTAL, null, null);
    version.completeProcessing("doc.pdf", "m.json");
    Document marriage = documentWithVersion(DocumentTypeCode.MARRIAGE_CERTIFICATE, version);
    Document predial = new Document(expedienteId, "predial", DocumentTypeCode.PROPERTY_TAX, null, true);
    when(documents.findById(predial.getId())).thenReturn(Optional.of(predial));
    when(documents.findByExpedienteId(expedienteId)).thenReturn(List.of(marriage, predial));
    when(pages.findByDocumentVersionIdOrderByPageNumberAsc(version.getId()))
        .thenReturn(List.of(new DocumentPage(version.getId(), 1, "orig/predial.jpg", "predial.jpg", "image/jpeg", 1000, "abc")));
    when(versions.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.moveCurrentFile(marriage.getId(), predial.getId(), staff);

    assertThat(predial.getCurrentVersionNumber()).isEqualTo(1);
    assertThat(predial.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
    assertThat(marriage.getStatus()).isEqualTo(DocumentStatus.PENDING);
    assertThat(published).anyMatch(DocumentFileMoved.class::isInstance);
    assertThat(published)
        .filteredOn(DocumentVersionUploaded.class::isInstance)
        .singleElement()
        .isInstanceOfSatisfying(DocumentVersionUploaded.class, e -> {
          assertThat(e.type()).isEqualTo(DocumentTypeCode.PROPERTY_TAX);
          assertThat(e.pages()).singleElement().satisfies(p -> assertThat(p.storageKeyOriginal()).isEqualTo("orig/predial.jpg"));
        });
  }
}
