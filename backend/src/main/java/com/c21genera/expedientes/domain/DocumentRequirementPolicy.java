package com.c21genera.expedientes.domain;

import com.c21genera.expedientes.RequiredDocumentSpec;
import com.c21genera.shared.domain.DocumentTypeCode;
import java.util.ArrayList;
import java.util.List;

/**
 * Fuente de verdad de qué documentos requiere un expediente (ver AGENTS
 * §21). El frontend NUNCA decide esto: solo refleja lo que el backend
 * calcula aquí.
 */
public final class DocumentRequirementPolicy {

  private DocumentRequirementPolicy() {}

  public static List<RequiredDocumentSpec> compute(
      List<ExpedienteParticipant> participants,
      SignerCharacter signerCharacter,
      PersonType personType,
      AccreditationType accreditationType,
      boolean condominiumRegime,
      PropertyCaseType propertyCaseType) {

    List<RequiredDocumentSpec> specs = new ArrayList<>();

    // Un INE por cada propietario/copropietario del expediente.
    for (ExpedienteParticipant participant : participants) {
      specs.add(
          new RequiredDocumentSpec(
              "ine-" + participant.getId(), DocumentTypeCode.INE, true, false, participant.getId()));
    }

    specs.add(new RequiredDocumentSpec("fiscal", DocumentTypeCode.TAX_STATUS_CERTIFICATE, true, false, null));
    specs.add(new RequiredDocumentSpec("escritura", DocumentTypeCode.DEED, true, false, null));
    specs.add(new RequiredDocumentSpec("domicilio", DocumentTypeCode.PROOF_OF_ADDRESS, true, false, null));
    specs.add(new RequiredDocumentSpec("gravamen", DocumentTypeCode.LIEN_CERTIFICATE, true, false, null));
    specs.add(new RequiredDocumentSpec("predial", DocumentTypeCode.PROPERTY_TAX, true, false, null));

    // Condicionales.
    boolean requiresPower = signerCharacter == SignerCharacter.APODERADO;
    specs.add(new RequiredDocumentSpec("poder", DocumentTypeCode.POWER_OF_ATTORNEY, requiresPower, true, null));

    specs.add(
        new RequiredDocumentSpec("condominio", DocumentTypeCode.CONDOMINIUM_REGIME, condominiumRegime, true, null));

    return List.copyOf(specs);
  }
}
