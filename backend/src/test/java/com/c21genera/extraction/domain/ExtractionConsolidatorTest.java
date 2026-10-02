package com.c21genera.extraction.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.extraction.domain.StructuredExtractionProvider.ContentAssessment;
import com.c21genera.extraction.domain.StructuredExtractionProvider.ExtractionResult;
import com.c21genera.extraction.domain.StructuredExtractionProvider.FieldResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExtractionConsolidatorTest {

  @Test
  void keepsTheMostConfidentValueOfEachFieldAcrossBatchesAndAllWarnings() {
    ExtractionConsolidator consolidator = new ExtractionConsolidator();
    consolidator.add(
        new ExtractionResult(
            List.of(new FieldResult("landArea", "25O", 0.4, 3), new FieldResult("deedNumber", "45,678", 0.9, 1)),
            new ContentAssessment(true, true, "escritura pública", null),
            List.of("La página 3 está borrosa")));
    consolidator.add(
        new ExtractionResult(
            List.of(new FieldResult("landArea", "250", 0.95, 41)), new ContentAssessment(null, false, null, null), List.of("Sello ilegible")));

    ExtractionResult result = consolidator.result(16, 80);

    assertThat(result.fields()).filteredOn(f -> f.fieldName().equals("landArea")).singleElement().satisfies(f -> {
      assertThat(f.value()).isEqualTo("250");
      assertThat(f.page()).isEqualTo(41);
    });
    // Que una parte no se lea no hace ilegible un documento cuyo resto sí se leyó.
    assertThat(result.assessment().legible()).isTrue();
    assertThat(result.warnings()).containsExactly("La página 3 está borrosa", "Sello ilegible");
    assertThat(result.pagesAnalyzed()).isEqualTo(16);
  }

  @Test
  void aLowConfidenceFieldIsKeptButStillSearchedForInMorePages() {
    ExtractionConsolidator consolidator = new ExtractionConsolidator();
    consolidator.add(new ExtractionResult(List.of(new FieldResult("curp", "PELJ800101HMSRPN0?", 0.3)), ContentAssessment.unknown(), List.of()));

    assertThat(consolidator.pending(List.of("curp", "fullName"))).containsExactly("curp", "fullName");
    assertThat(consolidator.result(1, 1).fields()).extracting(FieldResult::fieldName).containsExactly("curp");
  }
}
