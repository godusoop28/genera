package com.c21genera.expedientes.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.c21genera.expedientes.CivilStatus;
import com.c21genera.expedientes.PersonType;
import com.c21genera.expedientes.SignerCharacter;
import com.c21genera.expedientes.application.ExpedienteService.ParticipantInput;
import com.c21genera.expedientes.domain.ExpedienteParticipant;
import com.c21genera.expedientes.domain.ParticipantRole;
import com.c21genera.shared.domain.UnprocessableException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ParticipantRulesTest {

  private static ParticipantInput p(ParticipantRole role, String name) {
    return new ParticipantInput(role, name, ExpedienteParticipant.Details.empty());
  }

  private static ParticipantInput companyWithCivilStatus() {
    return new ParticipantInput(
        ParticipantRole.OWNER,
        "Inmobiliaria SA",
        new ExpedienteParticipant.Details(null, null, null, null, null, CivilStatus.CASADO, null, null, null, null, null, null));
  }

  @Test
  void anyNumberOfCoOwnersIsAllowedAndTheyDefineTheSignerCharacter() {
    List<ParticipantInput> people = new ArrayList<>();
    people.add(p(ParticipantRole.OWNER, "Juan Pérez"));
    for (int i = 0; i < 7; i++) {
      people.add(p(ParticipantRole.CO_OWNER, "Copropietario " + i));
    }

    ExpedienteService.validateParticipants(PersonType.FISICA, false, people);

    assertThat(ExpedienteService.signerCharacterOf(PersonType.FISICA, false, people)).isEqualTo(SignerCharacter.COPROPIETARIO);
    assertThat(ExpedienteService.signerCharacterOf(PersonType.FISICA, false, people.subList(0, 1))).isEqualTo(SignerCharacter.PROPIETARIO);
    assertThat(ExpedienteService.displayNameOf(PersonType.FISICA, people.subList(0, 3))).isEqualTo("Juan Pérez, Copropietario 0 y Copropietario 1");
  }

  @Test
  void placeholderNamesAreRejected() {
    assertThatThrownBy(
            () ->
                ExpedienteService.validateParticipants(
                    PersonType.FISICA, false, List.of(p(ParticipantRole.OWNER, "Juan"), p(ParticipantRole.CO_OWNER, "Copropietario 2 (pendiente de captura)"))))
        .isInstanceOf(UnprocessableException.class);
  }

  @Test
  void legalEntityNeedsARepresentativeAndCannotHaveCivilStatusCoOwnersOrAttorneys() {
    assertThatThrownBy(() -> ExpedienteService.validateParticipants(PersonType.MORAL, false, List.of(p(ParticipantRole.OWNER, "Inmobiliaria SA"))))
        .hasMessageContaining("representante legal");
    assertThatThrownBy(
            () ->
                ExpedienteService.validateParticipants(
                    PersonType.MORAL, false, List.of(companyWithCivilStatus(), p(ParticipantRole.LEGAL_REPRESENTATIVE, "Rep"))))
        .hasMessageContaining("estado civil");
    assertThatThrownBy(
            () ->
                ExpedienteService.validateParticipants(
                    PersonType.MORAL,
                    false,
                    List.of(p(ParticipantRole.OWNER, "Inmobiliaria SA"), p(ParticipantRole.CO_OWNER, "Otro"), p(ParticipantRole.LEGAL_REPRESENTATIVE, "Rep"))))
        .isInstanceOf(UnprocessableException.class);

    List<ParticipantInput> ok = List.of(p(ParticipantRole.OWNER, "Inmobiliaria SA"), p(ParticipantRole.LEGAL_REPRESENTATIVE, "Rep"));
    ExpedienteService.validateParticipants(PersonType.MORAL, false, ok);
    assertThat(ExpedienteService.signerCharacterOf(PersonType.MORAL, false, ok)).isEqualTo(SignerCharacter.REPRESENTANTE_LEGAL);
  }

  @Test
  void naturalPersonCannotHaveALegalRepresentativeAndAttorneyMustBeRegistered() {
    assertThatThrownBy(
            () ->
                ExpedienteService.validateParticipants(
                    PersonType.FISICA, false, List.of(p(ParticipantRole.OWNER, "Juan"), p(ParticipantRole.LEGAL_REPRESENTATIVE, "Rep"))))
        .hasMessageContaining("apoderado");
    assertThatThrownBy(() -> ExpedienteService.validateParticipants(PersonType.FISICA, true, List.of(p(ParticipantRole.OWNER, "Juan"))))
        .hasMessageContaining("apoderado");

    List<ParticipantInput> withAttorney = List.of(p(ParticipantRole.OWNER, "Juan"), p(ParticipantRole.ATTORNEY, "Apoderado"));
    ExpedienteService.validateParticipants(PersonType.FISICA, true, withAttorney);
    assertThat(ExpedienteService.signerCharacterOf(PersonType.FISICA, true, withAttorney)).isEqualTo(SignerCharacter.APODERADO);
  }

  @Test
  void firstParticipantMustBeTheSingleMainOwner() {
    assertThatThrownBy(() -> ExpedienteService.validateParticipants(PersonType.FISICA, false, List.of(p(ParticipantRole.CO_OWNER, "Juan"))))
        .isInstanceOf(UnprocessableException.class);
    assertThatThrownBy(
            () -> ExpedienteService.validateParticipants(PersonType.FISICA, false, List.of(p(ParticipantRole.OWNER, "A"), p(ParticipantRole.OWNER, "B"))))
        .isInstanceOf(UnprocessableException.class);
  }
}
