package com.c21genera.extraction.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.extraction.domain.DocumentConsistencyChecker.AreaComparison;
import com.c21genera.extraction.domain.DocumentConsistencyChecker.DeclaredData;
import com.c21genera.extraction.domain.DocumentConsistencyChecker.DocumentFacts;
import com.c21genera.extraction.domain.DocumentConsistencyChecker.Finding;
import com.c21genera.extraction.domain.StructuredExtractionProvider.Alternative;
import com.c21genera.extraction.domain.StructuredExtractionProvider.ExtractionResult;
import com.c21genera.extraction.domain.StructuredExtractionProvider.FieldResult;
import com.c21genera.shared.domain.DocumentTypeCode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Controles de cambios #15 (cotejo del condominio) y #19 (varios valores en la escritura). */
class CondominiumAndAlternativesTest {

  private static DocumentFacts facts(DocumentTypeCode type, Map<String, String> fields) {
    return new DocumentFacts(UUID.randomUUID(), type, null, fields);
  }

  private static final DeclaredData NOTHING_DECLARED = new DeclaredData(List.of(), null, null, null);

  @Test
  void matchingAreasAreConsistentAndADifferentUndividedAreaRaisesAnAlert() {
    List<DocumentFacts> docs =
        List.of(
            facts(DocumentTypeCode.CONDOMINIUM_REGIME, Map.of("landArea", "85.40", "undividedLandArea", "12.5")),
            facts(DocumentTypeCode.DEED, Map.of("landArea", "85.4 m2", "undividedLandArea", "12.50")),
            facts(DocumentTypeCode.PROPERTY_TAX, Map.of("landArea", "85.40", "undividedLandArea", "19.00")));

    List<AreaComparison> rows = DocumentConsistencyChecker.condominiumAreas(docs);
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).consistent()).isTrue();
    assertThat(rows.get(1).consistent()).isFalse();
    assertThat(rows.get(1).values().get(DocumentTypeCode.PROPERTY_TAX)).isEqualTo("19.00");

    List<Finding> findings = DocumentConsistencyChecker.check(docs, NOTHING_DECLARED);
    assertThat(findings).extracting(Finding::key).contains("condo-undivided-area").doesNotContain("condo-land-area");
  }

  @Test
  void withoutACondominiumRegimeThereIsNoCondominiumComparison() {
    List<DocumentFacts> docs = List.of(facts(DocumentTypeCode.DEED, Map.of("landArea", "120")));
    assertThat(DocumentConsistencyChecker.condominiumAreas(docs)).isEmpty();
  }

  @Test
  void severalPossibleValuesAreKeptForAPersonToChoose() {
    ExtractedFieldObservation date =
        new ExtractedFieldObservation(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "registryDate", "2024-01-10", FieldOrigin.AI_EXTRACTED, 0.4, Instant.now(), 3);
    date.recordAlternatives(
        "2024-01-10",
        List.of(
            new Alternative("2008-03-15", "Escritura antecedente (primer propietario)", 2),
            new Alternative("2015-11-22", "Escritura antecedente (segunda transmisión)", 2),
            new Alternative("2024-01-10", "Instrumento actual", 1)));

    assertThat(date.getAlternatives()).extracting(Alternative::value).containsExactly("2008-03-15", "2015-11-22", "2024-01-10");

    date.confirm("2024-01-10", Instant.now(), UUID.randomUUID(), "Jorge");
    assertThat(date.getConfirmedByName()).isEqualTo("Jorge");
    assertThat(date.getConfirmedAt()).isNotNull();
  }

  @Test
  void aSingleValueIsNotPresentedAsAChoice() {
    ExtractedFieldObservation folio =
        new ExtractedFieldObservation(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "publicRegistryFolio", "123", FieldOrigin.AI_EXTRACTED, 0.9, Instant.now(), 5);
    folio.recordAlternatives("123", List.of(new Alternative("123", "Instrumento actual", 5)));
    assertThat(folio.getAlternatives()).isEmpty();
  }

  @Test
  void theConsolidatorKeepsAlternativesSeenInDifferentBatches() {
    ExtractionConsolidator consolidator = new ExtractionConsolidator();
    consolidator.add(
        new ExtractionResult(
            List.of(new FieldResult("notaryName", "Lic. Ana Ruiz", 0.6, 1, List.of(new Alternative("Lic. Ana Ruiz", "Instrumento actual", 1)))),
            null,
            List.of()));
    consolidator.add(
        new ExtractionResult(
            List.of(new FieldResult("notaryName", "Lic. Pedro Gil", 0.5, 4, List.of(new Alternative("Lic. Pedro Gil", "Antecedente", 4)))),
            null,
            List.of()));

    FieldResult result = consolidator.result(4, 4).fields().getFirst();
    assertThat(result.value()).isEqualTo("Lic. Ana Ruiz");
    assertThat(result.alternatives()).extracting(Alternative::value).containsExactly("Lic. Ana Ruiz", "Lic. Pedro Gil");
  }
}
