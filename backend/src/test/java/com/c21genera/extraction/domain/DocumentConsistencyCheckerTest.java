package com.c21genera.extraction.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.extraction.domain.DocumentConsistencyChecker.DeclaredData;
import com.c21genera.extraction.domain.DocumentConsistencyChecker.DeclaredParticipant;
import com.c21genera.extraction.domain.DocumentConsistencyChecker.DocumentFacts;
import com.c21genera.extraction.domain.DocumentConsistencyChecker.Finding;
import com.c21genera.shared.domain.DocumentTypeCode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentConsistencyCheckerTest {

  private static final UUID JUAN = UUID.randomUUID();
  private static final UUID MARIA = UUID.randomUUID();
  private static final String ADDRESS = "Calle Río Balsas No. Ext. 12, Col. Vista Hermosa, Cuernavaca, Morelos, C.P. 62290";

  private static DeclaredData declared(BigDecimal land, BigDecimal built) {
    return new DeclaredData(
        List.of(
            new DeclaredParticipant(JUAN, true, "Juan Pérez López", "PELJ800101AB1", "PELJ800101HMSRPN01"),
            new DeclaredParticipant(MARIA, true, "María Gómez Ruiz", null, null)),
        ADDRESS,
        land,
        built);
  }

  private static DocumentFacts doc(DocumentTypeCode type, UUID participant, Map<String, String> fields) {
    return new DocumentFacts(UUID.randomUUID(), type, participant, fields);
  }

  @Test
  void consistentDocumentsProduceNoFindings() {
    List<Finding> findings =
        DocumentConsistencyChecker.check(
            List.of(
                doc(DocumentTypeCode.INE, JUAN, Map.of("fullName", "PEREZ LOPEZ JUAN", "curp", "PELJ800101HMSRPN01")),
                doc(DocumentTypeCode.INE, MARIA, Map.of("fullName", "GOMEZ RUIZ MARIA")),
                doc(DocumentTypeCode.TAX_STATUS_CERTIFICATE, JUAN, Map.of("fullName", "JUAN PEREZ LOPEZ", "rfc", "PELJ-800101-AB1")),
                doc(
                    DocumentTypeCode.DEED,
                    null,
                    Map.of(
                        "ownerFullName", "Juan Pérez López y María Gómez Ruiz",
                        "propertyAddress", "Calle Rio Balsas numero 12, colonia Vista Hermosa",
                        "publicRegistryFolio", "FR-123456",
                        "landArea", "250.00 m2",
                        "builtArea", "180 m²")),
                doc(DocumentTypeCode.PROPERTY_TAX, null, Map.of("ownerFullName", "PEREZ LOPEZ JUAN", "cadastralKey", "1100-01-012-004", "landArea", "250")),
                doc(DocumentTypeCode.CADASTRAL_PLAN, null, Map.of("cadastralKey", "110001012004", "propertyAddress", "RIO BALSAS 12 VISTA HERMOSA")),
                doc(DocumentTypeCode.RPP_REGISTRATION_SLIP, null, Map.of("publicRegistryFolio", "fr 123456"))),
            declared(new BigDecimal("250"), new BigDecimal("181")));

    assertThat(findings).isEmpty();
  }

  @Test
  void coOwnersWithDifferentNamesAreNotAConflict() {
    // Antes se comparaba "fullName" entre todos los documentos: dos INE de
    // copropietarios distintos se marcaban como conflicto.
    List<Finding> findings =
        DocumentConsistencyChecker.check(
            List.of(
                doc(DocumentTypeCode.INE, JUAN, Map.of("fullName", "JUAN PEREZ LOPEZ")),
                doc(DocumentTypeCode.INE, MARIA, Map.of("fullName", "MARIA GOMEZ RUIZ"))),
            declared(null, null));

    assertThat(findings).isEmpty();
  }

  @Test
  void detectsNameAddressAreaCadastralAndOwnerDifferences() {
    List<Finding> findings =
        DocumentConsistencyChecker.check(
            List.of(
                doc(DocumentTypeCode.INE, JUAN, Map.of("fullName", "ROBERTO SANCHEZ DIAZ")),
                doc(
                    DocumentTypeCode.DEED,
                    null,
                    Map.of("ownerFullName", "Juan Pérez López", "propertyAddress", "Av. Morelos 455, Col. Centro", "landArea", "300")),
                doc(DocumentTypeCode.PROPERTY_TAX, null, Map.of("ownerFullName", "Pedro Martínez", "cadastralKey", "1100-01-012-004")),
                doc(DocumentTypeCode.CADASTRAL_PLAN, null, Map.of("cadastralKey", "1100-01-099-999", "landArea", "250"))),
            declared(new BigDecimal("250"), null));

    assertThat(findings).extracting(Finding::key)
        .contains(
            "identity-name:" + JUAN + ":INE",
            "owners:DEED",
            "owners:PROPERTY_TAX",
            "address:DEED",
            "cadastral-key",
            "land-area");
    assertThat(findings).filteredOn(f -> f.key().equals("owners:DEED")).first().extracting(Finding::description).asString().contains("María Gómez Ruiz");
  }

  @Test
  void propertyTaxInTheNameOfOneCoOwnerIsAccepted() {
    List<Finding> findings =
        DocumentConsistencyChecker.check(
            List.of(doc(DocumentTypeCode.PROPERTY_TAX, null, Map.of("ownerFullName", "GOMEZ RUIZ MARIA"))), declared(null, null));

    assertThat(findings).isEmpty();
  }

  @Test
  void rfcAndCurpDifferencesAreReported() {
    List<Finding> findings =
        DocumentConsistencyChecker.check(
            List.of(
                doc(DocumentTypeCode.INE, JUAN, Map.of("curp", "XXXX800101HMSRPN09")),
                doc(DocumentTypeCode.TAX_STATUS_CERTIFICATE, JUAN, Map.of("rfc", "ZZZZ800101AB1"))),
            declared(null, null));

    assertThat(findings).extracting(Finding::key).contains("identity-curp:" + JUAN + ":INE", "identity-rfc:" + JUAN);
  }

  @Test
  void abbreviationsAndPunctuationInAddressesAreTheSameAddress() {
    assertThat(DocumentConsistencyChecker.addressesMatch("Av. Reforma 120", "Avenida Reforma #120")).isTrue();
    assertThat(DocumentConsistencyChecker.addressesMatch("Calle Pino 5, Col. Las Fincas", "Pino No. 5, Colonia Las Fincas")).isTrue();
    assertThat(DocumentConsistencyChecker.namesMatch("PEREZ LOPEZ JUAN", "Juan Pérez López")).isTrue();
  }

  private static DeclaredData declaredAt(String address) {
    return new DeclaredData(
        List.of(new DeclaredParticipant(JUAN, true, "Juan Pérez López", "PELJ800101AB1", "PELJ800101HMSRPN01")), address, null, null);
  }

  @Test
  void minorDifferencesAreInfoOrWarningNeverCritical() {
    List<Finding> findings =
        DocumentConsistencyChecker.check(
            List.of(
                // Una letra distinta en el folio: probable error de lectura.
                doc(DocumentTypeCode.DEED, null, Map.of("ownerFullName", "Juan Pérez López", "publicRegistryFolio", "FR-123456")),
                doc(DocumentTypeCode.RPP_REGISTRATION_SLIP, null, Map.of("publicRegistryFolio", "FR-123458")),
                // Dirección escrita distinto (por lote/manzana en la escritura).
                doc(DocumentTypeCode.PROPERTY_TAX, null, Map.of("ownerFullName", "JUAN PEREZ", "propertyAddress", "Lote 7 Manzana 3 Fracc. Vista Hermosa"))),
            declaredAt(ADDRESS));

    assertThat(findings).isNotEmpty();
    assertThat(findings).noneMatch(f -> f.severity() == DataConflict.Severity.CRITICAL);
    assertThat(findings).filteredOn(f -> f.key().equals("registry-folio")).singleElement()
        .satisfies(f -> assertThat(f.severity()).isEqualTo(DataConflict.Severity.INFO));
    assertThat(findings).allMatch(f -> f.description().startsWith("Posible inconsistencia"));
  }

  @Test
  void onlyStrongEvidenceOfAnotherPersonOrPropertyIsCritical() {
    List<Finding> findings =
        DocumentConsistencyChecker.check(
            List.of(
                doc(DocumentTypeCode.INE, JUAN, Map.of("fullName", "ROBERTO SANCHEZ DIAZ", "curp", "SADR751212HDFNZB07")),
                doc(DocumentTypeCode.DEED, null, Map.of("ownerFullName", "Pedro Martínez Ortiz"))),
            declaredAt(ADDRESS));

    assertThat(findings).filteredOn(f -> f.key().startsWith("identity-name")).singleElement()
        .satisfies(f -> assertThat(f.severity()).isEqualTo(DataConflict.Severity.CRITICAL));
    assertThat(findings).filteredOn(f -> f.key().startsWith("identity-curp")).singleElement()
        .satisfies(f -> assertThat(f.severity()).isEqualTo(DataConflict.Severity.CRITICAL));
    assertThat(findings).filteredOn(f -> f.key().equals("owners:DEED")).singleElement()
        .satisfies(f -> assertThat(f.severity()).isEqualTo(DataConflict.Severity.CRITICAL));
  }

  @Test
  void aCurpMisreadByOneCharacterIsOnlyAWarning() {
    List<Finding> findings =
        DocumentConsistencyChecker.check(
            List.of(doc(DocumentTypeCode.INE, JUAN, Map.of("curp", "PELJ800101HMSRPN0I"))), declaredAt(null));

    assertThat(findings).singleElement().satisfies(f -> assertThat(f.severity()).isEqualTo(DataConflict.Severity.WARNING));
  }

  @Test
  void aPropertyTaxInTheNameOfAPreviousOwnerIsOnlyAWarning() {
    List<Finding> findings =
        DocumentConsistencyChecker.check(
            List.of(doc(DocumentTypeCode.PROPERTY_TAX, null, Map.of("ownerFullName", "Antonio Ruiz Hernández"))), declaredAt(null));

    assertThat(findings).singleElement().satisfies(f -> assertThat(f.severity()).isEqualTo(DataConflict.Severity.WARNING));
  }

  @Test
  void aPowerOfAttorneyFromAnotherPersonIsCritical() {
    UUID company = UUID.randomUUID();
    UUID sofia = UUID.randomUUID();
    DeclaredData moral =
        new DeclaredData(
            List.of(
                new DeclaredParticipant(company, true, "INMUEBLES DEMO DEL SUR, S.A. DE C.V.", "IDS260101AA1", null, false),
                new DeclaredParticipant(sofia, false, "SOFIA EJEMPLO RIVERA", null, null, true)),
            null, null, null);

    List<Finding> findings =
        DocumentConsistencyChecker.check(
            List.of(
                doc(DocumentTypeCode.POWER_OF_ATTORNEY, null, Map.of("grantorFullName", "CARLOS EDUARDO RODRIGUEZ CALDERON", "attorneyFullName", "DANIELA TORRES VEGA")),
                doc(DocumentTypeCode.INCORPORATION_DEED, null, Map.of("companyName", "INMUEBLES DEMO DEL SUR, S.A. DE C.V.", "legalRepresentativeFullName", "SOFIA EJEMPLO RIVERA"))),
            moral);

    assertThat(findings).extracting(Finding::key).containsExactlyInAnyOrder("poa-grantor", "poa-attorney");
    assertThat(findings).allMatch(f -> f.severity() == DataConflict.Severity.CRITICAL);
  }

  @Test
  void theRightPowerOfAttorneyRaisesNothing() {
    UUID daniela = UUID.randomUUID();
    DeclaredData represented =
        new DeclaredData(
            List.of(
                new DeclaredParticipant(JUAN, true, "CARLOS EDUARDO RODRIGUEZ CALDERON", null, null, false),
                new DeclaredParticipant(daniela, false, "DANIELA TORRES VEGA", null, null, true)),
            null, null, null);

    List<Finding> findings =
        DocumentConsistencyChecker.check(
            List.of(doc(DocumentTypeCode.POWER_OF_ATTORNEY, null, Map.of("grantorFullName", "CARLOS EDUARDO RODRIGUEZ CALDERON", "attorneyFullName", "DANIELA TORRES VEGA"))),
            represented);

    assertThat(findings).isEmpty();
  }

  @Test
  void numbersAreParsedFromFreeText() {
    assertThat(DocumentConsistencyChecker.parseNumber("1,250.50 m²")).isEqualByComparingTo("1250.50");
    assertThat(DocumentConsistencyChecker.parseNumber("sin dato")).isNull();
  }
}
