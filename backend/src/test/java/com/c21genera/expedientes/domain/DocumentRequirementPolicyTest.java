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

  private static List<RequiredDocumentSpec> compute(
      List<ExpedienteParticipant> participants,
      SignerCharacter signerCharacter,
      boolean condominiumRegime,
      CivilStatus civilStatus) {
    return DocumentRequirementPolicy.compute(
        participants,
        signerCharacter,
        PersonType.FISICA,
        AccreditationType.ESCRITURA_PUBLICA,
        condominiumRegime,
        PropertyCaseType.HOUSING,
        civilStatus);
  }

  @Test
  void oneOwnerGeneratesOneIne() {
    List<RequiredDocumentSpec> specs = compute(List.of(participant()), SignerCharacter.PROPIETARIO, false, null);

    assertThat(countRequired(specs, DocumentTypeCode.INE)).isEqualTo(1);
  }

  @Test
  void twoOwnersGenerateTwoIne() {
    List<ExpedienteParticipant> owners =
        List.of(
            new ExpedienteParticipant(UUID.randomUUID(), ParticipantRole.OWNER, "Propietario 1", 1),
            new ExpedienteParticipant(UUID.randomUUID(), ParticipantRole.CO_OWNER, "Propietario 2", 2));

    List<RequiredDocumentSpec> specs = compute(owners, SignerCharacter.COPROPIETARIO, false, null);

    assertThat(countRequired(specs, DocumentTypeCode.INE)).isEqualTo(2);
  }

  @Test
  void threeParticipantsGenerateThreeIne() {
    List<ExpedienteParticipant> owners =
        List.of(
            new ExpedienteParticipant(UUID.randomUUID(), ParticipantRole.OWNER, "Propietario 1", 1),
            new ExpedienteParticipant(UUID.randomUUID(), ParticipantRole.CO_OWNER, "Propietario 2", 2),
            new ExpedienteParticipant(UUID.randomUUID(), ParticipantRole.CO_OWNER, "Propietario 3", 3));

    List<RequiredDocumentSpec> specs = compute(owners, SignerCharacter.COPROPIETARIO, false, null);

    assertThat(countRequired(specs, DocumentTypeCode.INE)).isEqualTo(3);
  }

  @Test
  void attorneyRequiresPowerOfAttorney() {
    List<RequiredDocumentSpec> specs = compute(List.of(participant()), SignerCharacter.APODERADO, false, null);

    assertThat(specs)
        .filteredOn(s -> s.type() == DocumentTypeCode.POWER_OF_ATTORNEY)
        .singleElement()
        .satisfies(s -> assertThat(s.required()).isTrue());
  }

  @Test
  void ownerActingOnOwnBehalfDoesNotRequirePowerOfAttorney() {
    List<RequiredDocumentSpec> specs = compute(List.of(participant()), SignerCharacter.PROPIETARIO, false, null);

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
    List<RequiredDocumentSpec> withCondominium = compute(List.of(participant()), SignerCharacter.PROPIETARIO, true, null);
    List<RequiredDocumentSpec> withoutCondominium = compute(List.of(participant()), SignerCharacter.PROPIETARIO, false, null);

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
  void marriageCertificateIsRequiredOnlyWhenMarried() {
    List<RequiredDocumentSpec> married = compute(List.of(participant()), SignerCharacter.PROPIETARIO, false, CivilStatus.CASADO);
    List<RequiredDocumentSpec> single = compute(List.of(participant()), SignerCharacter.PROPIETARIO, false, CivilStatus.SOLTERO);
    List<RequiredDocumentSpec> unknown = compute(List.of(participant()), SignerCharacter.PROPIETARIO, false, null);

    assertThat(married)
        .filteredOn(s -> s.type() == DocumentTypeCode.MARRIAGE_CERTIFICATE)
        .singleElement()
        .satisfies(s -> assertThat(s.required()).isTrue());
    assertThat(single)
        .filteredOn(s -> s.type() == DocumentTypeCode.MARRIAGE_CERTIFICATE)
        .singleElement()
        .satisfies(s -> assertThat(s.required()).isFalse());
    assertThat(unknown)
        .filteredOn(s -> s.type() == DocumentTypeCode.MARRIAGE_CERTIFICATE)
        .singleElement()
        .satisfies(s -> assertThat(s.required()).isFalse());
  }

  @Test
  void baseDocumentsAreAlwaysRequired() {
    List<RequiredDocumentSpec> specs = compute(List.of(participant()), SignerCharacter.PROPIETARIO, false, null);

    assertThat(countRequired(specs, DocumentTypeCode.TAX_STATUS_CERTIFICATE)).isEqualTo(1);
    assertThat(countRequired(specs, DocumentTypeCode.DEED)).isEqualTo(1);
    assertThat(countRequired(specs, DocumentTypeCode.CADASTRAL_PLAN)).isEqualTo(1);
    assertThat(countRequired(specs, DocumentTypeCode.RPP_REGISTRATION_SLIP)).isEqualTo(1);
    assertThat(countRequired(specs, DocumentTypeCode.ELECTRICITY_RECEIPT)).isEqualTo(1);
    assertThat(countRequired(specs, DocumentTypeCode.WATER_RECEIPT)).isEqualTo(1);
    assertThat(countRequired(specs, DocumentTypeCode.PROPERTY_TAX)).isEqualTo(1);
  }

  @Test
  void libertyOfLienCertificateAndSingleProofOfAddressAreNoLongerRequired() {
    List<RequiredDocumentSpec> specs = compute(List.of(participant()), SignerCharacter.PROPIETARIO, false, null);

    assertThat(specs).noneMatch(s -> s.type() == DocumentTypeCode.LIEN_CERTIFICATE);
    assertThat(specs).noneMatch(s -> s.type() == DocumentTypeCode.PROOF_OF_ADDRESS);
  }
}
