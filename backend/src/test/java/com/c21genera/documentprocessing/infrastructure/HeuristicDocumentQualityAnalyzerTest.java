package com.c21genera.documentprocessing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.documentprocessing.domain.DocumentQualityAnalyzer.QualityResult;
import org.junit.jupiter.api.Test;

class HeuristicDocumentQualityAnalyzerTest {

  private final HeuristicDocumentQualityAnalyzer analyzer = new HeuristicDocumentQualityAnalyzer();

  @Test
  void acceptsAGoodResolutionPhoto() {
    QualityResult result = analyzer.analyze(new byte[]{1, 2, 3}, "image/jpeg", 1200, 1600);

    assertThat(result.acceptable()).isTrue();
    assertThat(result.issues()).isEmpty();
  }

  @Test
  void rejectsALowResolutionPhoto() {
    QualityResult result = analyzer.analyze(new byte[]{1, 2, 3}, "image/jpeg", 200, 300);

    assertThat(result.acceptable()).isFalse();
    assertThat(result.issues()).isNotEmpty();
  }

  @Test
  void rejectsAnExtremeAspectRatio() {
    QualityResult result = analyzer.analyze(new byte[]{1, 2, 3}, "image/jpeg", 5000, 500);

    assertThat(result.acceptable()).isFalse();
  }

  @Test
  void rejectsAnEmptyFile() {
    QualityResult result = analyzer.analyze(new byte[0], "image/jpeg", 1200, 1600);

    assertThat(result.acceptable()).isFalse();
  }
}
