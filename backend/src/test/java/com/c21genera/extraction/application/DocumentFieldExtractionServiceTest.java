package com.c21genera.extraction.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.c21genera.documents.DocumentsApi;
import com.c21genera.expedientes.ExpedienteLifecycleApi;
import com.c21genera.expedientes.ExpedienteLifecycleApi.ManualClientDataView;
import com.c21genera.expedientes.ExpedienteSummary;
import com.c21genera.extraction.domain.ExtractedFieldObservation;
import com.c21genera.extraction.domain.FieldOrigin;
import com.c21genera.extraction.domain.StructuredExtractionProvider;
import com.c21genera.extraction.domain.StructuredExtractionProvider.ContentAssessment;
import com.c21genera.extraction.domain.StructuredExtractionProvider.ExtractionResult;
import com.c21genera.extraction.domain.StructuredExtractionProvider.FieldResult;
import com.c21genera.extraction.infrastructure.DataConflictRepository;
import com.c21genera.extraction.infrastructure.ExtractedFieldObservationRepository;
import com.c21genera.shared.domain.DocumentTypeCode;
import com.c21genera.shared.events.DocumentEvents.DocumentContentAssessed;
import com.c21genera.shared.storage.FileStorage;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class DocumentFieldExtractionServiceTest {

  private final ExtractedFieldObservationRepository observations = mock(ExtractedFieldObservationRepository.class);
  private final DataConflictRepository conflicts = mock(DataConflictRepository.class);
  private final StructuredExtractionProvider provider = mock(StructuredExtractionProvider.class);
  private final FileStorage storage = mock(FileStorage.class);
  private final DocumentsApi documentsApi = mock(DocumentsApi.class);
  private final ExpedienteLifecycleApi expedienteApi = mock(ExpedienteLifecycleApi.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final List<ExtractedFieldObservation> saved = new ArrayList<>();
  private DocumentFieldExtractionService service;

  private final UUID expedienteId = UUID.randomUUID();
  private final UUID documentId = UUID.randomUUID();
  private final UUID versionId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    when(tx.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
    when(storage.get(anyString())).thenAnswer(inv -> new ByteArrayInputStream(new byte[] {1}));
    when(observations.save(any())).thenAnswer(inv -> {
      saved.add(inv.getArgument(0));
      return inv.getArgument(0);
    });
    ExpedienteSummary summary = mock(ExpedienteSummary.class);
    when(summary.participants()).thenReturn(List.of());
    when(expedienteApi.getSummary(expedienteId)).thenReturn(summary);
    when(expedienteApi.getManualData(expedienteId)).thenReturn(mock(ManualClientDataView.class));
    when(documentsApi.requirementStatusOf(expedienteId)).thenReturn(List.of());
    service =
        new DocumentFieldExtractionService(
            observations, conflicts, provider, storage, documentsApi, expedienteApi, events, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), tx);
  }

  private ExtractDocumentFieldsPayload payload(DocumentTypeCode type) {
    return new ExtractDocumentFieldsPayload(expedienteId, documentId, versionId, type, "doc.pdf");
  }

  private DocumentContentAssessed publishedAssessment() {
    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(events, org.mockito.Mockito.atLeastOnce()).publishEvent(captor.capture());
    return captor.getAllValues().stream()
        .filter(DocumentContentAssessed.class::isInstance)
        .map(DocumentContentAssessed.class::cast)
        .findFirst()
        .orElseThrow();
  }

  @Test
  void aFileUploadedInTheWrongCategoryKeepsItsExtractedData() {
    when(provider.extract(eq(DocumentTypeCode.MARRIAGE_CERTIFICATE), any(), anyList()))
        .thenReturn(
            new ExtractionResult(
                List.of(new FieldResult("ownerFullName", "JUAN PEREZ", 0.9, 1), new FieldResult("cadastralKey", "1100-01", 0.85, 1)),
                new ContentAssessment(false, true, "Predial", "Es un recibo predial"),
                List.of()));

    service.extract(payload(DocumentTypeCode.MARRIAGE_CERTIFICATE));

    assertThat(saved).extracting(ExtractedFieldObservation::getFieldName).containsExactly("ownerFullName", "cadastralKey");
    DocumentContentAssessed assessed = publishedAssessment();
    assertThat(assessed.matchesExpectedType()).isFalse();
    assertThat(assessed.detectedDocumentKind()).isEqualTo("Predial");
  }

  @Test
  void aPartialIneExtractionIsSavedAndCountedAsPartial() {
    when(provider.extract(eq(DocumentTypeCode.INE), any(), anyList()))
        .thenReturn(
            new ExtractionResult(
                List.of(
                    new FieldResult("fullName", "JUAN PEREZ LOPEZ", 0.95, 1),
                    new FieldResult("curp", "PELJ800101HMSRPN01", 0.9, 1),
                    new FieldResult("birthDate", "1980-01-01", 0.4, 1)),
                new ContentAssessment(true, true, "credencial INE", null),
                List.of("La clave de elector no se distingue"),
                1,
                1));

    service.extract(payload(DocumentTypeCode.INE));

    assertThat(saved).hasSize(3);
    assertThat(saved).filteredOn(o -> o.getFieldName().equals("birthDate")).singleElement()
        .satisfies(o -> assertThat(o.getConfidence()).isEqualTo(0.4));
    DocumentContentAssessed assessed = publishedAssessment();
    assertThat(assessed.fieldsExpected()).isEqualTo(6);
    assertThat(assessed.fieldsFound()).isEqualTo(3);
    assertThat(assessed.warnings()).containsExactly("La clave de elector no se distingue");
  }

  @Test
  void anIdentifierWithAnInvalidFormatIsNeverPresentedAsReliable() {
    ExtractionResult guarded =
        DocumentFieldExtractionService.guardIdentifierFormats(
            new ExtractionResult(
                List.of(new FieldResult("electorKey", "RDCAL8503147H900", 0.9, 1), new FieldResult("curp", "ROCC850314HMSDLR07", 0.96, 1)),
                ContentAssessment.unknown(),
                List.of()));

    assertThat(guarded.fields()).filteredOn(f -> f.fieldName().equals("electorKey")).singleElement()
        .satisfies(f -> assertThat(f.confidence()).isEqualTo(0.3));
    assertThat(guarded.fields()).filteredOn(f -> f.fieldName().equals("curp")).singleElement()
        .satisfies(f -> assertThat(f.confidence()).isEqualTo(0.96));
    assertThat(guarded.warnings()).singleElement().asString().contains("clave de elector").contains("16 caracteres");
  }

  @Test
  void reprocessingReplacesAiDataButNeverOverwritesWhatTheStaffConfirmed() {
    ExtractedFieldObservation confirmed =
        new ExtractedFieldObservation(expedienteId, documentId, versionId, "curp", "PELJ800101HMSRPN0I", FieldOrigin.AI_EXTRACTED, 0.5, Instant.EPOCH);
    confirmed.confirm("PELJ800101HMSRPN01", Instant.EPOCH);
    when(observations.findByDocumentVersionId(versionId)).thenReturn(List.of(confirmed));
    when(provider.extract(eq(DocumentTypeCode.INE), any(), anyList()))
        .thenReturn(
            new ExtractionResult(
                List.of(new FieldResult("curp", "OTRA-LECTURA", 0.7, 1), new FieldResult("fullName", "JUAN PEREZ LOPEZ", 0.9, 1)),
                new ContentAssessment(true, true, "credencial INE", null),
                List.of()));

    service.extract(payload(DocumentTypeCode.INE));

    var order = inOrder(observations);
    order.verify(observations).deleteByDocumentVersionIdAndConfirmedValueIsNull(versionId);
    order.verify(observations).save(any());
    assertThat(saved).extracting(ExtractedFieldObservation::getFieldName).containsExactly("fullName");
  }
}
