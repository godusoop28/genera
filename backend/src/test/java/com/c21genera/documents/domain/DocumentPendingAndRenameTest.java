package com.c21genera.documents.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c21genera.documents.DocumentStatus;
import com.c21genera.shared.domain.ConflictException;
import com.c21genera.shared.domain.DocumentTypeCode;
import com.c21genera.shared.domain.UnprocessableException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Controles de cambios #13, #14 y #18 del cliente (16/10). */
class DocumentPendingAndRenameTest {

  @Test
  void aPendingDocumentDoesNotBlockAndBecomesDeliveredWhenUploaded() {
    Document appraisal = new Document(UUID.randomUUID(), "avaluo", DocumentTypeCode.APPRAISAL, null, true);
    assertThat(appraisal.isSatisfiedForSubmission()).isFalse();

    appraisal.markPending("El valuador lo entrega la próxima semana", LocalDate.of(2026, 10, 23), UUID.randomUUID(), Instant.now());
    assertThat(appraisal.isMarkedPending()).isTrue();
    assertThat(appraisal.isSatisfiedForSubmission()).isTrue();
    assertThat(appraisal.isSatisfiedForApproval()).isTrue();

    appraisal.startNewVersion();
    assertThat(appraisal.isMarkedPending()).isFalse();
    assertThat(appraisal.getPendingNote()).isNull();
    assertThat(appraisal.getStatus()).isEqualTo(DocumentStatus.UPLOADED);
  }

  @Test
  void onlyCustomDocumentsCanBeRenamedAndTheFileIsUntouched() {
    Document custom = Document.custom(UUID.randomUUID(), "Constansia de mantenimiento", null);
    custom.startNewVersion();
    custom.rename("Constancia de mantenimiento");
    assertThat(custom.displayName()).isEqualTo("Constancia de mantenimiento");
    assertThat(custom.getCurrentVersionNumber()).isEqualTo(1);

    Document deed = new Document(UUID.randomUUID(), "escritura", DocumentTypeCode.DEED, null, true);
    assertThatThrownBy(() -> deed.rename("Otra cosa")).isInstanceOf(ConflictException.class);
  }

  @Test
  void theProofOfAddressRecordsWhichAcceptedReceiptWasDelivered() {
    Document proof = new Document(UUID.randomUUID(), "domicilio-1", DocumentTypeCode.PROOF_OF_ADDRESS, null, true);
    proof.changeAddressProofKind(AddressProofKind.parse("telmex"));
    assertThat(proof.getAddressProofKind()).isEqualTo(AddressProofKind.TELMEX);

    assertThatThrownBy(() -> AddressProofKind.parse("WATER")).isInstanceOf(UnprocessableException.class);
    Document ine = new Document(UUID.randomUUID(), "ine-1", DocumentTypeCode.INE, null, true);
    assertThatThrownBy(() -> ine.changeAddressProofKind(AddressProofKind.BANK_STATEMENT)).isInstanceOf(ConflictException.class);
  }
}
