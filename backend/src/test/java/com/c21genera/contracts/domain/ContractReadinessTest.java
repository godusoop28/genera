package com.c21genera.contracts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.c21genera.contracts.application.ContractService;
import com.c21genera.contracts.application.ContractService.Readiness;
import com.c21genera.documents.DocumentsApi;
import com.c21genera.documents.DocumentsApi.RequirementStatusView;
import com.c21genera.expedientes.AccreditationType;
import com.c21genera.expedientes.CivilStatus;
import com.c21genera.expedientes.ExpedienteLifecycleApi;
import com.c21genera.expedientes.ExpedienteSummary;
import com.c21genera.expedientes.PersonType;
import com.c21genera.expedientes.PropertyCaseType;
import com.c21genera.expedientes.SignerCharacter;
import com.c21genera.extraction.ExtractionApi;
import com.c21genera.extraction.ExtractionApi.ConflictView;
import com.c21genera.privacy.PrivacyApi;
import com.c21genera.privacy.PrivacyApi.ConsentView;
import com.c21genera.shared.domain.DocumentTypeCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * El contrato solo se bloquea por lo jurídicamente indispensable: una
 * dirección escrita distinto, un nombre en otro orden o una advertencia
 * revisable no impiden enviarlo a firma.
 */
class ContractReadinessTest {

  private final ExpedienteLifecycleApi expedienteApi = mock(ExpedienteLifecycleApi.class);
  private final DocumentsApi documentsApi = mock(DocumentsApi.class);
  private final PrivacyApi privacyApi = mock(PrivacyApi.class);
  private final ExtractionApi extractionApi = mock(ExtractionApi.class);
  private ContractService service;
  private ExpedienteSummary summary;

  @BeforeEach
  void setUp() {
    summary =
        ContractTemplateTest.summary(
            PersonType.FISICA, SignerCharacter.PROPIETARIO, AccreditationType.ESCRITURA_PUBLICA, false, PropertyCaseType.HOUSING,
            List.of(ContractTemplateTest.person("OWNER", "Juan Pérez López", 1, CivilStatus.CASADO)),
            ContractTemplateTest.deedDetails(ContractTemplateTest.housingChecklist()));
    when(expedienteApi.getSummary(summary.id())).thenReturn(summary);
    when(expedienteApi.getManualData(summary.id())).thenReturn(ContractTemplateTest.completeClientData());
    when(documentsApi.requirementStatusOf(summary.id()))
        .thenReturn(List.of(new RequirementStatusView(UUID.randomUUID(), DocumentTypeCode.DEED, null, true, "ACCEPTED")));
    when(documentsApi.acceptedDocumentsOf(any())).thenReturn(List.of());
    when(privacyApi.consentOf(summary.id())).thenReturn(Optional.of(new ConsentView(true, false, Instant.now())));
    service =
        new ContractService(
            expedienteApi, documentsApi, privacyApi, extractionApi, null, null, null, null, null, null, null,
            Clock.fixed(Instant.parse("2026-09-25T18:00:00Z"), ZoneOffset.UTC));
  }

  @Test
  void minorInconsistenciesAreListedForReviewButDoNotBlockTheSignature() {
    when(extractionApi.unresolvedConflictDescriptions(summary.id())).thenReturn(List.of());
    when(extractionApi.unresolvedConflicts(summary.id()))
        .thenReturn(
            List.of(
                new ConflictView("WARNING", "Posible inconsistencia: el domicilio del inmueble en Predial está escrito distinto"),
                new ConflictView("INFO", "Posible inconsistencia: Folio real distinta entre documentos")));

    Readiness readiness = service.readinessOf(summary.id());

    assertThat(readiness.ready()).isTrue();
    assertThat(readiness.blockers()).isEmpty();
    assertThat(readiness.inconsistencies()).hasSize(2).anyMatch(i -> i.startsWith("Revisar:")).anyMatch(i -> i.startsWith("Probablemente igual:"));
  }

  @Test
  void aCriticalInconsistencyIsTheOnlyKindThatBlocks() {
    when(extractionApi.unresolvedConflictDescriptions(summary.id()))
        .thenReturn(List.of("La CURP en INE no coincide con la registrada para Juan Pérez López"));
    when(extractionApi.unresolvedConflicts(summary.id()))
        .thenReturn(List.of(new ConflictView("CRITICAL", "La CURP en INE no coincide con la registrada para Juan Pérez López")));

    Readiness readiness = service.readinessOf(summary.id());

    assertThat(readiness.ready()).isFalse();
    assertThat(readiness.blockers()).singleElement().asString().startsWith("Inconsistencia crítica sin resolver");
    assertThat(readiness.inconsistencies()).isEmpty();
  }
}
