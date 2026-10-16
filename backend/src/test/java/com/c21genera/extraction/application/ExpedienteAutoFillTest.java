package com.c21genera.extraction.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.documents.DocumentsApi.RequirementStatusView;
import com.c21genera.expedientes.ExpedienteLifecycleApi.DocumentFill;
import com.c21genera.extraction.application.DocumentFieldExtractionService.DocumentObservation;
import com.c21genera.extraction.domain.ExtractedFieldObservation;
import com.c21genera.extraction.domain.FieldOrigin;
import com.c21genera.shared.domain.DocumentTypeCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Control de Cambios #7 (y #8/#9): lo validado en los documentos llena solo el contrato. */
class ExpedienteAutoFillTest {

  private static final UUID OWNER = UUID.randomUUID();

  private static DocumentObservation obs(DocumentTypeCode type, UUID participant, String field, String value) {
    UUID documentId = UUID.randomUUID();
    ExtractedFieldObservation o =
        new ExtractedFieldObservation(UUID.randomUUID(), documentId, UUID.randomUUID(), field, value, FieldOrigin.AI_EXTRACTED, 0.95, Instant.now());
    return new DocumentObservation(o, new RequirementStatusView(documentId, type, participant, true, "ACCEPTED"));
  }

  @Test
  void deedAreasNotaryAndRealFolioFromTheSlipFillTheContract() {
    DocumentFill fill =
        ExpedienteAutoFill.build(
            List.of(
                obs(DocumentTypeCode.DEED, null, "deedNumber", "12,345"),
                obs(DocumentTypeCode.DEED, null, "deedDate", "18 de abril de 2022"),
                obs(DocumentTypeCode.DEED, null, "notaryName", "Lic. Ana Torres"),
                obs(DocumentTypeCode.DEED, null, "notaryNumber", "7"),
                obs(DocumentTypeCode.DEED, null, "publicRegistryFolio", "según escritura"),
                obs(DocumentTypeCode.DEED, null, "landArea", "1,250.50 m2"),
                obs(DocumentTypeCode.PROPERTY_TAX, null, "builtArea", "180 m²"),
                obs(DocumentTypeCode.RPP_REGISTRATION_SLIP, null, "publicRegistryFolio", "FR-889120")),
            OWNER);

    assertThat(fill.legal())
        .containsEntry("deed.number", "12,345")
        .containsEntry("deed.date", "2022-04-18")
        .containsEntry("deed.notaryName", "Lic. Ana Torres")
        .containsEntry("deed.notaryNumber", "7")
        // La boleta del RPP gana el folio real sobre lo que diga la escritura.
        .containsEntry("deed.registryData", "FR-889120")
        .containsEntry("privateContract.realFolio", "FR-889120");
    assertThat(fill.landAreaM2()).isEqualByComparingTo("1250.50");
    assertThat(fill.builtAreaM2()).isEqualByComparingTo("180");
  }

  @Test
  void anIneMakesTheHolderMexicanButAForeignPassportDoesNot() {
    UUID coOwner = UUID.randomUUID();
    DocumentFill fill =
        ExpedienteAutoFill.build(
            List.of(
                obs(DocumentTypeCode.INE, OWNER, "electorKey", "RDCACL85031417H900"),
                obs(DocumentTypeCode.INE, OWNER, "birthDate", "14/03/1985"),
                obs(DocumentTypeCode.TAX_STATUS_CERTIFICATE, OWNER, "rfc", "ROCC850314TQ7"),
                obs(DocumentTypeCode.INE, coOwner, "identificationType", "Pasaporte"),
                obs(DocumentTypeCode.INE, coOwner, "documentNumber", "A12345678"),
                obs(DocumentTypeCode.INE, coOwner, "issuingCountry", "Estados Unidos de América")),
            OWNER);

    assertThat(fill.participants().get(OWNER))
        .containsEntry("idDocumentNumber", "RDCACL85031417H900")
        .containsEntry("idDocumentType", "INE")
        .containsEntry("idDocumentIssuer", "Instituto Nacional Electoral")
        .containsEntry("birthDate", "1985-03-14")
        .containsEntry("rfc", "ROCC850314TQ7")
        .containsEntry("nationality", "Mexicana");
    assertThat(fill.participants().get(coOwner))
        .containsEntry("idDocumentType", "PASAPORTE")
        .containsEntry("idDocumentNumber", "A12345678")
        .containsEntry("idDocumentIssuer", "Gobierno de Estados Unidos de América")
        .doesNotContainKey("nationality");
  }

  @Test
  void identificationKindsAreRecognized() {
    assertThat(ExpedienteAutoFill.identificationKind("Credencial para votar (IFE)")).isEqualTo("INE");
    assertThat(ExpedienteAutoFill.identificationKind("TARJETA DE RESIDENTE PERMANENTE")).isEqualTo("FM2_RESIDENTE");
    assertThat(ExpedienteAutoFill.identificationKind("Licencia de conducir")).isEqualTo("OTRA");
    assertThat(ExpedienteAutoFill.normalizeDate("31/02/2020")).isNull();
  }
}
