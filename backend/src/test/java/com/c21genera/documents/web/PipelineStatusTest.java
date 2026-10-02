package com.c21genera.documents.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.documents.domain.Document;
import com.c21genera.documents.domain.DocumentVersion;
import com.c21genera.documents.domain.UploadedVia;
import com.c21genera.shared.domain.DocumentTypeCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** E2E 02/10: todas las versiones salían "Requiere revisión" por notas informativas de la IA. */
class PipelineStatusTest {

  private static Document document() {
    Document d = new Document(UUID.randomUUID(), "escritura", DocumentTypeCode.DEED, null, true);
    d.startNewVersion();
    return d;
  }

  private static DocumentVersion assessed(Boolean matches, List<String> aiWarnings, int expected, int found) {
    DocumentVersion v = new DocumentVersion(UUID.randomUUID(), 1, Instant.now(), UploadedVia.PUBLIC_PORTAL, null, null);
    v.completeProcessing("doc.pdf", "m.json");
    v.recordAiAssessment(matches, true, "escritura", null, false, Instant.now(), new DocumentVersion.AiDetails(aiWarnings, 1, 1, expected, found));
    return v;
  }

  @Test
  void informationalAiNotesDoNotForceAReview() {
    DocumentVersion v = assessed(true, List.of("No se observa la ciudad y estado de la notaría."), 10, 9);

    assertThat(DocumentDtos.pipelineStatusOf(document(), v)).isEqualTo("EXTRACTION_PARTIAL");
  }

  @Test
  void aCompleteExtractionIsComplete() {
    assertThat(DocumentDtos.pipelineStatusOf(document(), assessed(true, List.of(), 6, 6))).isEqualTo("EXTRACTION_COMPLETE");
  }

  @Test
  void aFileThatLooksLikeAnotherDocumentStillNeedsReview() {
    assertThat(DocumentDtos.pipelineStatusOf(document(), assessed(false, List.of(), 6, 6))).isEqualTo("REQUIRES_REVIEW");
  }
}
