package com.c21genera.expedientes.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c21genera.expedientes.ExpedienteStatus;
import org.junit.jupiter.api.Test;

class ExpedienteStateMachineTest {

  @Test
  void allowsTheHappyPathEndToEnd() {
    assertThat(ExpedienteStateMachine.isAllowed(ExpedienteStatus.DRAFT, ExpedienteStatus.WAITING_PRIVACY)).isTrue();
    assertThat(ExpedienteStateMachine.isAllowed(ExpedienteStatus.WAITING_PRIVACY, ExpedienteStatus.WAITING_DOCUMENTS))
        .isTrue();
    assertThat(
            ExpedienteStateMachine.isAllowed(ExpedienteStatus.WAITING_DOCUMENTS, ExpedienteStatus.DOCUMENTS_RECEIVED))
        .isTrue();
    assertThat(ExpedienteStateMachine.isAllowed(ExpedienteStatus.DOCUMENTS_RECEIVED, ExpedienteStatus.UNDER_REVIEW))
        .isTrue();
    assertThat(ExpedienteStateMachine.isAllowed(ExpedienteStatus.UNDER_REVIEW, ExpedienteStatus.DOCUMENTS_APPROVED))
        .isTrue();
    assertThat(ExpedienteStateMachine.isAllowed(ExpedienteStatus.DOCUMENTS_APPROVED, ExpedienteStatus.RECEPTION_SIGNED))
        .isTrue();
    assertThat(
            ExpedienteStateMachine.isAllowed(ExpedienteStatus.RECEPTION_SIGNED, ExpedienteStatus.CONTRACT_PREPARATION))
        .isTrue();
    assertThat(
            ExpedienteStateMachine.isAllowed(
                ExpedienteStatus.CONTRACT_PREPARATION, ExpedienteStatus.READY_FOR_SIGNATURE))
        .isTrue();
    assertThat(ExpedienteStateMachine.isAllowed(ExpedienteStatus.READY_FOR_SIGNATURE, ExpedienteStatus.CONTRACT_SIGNED))
        .isTrue();
    assertThat(ExpedienteStateMachine.isAllowed(ExpedienteStatus.CONTRACT_SIGNED, ExpedienteStatus.PROPERTY_ACCEPTED))
        .isTrue();
    assertThat(ExpedienteStateMachine.isAllowed(ExpedienteStatus.PROPERTY_ACCEPTED, ExpedienteStatus.CLOSED)).isTrue();
  }

  @Test
  void propertyCanOnlyBeAcceptedWithTheContractSigned() {
    for (ExpedienteStatus from : ExpedienteStatus.values()) {
      boolean allowed = ExpedienteStateMachine.isAllowed(from, ExpedienteStatus.PROPERTY_ACCEPTED);
      assertThat(allowed).as(from.name()).isEqualTo(from == ExpedienteStatus.CONTRACT_SIGNED);
    }
  }

  @Test
  void aCorrectionAfterApprovalReturnsTheExpedienteToCorrections() {
    for (ExpedienteStatus from :
        java.util.List.of(
            ExpedienteStatus.DOCUMENTS_APPROVED,
            ExpedienteStatus.RECEPTION_SIGNED,
            ExpedienteStatus.CONTRACT_PREPARATION,
            ExpedienteStatus.READY_FOR_SIGNATURE)) {
      assertThat(ExpedienteStateMachine.isAllowed(from, ExpedienteStatus.CORRECTIONS_REQUESTED)).as(from.name()).isTrue();
    }
    assertThat(ExpedienteStateMachine.isAllowed(ExpedienteStatus.CONTRACT_SIGNED, ExpedienteStatus.CORRECTIONS_REQUESTED)).isFalse();
  }

  @Test
  void anInvalidatedContractGoesBackToPreparation() {
    assertThat(ExpedienteStateMachine.isAllowed(ExpedienteStatus.READY_FOR_SIGNATURE, ExpedienteStatus.CONTRACT_PREPARATION))
        .isTrue();
  }

  @Test
  void rejectsSkippingWaitingPrivacyDirectlyToPropertyAccepted() {
    assertThat(ExpedienteStateMachine.isAllowed(ExpedienteStatus.WAITING_PRIVACY, ExpedienteStatus.PROPERTY_ACCEPTED))
        .isFalse();

    assertThatThrownBy(
            () -> ExpedienteStateMachine.ensureAllowed(ExpedienteStatus.WAITING_PRIVACY, ExpedienteStatus.PROPERTY_ACCEPTED))
        .isInstanceOf(InvalidExpedienteTransitionException.class);
  }

  @Test
  void underReviewCanGoToCorrectionsRequestedWhenDocumentsAreReturned() {
    assertThat(ExpedienteStateMachine.isAllowed(ExpedienteStatus.UNDER_REVIEW, ExpedienteStatus.CORRECTIONS_REQUESTED))
        .isTrue();
  }

  @Test
  void correctionsRequestedCanGoBackToUnderReviewWhenClientReplacesDocuments() {
    assertThat(ExpedienteStateMachine.isAllowed(ExpedienteStatus.CORRECTIONS_REQUESTED, ExpedienteStatus.UNDER_REVIEW))
        .isTrue();
  }

  @Test
  void cannotAcceptPropertyWithoutReceptionSigned() {
    assertThat(ExpedienteStateMachine.isAllowed(ExpedienteStatus.DOCUMENTS_APPROVED, ExpedienteStatus.PROPERTY_ACCEPTED))
        .isFalse();
    assertThat(ExpedienteStateMachine.isAllowed(ExpedienteStatus.UNDER_REVIEW, ExpedienteStatus.PROPERTY_ACCEPTED))
        .isFalse();
  }

  @Test
  void closedIsTerminal() {
    for (ExpedienteStatus target : ExpedienteStatus.values()) {
      assertThat(ExpedienteStateMachine.isAllowed(ExpedienteStatus.CLOSED, target)).isFalse();
    }
  }

  @Test
  void propertyRejectedIsReachableFromSeveralLaterStages() {
    assertThat(ExpedienteStateMachine.isAllowed(ExpedienteStatus.UNDER_REVIEW, ExpedienteStatus.PROPERTY_REJECTED))
        .isTrue();
    assertThat(
            ExpedienteStateMachine.isAllowed(ExpedienteStatus.DOCUMENTS_APPROVED, ExpedienteStatus.PROPERTY_REJECTED))
        .isTrue();
    assertThat(
            ExpedienteStateMachine.isAllowed(ExpedienteStatus.RECEPTION_SIGNED, ExpedienteStatus.PROPERTY_REJECTED))
        .isTrue();
    assertThat(ExpedienteStateMachine.isAllowed(ExpedienteStatus.PROPERTY_REJECTED, ExpedienteStatus.CLOSED)).isTrue();
  }
}
