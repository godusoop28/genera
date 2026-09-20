package com.c21genera.documentprocessing.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.documentprocessing.domain.DocumentQualityAnalyzer.QualityResult;
import com.c21genera.shared.domain.DocumentTypeCode;
import org.junit.jupiter.api.Test;

class HeuristicDocumentQualityAnalyzerTest {

  private final HeuristicDocumentQualityAnalyzer analyzer = new HeuristicDocumentQualityAnalyzer();

  @Test
  void acceptsAGoodResolutionVerticalPhoto() {
    QualityResult result = analyzer.analyze(new byte[]{1, 2, 3}, "image/jpeg", 1200, 1600, DocumentTypeCode.DEED);

    assertThat(result.acceptable()).isTrue();
    assertThat(result.issues()).isEmpty();
  }

  @Test
  void rejectsALowResolutionPhoto() {
    QualityResult result = analyzer.analyze(new byte[]{1, 2, 3}, "image/jpeg", 200, 300, DocumentTypeCode.DEED);

    assertThat(result.acceptable()).isFalse();
    assertThat(result.issues()).isNotEmpty();
  }

  @Test
  void rejectsAnExtremeAspectRatio() {
    QualityResult result = analyzer.analyze(new byte[]{1, 2, 3}, "image/jpeg", 5000, 500, DocumentTypeCode.DEED);

    assertThat(result.acceptable()).isFalse();
  }

  @Test
  void rejectsAnEmptyFile() {
    QualityResult result = analyzer.analyze(new byte[0], "image/jpeg", 1200, 1600, DocumentTypeCode.DEED);

    assertThat(result.acceptable()).isFalse();
  }

  @Test
  void rejectsALandscapePhotoForADocumentThatShouldBeVertical() {
    QualityResult result = analyzer.analyze(new byte[]{1, 2, 3}, "image/jpeg", 1600, 1200, DocumentTypeCode.PROOF_OF_ADDRESS);

    assertThat(result.acceptable()).isFalse();
    assertThat(result.issues()).anyMatch(issue -> issue.contains("posición vertical"));
  }

  @Test
  void acceptsALandscapePhotoForACardShapedDocument() {
    QualityResult result = analyzer.analyze(new byte[]{1, 2, 3}, "image/jpeg", 1600, 1200, DocumentTypeCode.INE);

    assertThat(result.acceptable()).isTrue();
  }
}
