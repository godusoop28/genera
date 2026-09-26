// Vista previa (solo informativa) de los documentos que pedirá el sistema al
// crear el expediente. La lista real la calcula el backend
// (DocumentRequirementPolicy.java) y es la que se usa; si cambias las reglas
// allá, actualiza también esta copia.
import type {
  BackendAccreditationType,
  BackendPersonType,
  BackendPropertyCaseType,
  ParticipantRequest,
} from "@/lib/api/types";
import { documentTypeLabel } from "@/lib/document-type-labels";

export interface PreviewItem {
  label: string;
  note?: string;
}

export function previewRequirements(input: {
  personType: BackendPersonType;
  signedByAttorney: boolean;
  accreditationType: BackendAccreditationType;
  condominiumRegime: boolean;
  propertyCaseType: BackendPropertyCaseType;
  participants: ParticipantRequest[];
}): PreviewItem[] {
  const items: PreviewItem[] = [];
  const moral = input.personType === "MORAL";

  input.participants.forEach((p, i) => {
    const name = p.fullName.trim() || `Participante ${i + 1}`;
    const isCompany = moral && p.role === "OWNER";
    const isOwner = p.role === "OWNER" || p.role === "CO_OWNER";
    if (!isCompany) items.push({ label: `${documentTypeLabel("INE")} — ${name}` });
    if (isOwner) items.push({ label: `${documentTypeLabel("TAX_STATUS_CERTIFICATE")} — ${name}` });
    if (isOwner && !isCompany && p.civilStatus === "CASADO") items.push({ label: `${documentTypeLabel("MARRIAGE_CERTIFICATE")} — ${name}` });
  });

  if (moral) items.push({ label: documentTypeLabel("INCORPORATION_DEED") });
  items.push({ label: documentTypeLabel(input.accreditationType === "ESCRITURA_PUBLICA" ? "DEED" : "PRIVATE_CONTRACT") });
  items.push({ label: documentTypeLabel("CADASTRAL_PLAN") });
  items.push({ label: documentTypeLabel("RPP_REGISTRATION_SLIP") });
  items.push({ label: documentTypeLabel("PROPERTY_TAX") });
  if (input.propertyCaseType !== "RESIDENTIAL_LAND") {
    items.push({ label: documentTypeLabel("ELECTRICITY_RECEIPT") });
    items.push({ label: documentTypeLabel("WATER_RECEIPT") });
  } else {
    items.push({ label: `${documentTypeLabel("ELECTRICITY_RECEIPT")} y ${documentTypeLabel("WATER_RECEIPT")}`, note: "Opcionales en terreno" });
  }
  if (input.signedByAttorney || moral) {
    items.push({ label: documentTypeLabel("POWER_OF_ATTORNEY"), note: moral ? "Facultades del representante legal" : undefined });
  }
  if (input.condominiumRegime) items.push({ label: documentTypeLabel("CONDOMINIUM_REGIME") });
  return items;
}
