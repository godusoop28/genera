package com.c21genera.documents.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c21genera.documents.DocumentStatus;
import com.c21genera.shared.domain.ConflictException;
import com.c21genera.shared.domain.DocumentTypeCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentReviewRulesTest {

  private static DocumentVersion version() {
    return new DocumentVersion(UUID.randomUUID(), 1, Instant.now(), UploadedVia.PUBLIC_PORTAL, null, null);
  }

  @Test
  void aProcessedLegibleMatchingFileHasNoBlockingIssues() {
    DocumentVersion v = version();
    v.completeProcessing("doc.pdf", "manifest.json");
    v.recordAiAssessment(true, true, "credencial INE", null, false, Instant.now());

    assertThat(v.blockingIssues()).isEmpty();
  }

  @Test
  void onlyAnUnreadableFileBlocksAcceptance() {
    DocumentVersion grayImage = version();
    grayImage.failQuality("La imagen está en blanco, gris o sin contraste");
    assertThat(grayImage.blockingIssues()).singleElement().asString().contains("gris");

    DocumentVersion illegible = version();
    illegible.completeProcessing("doc.pdf", "m.json");
    illegible.recordAiAssessment(null, false, null, null, false, Instant.now());
    assertThat(illegible.blockingIssues()).singleElement().asString().contains("no es legible");
  }

  @Test
  void aFileThatLooksLikeAnotherDocumentIsAWarningAndKeepsItsData() {
    DocumentVersion predialAsMarriage = version();
    predialAsMarriage.completeProcessing("doc.pdf", "m.json");
    predialAsMarriage.recordAiAssessment(false, true, "Predial", "Es un recibo predial", false, Instant.now());

    assertThat(predialAsMarriage.blockingIssues()).isEmpty();
    assertThat(predialAsMarriage.warnings()).anyMatch(w -> w.contains("Predial") && w.contains("se conservaron"));
  }

  @Test
  void qualityAndAiWarningsNeverBlockAcceptance() {
    DocumentVersion v = version();
    v.completeProcessing("doc.pdf", "m.json", "ACCEPTED_WITH_WARNINGS", List.of("La foto está algo borrosa", "Resolución baja (600x800 px)"));
    v.recordAiAssessment(
        true,
        true,
        "credencial INE (frente)",
        null,
        false,
        Instant.now(),
        new DocumentVersion.AiDetails(List.of("Solo se ve el frente de la credencial"), 1, 1, 6, 4));

    assertThat(v.blockingIssues()).isEmpty();
    assertThat(v.warnings()).containsExactly("La foto está algo borrosa", "Resolución baja (600x800 px)", "Solo se ve el frente de la credencial");
    assertThat(v.getAiFieldsFound()).isEqualTo(4);
  }

  @Test
  void notApplicableCountsAsSatisfiedAndBlocksUploadsUntilRequestedAgain() {
    Document document = new Document(UUID.randomUUID(), "recibo-agua", DocumentTypeCode.WATER_RECEIPT, null, true);

    document.markNotApplicable("El terreno no tiene toma de agua todavía", UUID.randomUUID(), Instant.now());

    assertThat(document.getStatus()).isEqualTo(DocumentStatus.NOT_APPLICABLE);
    assertThat(document.isSatisfiedForSubmission()).isTrue();
    assertThat(document.isSatisfiedForApproval()).isTrue();
    assertThatThrownBy(document::startNewVersion).isInstanceOf(ConflictException.class);

    document.requestAgain();
    assertThat(document.getStatus()).isEqualTo(DocumentStatus.PENDING);
    assertThat(document.isSatisfiedForSubmission()).isFalse();
  }

  @Test
  void anAcceptedDocumentCannotBeMarkedNotApplicable() {
    Document document = new Document(UUID.randomUUID(), "predial", DocumentTypeCode.PROPERTY_TAX, null, true);
    document.startNewVersion();
    document.applyReview(com.c21genera.shared.domain.ReviewDecision.ACCEPTED, null, null, Instant.now());

    assertThatThrownBy(() -> document.markNotApplicable("No aplica porque sí aplica", UUID.randomUUID(), Instant.now()))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void returnKeepsTheReasonForTheClient() {
    Document document = new Document(UUID.randomUUID(), "predial", DocumentTypeCode.PROPERTY_TAX, null, true);
    document.startNewVersion();
    document.applyReview(com.c21genera.shared.domain.ReviewDecision.RETURNED, ReturnReasonCode.MISSING_PAGE, "Falta la hoja 2", Instant.now());

    assertThat(document.getStatus()).isEqualTo(DocumentStatus.RETURNED);
    assertThat(document.getLastReviewReasonCode()).isEqualTo(ReturnReasonCode.MISSING_PAGE);
    assertThat(document.getLastReviewComment()).isEqualTo("Falta la hoja 2");
  }
}
