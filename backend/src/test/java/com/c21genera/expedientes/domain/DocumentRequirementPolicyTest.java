package com.c21genera.expedientes.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.shared.domain.RequiredDocumentSpec;
import com.c21genera.shared.domain.DocumentTypeCode;
import com.c21genera.expedientes.AccreditationType;
import com.c21genera.expedientes.PersonType;
import com.c21genera.expedientes.PropertyCaseType;
import com.c21genera.expedientes.SignerCharacter;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentRequirementPolicyTest {

  private static ExpedienteParticipant participant() {
    return new ExpedienteParticipant(UUID.randomUUID(), ParticipantRole.OWNER, "Juan Pérez López", 1);
  }

  private static long countRequired(List<RequiredDocumentSpec> specs, DocumentTypeCode type) {
    return specs.stream().filter(s -> s.type() == type && s.required()).count();
  }

  @Test
  void oneOwnerGeneratesOneIne() {
    List<RequiredDocumentSpec> specs =
        DocumentRequirementPolicy.compute(
            List.of(participant()),
            SignerCharacter.PROPIETARIO,
            PersonType.FISICA,
            AccreditationType.ESCRITURA_PUBLICA,
            false,
            PropertyCaseType.HOUSING);

    assertThat(countRequired(specs, DocumentTypeCode.INE)).isEqualTo(1);
  }

  @Test
  void twoOwnersGenerateTwoIne() {
    List<ExpedienteParticipant> owners =
        List.of(
            new ExpedienteParticipant(UUID.randomUUID(), ParticipantRole.OWNER, "Propietario 1", 1),
            new ExpedienteParticipant(UUID.randomUUID(), ParticipantRole.CO_OWNER, "Propietario 2", 2));

    List<RequiredDocumentSpec> specs =
        DocumentRequirementPolicy.compute(
            owners,
            SignerCharacter.COPROPIETARIO,
            PersonType.FISICA,
            AccreditationType.ESCRITURA_PUBLICA,
            false,
            PropertyCaseType.HOUSING);

    assertThat(countRequired(specs, DocumentTypeCode.INE)).isEqualTo(2);
  }

  @Test
  void threeParticipantsGenerateThreeIne() {
    List<ExpedienteParticipant> owners =
        List.of(
            new ExpedienteParticipant(UUID.randomUUID(), ParticipantRole.OWNER, "Propietario 1", 1),
            new ExpedienteParticipant(UUID.randomUUID(), ParticipantRole.CO_OWNER, "Propietario 2", 2),
            new ExpedienteParticipant(UUID.randomUUID(), ParticipantRole.CO_OWNER, "Propietario 3", 3));

    List<RequiredDocumentSpec> specs =
        DocumentRequirementPolicy.compute(
            owners,
            SignerCharacter.COPROPIETARIO,
            PersonType.FISICA,
            AccreditationType.ESCRITURA_PUBLICA,
            false,
            PropertyCaseType.HOUSING);

    assertThat(countRequired(specs, DocumentTypeCode.INE)).isEqualTo(3);
  }

  @Test
  void attorneyRequiresPowerOfAttorney() {
    List<RequiredDocumentSpec> specs =
        DocumentRequirementPolicy.compute(
            List.of(participant()),
            SignerCharacter.APODERADO,
            PersonType.FISICA,
            AccreditationType.ESCRITURA_PUBLICA,
            false,
            PropertyCaseType.HOUSING);

    assertThat(specs)
        .filteredOn(s -> s.type() == DocumentTypeCode.POWER_OF_ATTORNEY)
        .singleElement()
        .satisfies(s -> assertThat(s.required()).isTrue());
  }

  @Test
  void ownerActingOnOwnBehalfDoesNotRequirePowerOfAttorney() {
    List<RequiredDocumentSpec> specs =
        DocumentRequirementPolicy.compute(
            List.of(participant()),
            SignerCharacter.PROPIETARIO,
            PersonType.FISICA,
            AccreditationType.ESCRITURA_PUBLICA,
            false,
            PropertyCaseType.HOUSING);

    assertThat(specs)
        .filteredOn(s -> s.type() == DocumentTypeCode.POWER_OF_ATTORNEY)
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.required()).isFalse();
              assertThat(s.conditional()).isTrue();
            });
  }

  @Test
  void condominiumRegimeIsRequiredOnlyWhenApplicable() {
    List<RequiredDocumentSpec> withCondominium =
        DocumentRequirementPolicy.compute(
            List.of(participant()),
            SignerCharacter.PROPIETARIO,
            PersonType.FISICA,
            AccreditationType.ESCRITURA_PUBLICA,
            true,
            PropertyCaseType.HOUSING);
    List<RequiredDocumentSpec> withoutCondominium =
        DocumentRequirementPolicy.compute(
            List.of(participant()),
            SignerCharacter.PROPIETARIO,
            PersonType.FISICA,
            AccreditationType.ESCRITURA_PUBLICA,
            false,
            PropertyCaseType.HOUSING);

    assertThat(withCondominium)
        .filteredOn(s -> s.type() == DocumentTypeCode.CONDOMINIUM_REGIME)
        .singleElement()
        .satisfies(s -> assertThat(s.required()).isTrue());
    assertThat(withoutCondominium)
        .filteredOn(s -> s.type() == DocumentTypeCode.CONDOMINIUM_REGIME)
        .singleElement()
        .satisfies(s -> assertThat(s.required()).isFalse());
  }

  @Test
  void baseDocumentsAreAlwaysRequired() {
    List<RequiredDocumentSpec> specs =
        DocumentRequirementPolicy.compute(
            List.of(participant()),
            SignerCharacter.PROPIETARIO,
            PersonType.FISICA,
            AccreditationType.ESCRITURA_PUBLICA,
            false,
            PropertyCaseType.HOUSING);

    assertThat(countRequired(specs, DocumentTypeCode.TAX_STATUS_CERTIFICATE)).isEqualTo(1);
    assertThat(countRequired(specs, DocumentTypeCode.DEED)).isEqualTo(1);
    assertThat(countRequired(specs, DocumentTypeCode.PROOF_OF_ADDRESS)).isEqualTo(1);
    assertThat(countRequired(specs, DocumentTypeCode.LIEN_CERTIFICATE)).isEqualTo(1);
    assertThat(countRequired(specs, DocumentTypeCode.PROPERTY_TAX)).isEqualTo(1);
  }
}
