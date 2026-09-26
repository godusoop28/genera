package com.c21genera.expedientes.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.c21genera.expedientes.AccreditationType;
import com.c21genera.expedientes.CivilStatus;
import com.c21genera.expedientes.PersonType;
import com.c21genera.expedientes.PropertyCaseType;
import com.c21genera.expedientes.SignerCharacter;
import com.c21genera.shared.domain.DocumentTypeCode;
import com.c21genera.shared.domain.RequiredDocumentSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentRequirementPolicyTest {

  private static final UUID EXPEDIENTE = UUID.randomUUID();

  private static ExpedienteParticipant person(ParticipantRole role, String name, int ordinal, CivilStatus civilStatus) {
    ExpedienteParticipant p = new ExpedienteParticipant(EXPEDIENTE, role, name, ordinal);
    p.declareCivilStatus(civilStatus, null);
    return p;
  }

  private static ExpedienteParticipant owner(CivilStatus civilStatus) {
    return person(ParticipantRole.OWNER, "Juan Pérez López", 1, civilStatus);
  }

  private static List<RequiredDocumentSpec> compute(
      List<ExpedienteParticipant> participants,
      SignerCharacter signer,
      PersonType personType,
      AccreditationType accreditation,
      boolean condominium,
      PropertyCaseType propertyType) {
    return DocumentRequirementPolicy.compute(
        new DocumentRequirementPolicy.Input(participants, signer, personType, accreditation, condominium, propertyType, null));
  }

  private static List<RequiredDocumentSpec> housing(List<ExpedienteParticipant> participants, SignerCharacter signer) {
    return compute(participants, signer, PersonType.FISICA, AccreditationType.ESCRITURA_PUBLICA, false, PropertyCaseType.HOUSING);
  }

  private static long required(List<RequiredDocumentSpec> specs, DocumentTypeCode type) {
    return specs.stream().filter(s -> s.type() == type && s.required()).count();
  }

  @Test
  void singleOwnerNeedsHisIdTaxCertificateAndThePropertyDocuments() {
    List<RequiredDocumentSpec> specs = housing(List.of(owner(CivilStatus.SOLTERO)), SignerCharacter.PROPIETARIO);

    assertThat(required(specs, DocumentTypeCode.INE)).isEqualTo(1);
    assertThat(required(specs, DocumentTypeCode.TAX_STATUS_CERTIFICATE)).isEqualTo(1);
    assertThat(required(specs, DocumentTypeCode.DEED)).isEqualTo(1);
    assertThat(required(specs, DocumentTypeCode.CADASTRAL_PLAN)).isEqualTo(1);
    assertThat(required(specs, DocumentTypeCode.RPP_REGISTRATION_SLIP)).isEqualTo(1);
    assertThat(required(specs, DocumentTypeCode.PROPERTY_TAX)).isEqualTo(1);
    assertThat(required(specs, DocumentTypeCode.ELECTRICITY_RECEIPT)).isEqualTo(1);
    assertThat(required(specs, DocumentTypeCode.WATER_RECEIPT)).isEqualTo(1);
    assertThat(required(specs, DocumentTypeCode.MARRIAGE_CERTIFICATE)).isZero();
    assertThat(required(specs, DocumentTypeCode.POWER_OF_ATTORNEY)).isZero();
    assertThat(required(specs, DocumentTypeCode.PRIVATE_CONTRACT)).isZero();
    assertThat(required(specs, DocumentTypeCode.INCORPORATION_DEED)).isZero();
  }

  @Test
  void thereIsNoLimitOnCoOwnersAndEachOneHasHisOwnDocuments() {
    List<ExpedienteParticipant> owners = new ArrayList<>();
    owners.add(owner(CivilStatus.SOLTERO));
    for (int i = 2; i <= 6; i++) {
      owners.add(person(ParticipantRole.CO_OWNER, "Copropietario " + i, i, i % 2 == 0 ? CivilStatus.CASADO : CivilStatus.SOLTERO));
    }

    List<RequiredDocumentSpec> specs = housing(owners, SignerCharacter.COPROPIETARIO);

    assertThat(required(specs, DocumentTypeCode.INE)).isEqualTo(6);
    assertThat(required(specs, DocumentTypeCode.TAX_STATUS_CERTIFICATE)).isEqualTo(6);
    // Copropietarios 2, 4 y 6 están casados: un acta de matrimonio por cada uno.
    assertThat(required(specs, DocumentTypeCode.MARRIAGE_CERTIFICATE)).isEqualTo(3);
    assertThat(specs.stream().filter(s -> s.type() == DocumentTypeCode.INE).map(RequiredDocumentSpec::participantId).distinct().count())
        .isEqualTo(6);
  }

  @Test
  void principalOwnerKeepsHistoricalRequirementCodes() {
    List<RequiredDocumentSpec> specs = housing(List.of(owner(CivilStatus.CASADO)), SignerCharacter.PROPIETARIO);

    assertThat(specs).anyMatch(s -> s.requirementCode().equals("fiscal") && s.required());
    assertThat(specs).anyMatch(s -> s.requirementCode().equals("acta-matrimonio") && s.required());
  }

  @Test
  void legacyCivilStatusOfTheExpedienteStillAppliesToThePrincipalOwner() {
    ExpedienteParticipant principal = new ExpedienteParticipant(EXPEDIENTE, ParticipantRole.OWNER, "Juan", 1);

    List<RequiredDocumentSpec> specs =
        DocumentRequirementPolicy.compute(
            new DocumentRequirementPolicy.Input(
                List.of(principal),
                SignerCharacter.PROPIETARIO,
                PersonType.FISICA,
                AccreditationType.ESCRITURA_PUBLICA,
                false,
                PropertyCaseType.HOUSING,
                CivilStatus.CASADO));

    assertThat(required(specs, DocumentTypeCode.MARRIAGE_CERTIFICATE)).isEqualTo(1);
  }

  @Test
  void landDoesNotRequireWaterNorElectricityReceipts() {
    List<RequiredDocumentSpec> specs =
        compute(
            List.of(owner(CivilStatus.SOLTERO)),
            SignerCharacter.PROPIETARIO,
            PersonType.FISICA,
            AccreditationType.ESCRITURA_PUBLICA,
            false,
            PropertyCaseType.RESIDENTIAL_LAND);

    assertThat(required(specs, DocumentTypeCode.ELECTRICITY_RECEIPT)).isZero();
    assertThat(required(specs, DocumentTypeCode.WATER_RECEIPT)).isZero();
    // Siguen existiendo como opcionales por si el terreno sí tiene servicios.
    assertThat(specs).anyMatch(s -> s.type() == DocumentTypeCode.WATER_RECEIPT && s.conditional());
  }

  @Test
  void privateContractReplacesTheDeed() {
    List<RequiredDocumentSpec> specs =
        compute(
            List.of(owner(CivilStatus.SOLTERO)),
            SignerCharacter.PROPIETARIO,
            PersonType.FISICA,
            AccreditationType.CONTRATO_PRIVADO,
            false,
            PropertyCaseType.HOUSING);

    assertThat(required(specs, DocumentTypeCode.DEED)).isZero();
    assertThat(required(specs, DocumentTypeCode.PRIVATE_CONTRACT)).isEqualTo(1);
  }

  @Test
  void attorneyNeedsHisOwnIdAndThePowerOfAttorney() {
    List<RequiredDocumentSpec> specs =
        housing(
            List.of(owner(CivilStatus.SOLTERO), person(ParticipantRole.ATTORNEY, "Apoderado", 2, null)),
            SignerCharacter.APODERADO);

    assertThat(required(specs, DocumentTypeCode.INE)).isEqualTo(2);
    assertThat(required(specs, DocumentTypeCode.POWER_OF_ATTORNEY)).isEqualTo(1);
    // El apoderado no es titular: no se le pide constancia fiscal ni acta de matrimonio.
    assertThat(required(specs, DocumentTypeCode.TAX_STATUS_CERTIFICATE)).isEqualTo(1);
  }

  @Test
  void legalEntityNeedsIncorporationDeedAndItsRepresentativeIdButNoMarriageCertificate() {
    ExpedienteParticipant company = new ExpedienteParticipant(EXPEDIENTE, ParticipantRole.OWNER, "Inmobiliaria SA de CV", 1);
    ExpedienteParticipant representative = person(ParticipantRole.LEGAL_REPRESENTATIVE, "Representante", 2, null);

    List<RequiredDocumentSpec> specs =
        compute(
            List.of(company, representative),
            SignerCharacter.REPRESENTANTE_LEGAL,
            PersonType.MORAL,
            AccreditationType.ESCRITURA_PUBLICA,
            false,
            PropertyCaseType.HOUSING);

    assertThat(required(specs, DocumentTypeCode.INCORPORATION_DEED)).isEqualTo(1);
    assertThat(required(specs, DocumentTypeCode.POWER_OF_ATTORNEY)).isEqualTo(1);
    // INE solo del representante (una sociedad no tiene identificación personal).
    assertThat(specs.stream().filter(s -> s.type() == DocumentTypeCode.INE && s.required()).map(RequiredDocumentSpec::participantId))
        .containsExactly(representative.getId());
    assertThat(specs).noneMatch(s -> s.type() == DocumentTypeCode.MARRIAGE_CERTIFICATE);
    assertThat(required(specs, DocumentTypeCode.TAX_STATUS_CERTIFICATE)).isEqualTo(1);
  }

  @Test
  void condominiumRegimeRequiresItsDeed() {
    List<RequiredDocumentSpec> specs =
        compute(
            List.of(owner(CivilStatus.SOLTERO)),
            SignerCharacter.PROPIETARIO,
            PersonType.FISICA,
            AccreditationType.ESCRITURA_PUBLICA,
            true,
            PropertyCaseType.DEPARTMENT);

    assertThat(required(specs, DocumentTypeCode.CONDOMINIUM_REGIME)).isEqualTo(1);
  }

  @Test
  void requirementCodesAreUnique() {
    List<RequiredDocumentSpec> specs =
        housing(
            List.of(owner(CivilStatus.CASADO), person(ParticipantRole.CO_OWNER, "Co", 2, CivilStatus.CASADO)),
            SignerCharacter.COPROPIETARIO);

    assertThat(specs.stream().map(RequiredDocumentSpec::requirementCode).distinct().count()).isEqualTo(specs.size());
  }
}
