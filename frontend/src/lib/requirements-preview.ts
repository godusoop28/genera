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
  /** Código del tipo de documento (DocumentTypeCode del backend). */
  type: string;
  label: string;
  /** De quién o de qué es (p. ej. el nombre del propietario, "Del inmueble"). */
  detail?: string;
  /** Condición o aclaración (p. ej. "Solo si está casado"). */
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
  const doc = (type: string, detail?: string, note?: string): PreviewItem => ({ type, label: documentTypeLabel(type), detail, note });

  input.participants.forEach((p, i) => {
    const name = p.fullName.trim() || `Participante ${i + 1}`;
    const isCompany = moral && p.role === "OWNER";
    const isOwner = p.role === "OWNER" || p.role === "CO_OWNER";
    if (!isCompany) items.push(doc("INE", name));
    if (isOwner) items.push(doc("TAX_STATUS_CERTIFICATE", name));
    // El estado civil lo declara el cliente en su liga: el acta solo se vuelve obligatoria si está casado.
    if (isOwner && !isCompany) items.push(doc("MARRIAGE_CERTIFICATE", name, p.civilStatus === "CASADO" ? undefined : "Solo si está casado; lo declara el cliente"));
  });

  if (moral) items.push(doc("INCORPORATION_DEED", "De la empresa"));
  items.push(doc(input.accreditationType === "ESCRITURA_PUBLICA" ? "DEED" : "PRIVATE_CONTRACT", "Del inmueble"));
  items.push(doc("CADASTRAL_PLAN", "Del inmueble"));
  items.push(doc("RPP_REGISTRATION_SLIP", "Del inmueble"));
  items.push(doc("PROPERTY_TAX", "Comprobante de pago vigente"));
  const terreno = input.propertyCaseType === "RESIDENTIAL_LAND";
  items.push(doc("ELECTRICITY_RECEIPT", "Del inmueble", terreno ? "Opcional en terreno" : undefined));
  items.push(doc("WATER_RECEIPT", "Del inmueble", terreno ? "Opcional en terreno" : undefined));
  if (input.signedByAttorney || moral) {
    items.push(doc("POWER_OF_ATTORNEY", moral ? "Facultades del representante legal" : "Del apoderado"));
  }
  if (input.condominiumRegime) items.push(doc("CONDOMINIUM_REGIME", "Del inmueble"));
  return items;
}
