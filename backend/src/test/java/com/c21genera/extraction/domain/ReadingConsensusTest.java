package com.c21genera.extraction.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.extraction.domain.StructuredExtractionProvider.FieldResult;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Lecturas reales de producción (E2E 02/10) de la INE vertical. */
class ReadingConsensusTest {

  private static FieldResult r(String value, double confidence) {
    return new FieldResult("electorKey", value, confidence, 1);
  }

  @Test
  void twoIdenticalCleanReadingsAreAConsensus() {
    var decision = ReadingConsensus.decide("electorKey", List.of(r("RDCAL8503147H900", 0.48), r("RDCACL85031417H900", 0.9), r("rdcacl 85031417H900", 0.7)));

    assertThat(decision).hasValueSatisfying(d -> {
      assertThat(d.consensus()).isTrue();
      assertThat(d.field().value()).isEqualTo("RDCACL85031417H900");
      assertThat(d.field().confidence()).isEqualTo(0.9);
    });
  }

  @Test
  void readingsWithUnreadableCharactersOrABrokenFormatNeverCount() {
    var decision = ReadingConsensus.decide("electorKey", List.of(r("RDCC?85031417H900", 0.9), r("RDCC?85031417H900", 0.9), r("RDCAL8503147H900", 0.9)));

    assertThat(decision).isEmpty();
  }

  @Test
  void disagreeingCleanReadingsKeepTheBestOneInLowConfidence() {
    var decision = ReadingConsensus.decide("electorKey", List.of(r("RDQACL85031417H900", 0.98), r("RDCACL85031417H900", 0.6)));

    assertThat(decision).hasValueSatisfying(d -> {
      assertThat(d.consensus()).isFalse();
      assertThat(d.field().value()).isEqualTo("RDQACL85031417H900");
      assertThat(d.field().confidence()).isEqualTo(ReadingConsensus.DISAGREEMENT_MAX_CONFIDENCE);
      assertThat(d.distinctReadings()).containsExactly("RDQACL85031417H900", "RDCACL85031417H900");
    });
  }

  @Test
  void twoUnsureReadingsAreNotAConsensus() {
    var decision = ReadingConsensus.decide("electorKey", List.of(r("RDCACL85031417H900", 0.3), r("RDCACL85031417H900", 0.4)));

    assertThat(decision).hasValueSatisfying(d -> {
      assertThat(d.consensus()).isFalse();
      assertThat(d.field().confidence()).isEqualTo(0.4);
    });
  }

  @Test
  void identifiersCarryTheBirthDateTheyEncode() {
    assertThat(FieldFormats.embeddedDate("curp", "ROCC850314HMSDLR07")).contains("850314");
    assertThat(FieldFormats.embeddedDate("electorKey", "RDCACL85031417H900")).contains("850314");
    assertThat(FieldFormats.embeddedDate("rfc", "ROCC850314TQ7")).contains("850314");
    assertThat(FieldFormats.embeddedDate("rfc", "IDS260101AA1")).isEmpty(); // persona moral: fecha de constitución
    assertThat(FieldFormats.yymmdd("1985-03-14")).contains("850314");
    assertThat(FieldFormats.yymmdd("14/03/1985")).contains("850314");
  }
}
