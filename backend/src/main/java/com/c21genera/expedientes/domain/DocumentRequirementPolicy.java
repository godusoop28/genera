package com.c21genera.expedientes.domain;

import com.c21genera.expedientes.AccreditationType;
import com.c21genera.expedientes.CivilStatus;
import com.c21genera.expedientes.PersonType;
import com.c21genera.expedientes.PropertyCaseType;
import com.c21genera.expedientes.SignerCharacter;
import com.c21genera.shared.domain.DocumentTypeCode;
import com.c21genera.shared.domain.RequiredDocumentSpec;
import java.util.ArrayList;
import java.util.List;

/**
 * Fuente de verdad de qué documentos requiere un expediente (ver AGENTS
 * §21). El frontend NUNCA decide esto: solo refleja lo que el backend
 * calcula aquí.
 *
 * <p>Las reglas dependen de:
 * <ul>
 *   <li>Cada participante, individualmente: identificación de cada persona
 *       física que comparece; constancia fiscal y, si está casado, acta de
 *       matrimonio de cada propietario/copropietario.</li>
 *   <li>Tipo de persona: una persona moral no tiene INE ni estado civil;
 *       en su lugar se piden acta constitutiva, constancia fiscal de la
 *       sociedad y los poderes de su representante legal.</li>
 *   <li>Cómo se acredita la propiedad: escritura pública o contrato privado
 *       ratificado (nunca ambos).</li>
 *   <li>Tipo de inmueble: un terreno normalmente no tiene recibos de luz ni
 *       agua, así que dejan de ser obligatorios (se pueden cargar si existen).</li>
 *   <li>Régimen de condominio.</li>
 * </ul>
 * Cualquier documento obligatorio puede además marcarse como "No aplica"
 * por el staff, con justificación (ver documents).
 *
 * <p>Compatibilidad: el propietario principal (ordinal 1) conserva los
 * códigos de requisito históricos ("fiscal", "acta-matrimonio") para no
 * duplicar documentos ya cargados en expedientes existentes.
 */
public final class DocumentRequirementPolicy {

  private DocumentRequirementPolicy() {}

  public record Input(
      List<ExpedienteParticipant> participants,
      SignerCharacter signerCharacter,
      PersonType personType,
      AccreditationType accreditationType,
      boolean condominiumRegime,
      PropertyCaseType propertyCaseType,
      CivilStatus legacyOwnerCivilStatus) {}

  public static List<RequiredDocumentSpec> compute(Input input) {
    List<RequiredDocumentSpec> specs = new ArrayList<>();
    boolean moral = input.personType() == PersonType.MORAL;

    for (ExpedienteParticipant participant : input.participants()) {
      boolean principal = participant.getOrdinal() == 1;
      boolean isCompany = moral && participant.isOwner();

      if (!isCompany) {
        // Identificación de toda persona física que comparece (titulares,
        // representante legal o apoderado).
        specs.add(
            new RequiredDocumentSpec("ine-" + participant.getId(), DocumentTypeCode.INE, true, false, participant.getId()));
      }

      if (participant.isOwner()) {
        specs.add(
            new RequiredDocumentSpec(
                principal ? "fiscal" : "fiscal-" + participant.getId(),
                DocumentTypeCode.TAX_STATUS_CERTIFICATE,
                true,
                false,
                participant.getId()));
      }

      if (participant.isOwner() && !isCompany) {
        CivilStatus civilStatus = participant.getCivilStatus();
        if (civilStatus == null && principal) {
          civilStatus = input.legacyOwnerCivilStatus();
        }
        specs.add(
            new RequiredDocumentSpec(
                principal ? "acta-matrimonio" : "acta-matrimonio-" + participant.getId(),
                DocumentTypeCode.MARRIAGE_CERTIFICATE,
                civilStatus == CivilStatus.CASADO,
                true,
                participant.getId()));
      }
    }

    specs.add(new RequiredDocumentSpec("acta-constitutiva", DocumentTypeCode.INCORPORATION_DEED, moral, true, null));

    // Acreditación de la propiedad: escritura O contrato privado, nunca ambos.
    boolean deed = input.accreditationType() == AccreditationType.ESCRITURA_PUBLICA;
    specs.add(new RequiredDocumentSpec("escritura", DocumentTypeCode.DEED, deed, true, null));
    specs.add(new RequiredDocumentSpec("contrato-privado", DocumentTypeCode.PRIVATE_CONTRACT, !deed, true, null));

    specs.add(new RequiredDocumentSpec("plano-catastral", DocumentTypeCode.CADASTRAL_PLAN, true, false, null));
    specs.add(new RequiredDocumentSpec("boleta-rpp", DocumentTypeCode.RPP_REGISTRATION_SLIP, true, false, null));
    specs.add(new RequiredDocumentSpec("predial", DocumentTypeCode.PROPERTY_TAX, true, false, null));

    // Un terreno normalmente no tiene contratos de luz ni agua.
    boolean hasServices = input.propertyCaseType() != PropertyCaseType.RESIDENTIAL_LAND;
    specs.add(new RequiredDocumentSpec("recibo-cfe", DocumentTypeCode.ELECTRICITY_RECEIPT, hasServices, true, null));
    specs.add(new RequiredDocumentSpec("recibo-agua", DocumentTypeCode.WATER_RECEIPT, hasServices, true, null));

    // Poder notarial: apoderado de persona física, o facultades del
    // representante legal de una persona moral.
    boolean requiresPower = input.signerCharacter() == SignerCharacter.APODERADO || moral;
    specs.add(new RequiredDocumentSpec("poder", DocumentTypeCode.POWER_OF_ATTORNEY, requiresPower, true, null));

    specs.add(
        new RequiredDocumentSpec("condominio", DocumentTypeCode.CONDOMINIUM_REGIME, input.condominiumRegime(), true, null));

    return List.copyOf(specs);
  }
}
