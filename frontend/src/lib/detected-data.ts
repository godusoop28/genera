import type { ExpedienteObservationResponse, LegalDetails, ParticipantDetails } from "@/lib/api/types";
import { documentTypeLabel } from "@/lib/document-type-labels";

/** Un valor que la revisión automática leyó en un documento del expediente. */
export interface Detected {
  value: string;
  /** Documento del que salió, p. ej. "Escritura". */
  source: string;
  /** Ya lo confirmó una persona al revisar el documento. */
  confirmed: boolean;
}

type LegalSection = "deed" | "privateContract" | "condominium" | "company" | "representation";
export type LegalKey = `${LegalSection}.${string}`;

export interface DetectedData {
  legal: Partial<Record<LegalKey, Detected>>;
  landAreaM2?: Detected;
  builtAreaM2?: Detected;
  participants: Record<string, Partial<Record<keyof ParticipantDetails, Detected>>>;
}

const MONTHS = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"];

/** AAAA-MM-DD, DD/MM/AAAA o "15 de marzo de 2010" → AAAA-MM-DD; null si no se reconoce. */
export function normalizeDate(raw: string): string | null {
  const text = raw.trim().toLowerCase();
  const pad = (n: number) => String(n).padStart(2, "0");
  const valid = (y: number, m: number, d: number) => y > 1800 && y < 2200 && m >= 1 && m <= 12 && d >= 1 && d <= 31;
  let match = text.match(/^(\d{4})-(\d{1,2})-(\d{1,2})/);
  if (match) {
    const [y, m, d] = [Number(match[1]), Number(match[2]), Number(match[3])];
    return valid(y, m, d) ? `${y}-${pad(m)}-${pad(d)}` : null;
  }
  match = text.match(/^(\d{1,2})[/.-](\d{1,2})[/.-](\d{4})$/);
  if (match) {
    const [d, m, y] = [Number(match[1]), Number(match[2]), Number(match[3])];
    return valid(y, m, d) ? `${y}-${pad(m)}-${pad(d)}` : null;
  }
  match = text.match(/(\d{1,2})\s+de\s+([a-záéíóú]+)\s+(?:de|del)\s+(\d{4})/);
  if (match) {
    const m = MONTHS.indexOf(match[2].normalize("NFD").replace(/\p{M}/gu, "")) + 1;
    const [d, y] = [Number(match[1]), Number(match[3])];
    return valid(y, m, d) ? `${y}-${pad(m)}-${pad(d)}` : null;
  }
  return null;
}

/** "1,250.50 m2" → "1250.50"; null si no hay número. */
export function normalizeArea(raw: string): string | null {
  const match = raw.replace(/,(?=\d{3}\b)/g, "").match(/\d+(?:\.\d+)?/);
  return match ? match[0] : null;
}

type Target =
  | { kind: "legal"; key: LegalKey; date?: boolean }
  | { kind: "area"; key: "landAreaM2" | "builtAreaM2" }
  | { kind: "participant"; key: keyof ParticipantDetails; date?: boolean };

/** Qué dato del contrato alimenta cada campo de cada tipo de documento. */
const MAPPING: Record<string, Record<string, Target>> = {
  DEED: {
    deedNumber: { kind: "legal", key: "deed.number" },
    deedDate: { kind: "legal", key: "deed.date", date: true },
    notaryName: { kind: "legal", key: "deed.notaryName" },
    notaryNumber: { kind: "legal", key: "deed.notaryNumber" },
    notaryPlace: { kind: "legal", key: "deed.notaryPlace" },
    publicRegistryFolio: { kind: "legal", key: "deed.registryData" },
    landArea: { kind: "area", key: "landAreaM2" },
    builtArea: { kind: "area", key: "builtAreaM2" },
  },
  PRIVATE_CONTRACT: {
    sellerFullName: { kind: "legal", key: "privateContract.sellerName" },
    buyerFullName: { kind: "legal", key: "privateContract.buyerName" },
    contractDate: { kind: "legal", key: "privateContract.date", date: true },
    ratificationDate: { kind: "legal", key: "privateContract.ratificationDate", date: true },
    notaryName: { kind: "legal", key: "privateContract.notaryName" },
    notaryNumber: { kind: "legal", key: "privateContract.notaryNumber" },
    notaryPlace: { kind: "legal", key: "privateContract.notaryPlace" },
    registryDate: { kind: "legal", key: "privateContract.registryDate", date: true },
    publicRegistryFolio: { kind: "legal", key: "privateContract.realFolio" },
    landArea: { kind: "area", key: "landAreaM2" },
  },
  CONDOMINIUM_REGIME: {
    deedNumber: { kind: "legal", key: "condominium.deedNumber" },
    deedDate: { kind: "legal", key: "condominium.date", date: true },
    notaryName: { kind: "legal", key: "condominium.notaryName" },
    notaryNumber: { kind: "legal", key: "condominium.notaryNumber" },
    notaryPlace: { kind: "legal", key: "condominium.notaryPlace" },
    registryDate: { kind: "legal", key: "condominium.registryDate", date: true },
    regimeRegistrationFolio: { kind: "legal", key: "condominium.realFolio" },
  },
  INCORPORATION_DEED: {
    companyType: { kind: "legal", key: "company.companyType" },
    instrumentNumber: { kind: "legal", key: "company.instrumentNumber" },
    instrumentDate: { kind: "legal", key: "company.instrumentDate", date: true },
    notaryTitle: { kind: "legal", key: "company.notaryTitle" },
    notaryName: { kind: "legal", key: "company.notaryName" },
    notaryNumber: { kind: "legal", key: "company.notaryNumber" },
    notaryPlace: { kind: "legal", key: "company.notaryPlace" },
    commerceRegistryPlace: { kind: "legal", key: "company.commerceRegistryPlace" },
    mercantileFolio: { kind: "legal", key: "company.mercantileFolio" },
  },
  POWER_OF_ATTORNEY: {
    instrumentNumber: { kind: "legal", key: "representation.instrumentNumber" },
    instrumentDate: { kind: "legal", key: "representation.instrumentDate", date: true },
    notaryName: { kind: "legal", key: "representation.notaryName" },
    notaryNumber: { kind: "legal", key: "representation.notaryNumber" },
    notaryPlace: { kind: "legal", key: "representation.notaryPlace" },
    publicRegistryFolio: { kind: "legal", key: "representation.registryFolio" },
  },
  PROPERTY_TAX: {
    landArea: { kind: "area", key: "landAreaM2" },
    builtArea: { kind: "area", key: "builtAreaM2" },
  },
  CADASTRAL_PLAN: {
    landArea: { kind: "area", key: "landAreaM2" },
    builtArea: { kind: "area", key: "builtAreaM2" },
  },
  INE: {
    electorKey: { kind: "participant", key: "idDocumentNumber" },
    curp: { kind: "participant", key: "curp" },
    birthDate: { kind: "participant", key: "birthDate", date: true },
    address: { kind: "participant", key: "address" },
  },
  PASSPORT: {
    passportNumber: { kind: "participant", key: "idDocumentNumber" },
    nationality: { kind: "participant", key: "nationality" },
    birthDate: { kind: "participant", key: "birthDate", date: true },
    curp: { kind: "participant", key: "curp" },
  },
  CURP: {
    curp: { kind: "participant", key: "curp" },
    birthDate: { kind: "participant", key: "birthDate", date: true },
  },
  TAX_STATUS_CERTIFICATE: {
    rfc: { kind: "participant", key: "rfc" },
    curp: { kind: "participant", key: "curp" },
    address: { kind: "participant", key: "address" },
  },
  PROOF_OF_ADDRESS: {
    address: { kind: "participant", key: "address" },
  },
};

