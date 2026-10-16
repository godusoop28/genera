package com.c21genera.documents.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c21genera.documents.DocumentStatus;
import com.c21genera.shared.domain.ConflictException;
import com.c21genera.shared.domain.DocumentTypeCode;
import com.c21genera.shared.domain.ReviewDecision;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Controles de cambios #1, #5 y #10 del cliente (15/10). */
class DocumentFollowUpRulesTest {

  @Test
  void aCustomDocumentIsRequiredAndShownWithItsOwnName() {
    Document doc = Document.custom(UUID.randomUUID(), "Constancia de no adeudo de mantenimiento", null);

    assertThat(doc.isCustom()).isTrue();
    assertThat(doc.isRequired()).isTrue();
    assertThat(doc.getType()).isEqualTo(DocumentTypeCode.OTHER);
    assertThat(doc.displayName()).isEqualTo("Constancia de no adeudo de mantenimiento");
    assertThat(doc.getRequirementCode()).startsWith("extra-").hasSizeLessThanOrEqualTo(64);
  }

  @Test
  void anApprovedExceptionDoesNotBlockSubmissionOrApprovalAndCanStillBeDeliveredLater() {
    Document slip = new Document(UUID.randomUUID(), "boleta-rpp", DocumentTypeCode.RPP_REGISTRATION_SLIP, null, true);
    assertThat(slip.isSatisfiedForApproval()).isFalse();

    slip.approveException("La boleta está en trámite en el RPP", UUID.randomUUID(), Instant.now());
    assertThat(slip.hasException()).isTrue();
    assertThat(slip.isSatisfiedForSubmission()).isTrue();
    assertThat(slip.isSatisfiedForApproval()).isTrue();

    slip.startNewVersion();
    assertThat(slip.getStatus()).isEqualTo(DocumentStatus.UPLOADED);

    slip.revokeException();
    assertThat(slip.isSatisfiedForApproval()).isFalse();
  }

  @Test
  void anAcceptedDocumentNeedsNoException() {
    Document deed = new Document(UUID.randomUUID(), "escritura", DocumentTypeCode.DEED, null, true);
    deed.startNewVersion();
    deed.applyReview(ReviewDecision.ACCEPTED, null, null, Instant.now());

    assertThatThrownBy(() -> deed.approveException("Ya no hace falta", UUID.randomUUID(), Instant.now()))
        .isInstanceOf(ConflictException.class);
  }

  @Test
  void requestingAnUpdateKeepsTheCurrentVersionAndTheNewUploadBecomesTheNextVersion() {
    Document csf = new Document(UUID.randomUUID(), "csf", DocumentTypeCode.TAX_STATUS_CERTIFICATE, UUID.randomUUID(), true);
    assertThatThrownBy(() -> csf.requestUpdate("Vencida", Instant.now())).isInstanceOf(ConflictException.class);

    csf.startNewVersion();
    csf.applyReview(ReviewDecision.ACCEPTED, null, null, Instant.now());
    csf.requestUpdate("La constancia tiene más de 3 meses; súbela actualizada", Instant.now());

    assertThat(csf.isUpdateRequested()).isTrue();
    assertThat(csf.getStatus()).isEqualTo(DocumentStatus.ACCEPTED);
    assertThat(csf.isSatisfiedForApproval()).isTrue();

    assertThat(csf.startNewVersion()).isEqualTo(2);
    assertThat(csf.isUpdateRequested()).isFalse();
    assertThat(csf.getCurrentVersionNumber()).isEqualTo(2);
  }
}