/** Si dos documentos traen el mismo dato, gana el de mayor rango (la escritura sobre el predial, etc.). */
const PRIORITY = [
  "DEED",
  "PRIVATE_CONTRACT",
  "CONDOMINIUM_REGIME",
  "INCORPORATION_DEED",
  "POWER_OF_ATTORNEY",
  "INE",
  "PASSPORT",
  "TAX_STATUS_CERTIFICATE",
  "CURP",
  "PROPERTY_TAX",
  "CADASTRAL_PLAN",
  "PROOF_OF_ADDRESS",
];

/** Tipo de identificación y emisor que implica el documento del que sale el número. */
const ID_DOCUMENT: Record<string, { type: ParticipantDetails["idDocumentType"]; issuer: string }> = {
  INE: { type: "INE", issuer: "Instituto Nacional Electoral" },
  PASSPORT: { type: "PASAPORTE", issuer: "Secretaría de Relaciones Exteriores" },
};

export function buildDetectedData(observations: ExpedienteObservationResponse[], ownerParticipantId: string | null): DetectedData {
  const result: DetectedData = { legal: {}, participants: {} };
  const sorted = [...observations].sort((a, b) => {
    const rank = (t: string) => (PRIORITY.indexOf(t) === -1 ? PRIORITY.length : PRIORITY.indexOf(t));
    // Lo confirmado por una persona va primero; después, el documento de mayor rango.
    return Number(b.confirmed) - Number(a.confirmed) || rank(a.documentType) - rank(b.documentType);
  });

  for (const o of sorted) {
    const target = MAPPING[o.documentType]?.[o.fieldName];
    if (!target || !o.value?.trim()) continue;
    let value = o.value.trim();
    if ("date" in target && target.date) {
      const date = normalizeDate(value);
      if (!date) continue;
      value = date;
    }
    if (target.kind === "area") {
      const area = normalizeArea(value);
      if (!area) continue;
      value = area;
    }
    const detected: Detected = { value, source: documentTypeLabel(o.documentType), confirmed: o.confirmed };

    if (target.kind === "legal") {
      result.legal[target.key] ??= detected;
    } else if (target.kind === "area") {
      result[target.key] ??= detected;
    } else {
      // Documentos sin titular propio (p. ej. la constancia fiscal de la empresa) son del propietario.
      const participantId = o.participantId ?? ownerParticipantId;
      if (!participantId) continue;
      const bucket = (result.participants[participantId] ??= {});
      if (bucket[target.key]) continue;
      (bucket as Record<string, Detected>)[target.key] = detected;
      const id = ID_DOCUMENT[o.documentType];
      if (target.key === "idDocumentNumber" && id) {
        bucket.idDocumentType ??= { value: id.type ?? "", source: detected.source, confirmed: detected.confirmed };
        bucket.idDocumentIssuer ??= { value: id.issuer, source: detected.source, confirmed: detected.confirmed };
      }
    }
  }
  return result;
}

export function legalValue(legal: LegalDetails, key: LegalKey): string {
  const [section, field] = key.split(".") as [LegalSection, string];
  const value = (legal[section] as Record<string, unknown> | null | undefined)?.[field];
  return value === null || value === undefined ? "" : String(value);
}
