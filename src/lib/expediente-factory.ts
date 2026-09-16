import { buildDocumentRequirements, buildOwners } from "@/data/document-requirements";
import type {
  ActivityItem,
  ActivityType,
  Expediente,
  ExpedienteConfig,
  ExtractedField,
} from "@/types/expediente";

export const defaultExpedienteConfig: ExpedienteConfig = {
  contractType: "intermediacion_exclusiva",
  ownerCount: 1,
  personType: "fisica",
  signerCharacter: "propietario",
  accreditation: "escritura_publica",
  condominiumRegime: false,
  propertyType: "vivienda",
  legalStatus: "en_revision",
};

let idCounter = 0;
export function generateId(prefix: string): string {
  idCounter += 1;
  return `${prefix}-${Date.now().toString(36)}-${idCounter}`;
}

export function makeActivity(type: ActivityType, message: string, at?: string): ActivityItem {
  return { id: generateId("act"), type, message, at: at ?? new Date().toISOString() };
}

interface CreateExpedienteInput {
  folio: string;
  ownerName: string;
  config?: Partial<ExpedienteConfig>;
  propertyAddress?: string;
}

export function createExpediente({
  folio,
  ownerName,
  config,
  propertyAddress,
}: CreateExpedienteInput): Expediente {
  const finalConfig: ExpedienteConfig = { ...defaultExpedienteConfig, ...config };
  const owners = buildOwners(finalConfig.ownerCount);
  const now = new Date().toISOString();

  return {
    id: generateId("exp"),
    folio,
    ownerName,
    ownerCount: finalConfig.ownerCount,
    status: "waiting_privacy",
    createdAt: now,
    updatedAt: now,
    config: finalConfig,
    owners,
    propertyAddress,
    documentRequirements: buildDocumentRequirements(finalConfig, owners),
    documents: {},
    privacyConsent: { mainConsent: false, secondaryConsent: false },
    manualData: {},
    extractedFields: [],
    contract: {
      profecoNumber: "7/002193-2026",
      profecoDate: "19 de marzo de 2026",
      intermediaryLegalName: "Grupo WILGEN y Asociados S. de R.L. de C.V.",
      intermediaryCommercialName: "CENTURY 21 GENERA",
      legalRepresentative: "Jorge Ricardo Jurado Espinal",
    },
    activity: [makeActivity("expediente_creado", `Expediente ${folio} creado.`, now)],
    propertyDecision: { decision: "pending" },
    emails: [],
    closing: { status: "pending", documents: [], notes: "", contractSigned: false, copyDelivered: false },
    additionalServices: [],
    linkStatus: "active",
    linkId: generateId("liga"),
  };
}

export function defaultExtractedFieldsFor(exp: Expediente): ExtractedField[] {
  const fields: ExtractedField[] = [
    {
      id: "nombre",
      label: "Nombre completo del cliente",
      value: exp.ownerName,
      source: "INE",
      confidence: "alta",
      origin: "extraido",
    },
    {
      id: "caracter",
      label: "Carácter",
      value:
        exp.config.signerCharacter === "propietario"
          ? "Propietario"
          : exp.config.signerCharacter === "copropietario"
            ? "Copropietario"
            : "Apoderado",
      source: "Poder notarial",
      confidence: "alta",
      origin: exp.config.signerCharacter === "apoderado" ? "extraido" : "no_aplica",
      notApplicable: exp.config.signerCharacter !== "apoderado",
    },
    { id: "nacionalidad", label: "Nacionalidad", value: "Mexicana", source: "INE", confidence: "alta", origin: "extraido" },
    { id: "tipo-id", label: "Tipo de identificación", value: "INE / Credencial para votar", source: "INE", confidence: "alta", origin: "extraido" },
    { id: "folio-id", label: "Número de folio o identificación", value: "", source: "INE", confidence: "revisar", origin: "extraido" },
    { id: "fecha-nacimiento", label: "Fecha de nacimiento", value: "", source: "INE", confidence: "media", origin: "extraido" },
    { id: "edad", label: "Edad calculada", value: "", source: "Calculado", origin: "calculado" },
    { id: "domicilio-cliente", label: "Domicilio del cliente", value: "", source: "INE", confidence: "media", origin: "extraido" },
    { id: "rfc", label: "RFC", value: "", source: "Constancia fiscal", confidence: "alta", origin: "extraido" },
    { id: "domicilio-inmueble", label: "Domicilio del inmueble", value: exp.propertyAddress ?? "", source: "Escritura", confidence: "alta", origin: "extraido" },
    { id: "num-escritura", label: "Número de escritura", value: "", source: "Escritura", confidence: "alta", origin: "extraido" },
    { id: "fecha-escritura", label: "Fecha de escritura", value: "", source: "Escritura", confidence: "alta", origin: "extraido" },
    { id: "notario", label: "Nombre del notario", value: "", source: "Escritura", confidence: "media", origin: "extraido" },
    { id: "num-notaria", label: "Número de notaría", value: "", source: "Escritura", confidence: "media", origin: "extraido" },
    { id: "demarcacion", label: "Demarcación", value: "", source: "Escritura", confidence: "media", origin: "extraido" },
    { id: "estado", label: "Estado", value: "", source: "Escritura", confidence: "alta", origin: "extraido" },
    { id: "folio-real", label: "Folio real / datos registrales", value: "", source: "Certificado de gravamen", confidence: "media", origin: "extraido" },
    {
      id: "tipo-inmueble",
      label: "Tipo de inmueble",
      value: exp.config.propertyType === "vivienda" ? "Vivienda destinada a casa habitación" : "Terreno destinado a casa habitación",
      source: "Escritura",
      confidence: "alta",
      origin: "extraido",
    },
    {
      id: "regimen-condominio",
      label: "Régimen de condominio",
      value: exp.config.condominiumRegime ? "Sí, sujeto a régimen de condominio" : "",
      source: "Régimen de condominio",
      origin: exp.config.condominiumRegime ? "extraido" : "no_aplica",
      notApplicable: !exp.config.condominiumRegime,
    },
    {
      id: "porcentaje-indiviso",
      label: "Porcentaje de indiviso",
      value: "",
      source: "Régimen de condominio",
      origin: exp.config.condominiumRegime ? "extraido" : "no_aplica",
      notApplicable: !exp.config.condominiumRegime,
    },
    { id: "superficie-terreno", label: "Superficie de terreno", value: "", source: "Escritura", confidence: "media", origin: "extraido" },
    {
      id: "superficie-construccion",
      label: "Superficie de construcción",
      value: "",
      source: "Escritura",
      origin: exp.config.propertyType === "vivienda" ? "extraido" : "no_aplica",
      notApplicable: exp.config.propertyType !== "vivienda",
    },
    { id: "gravamenes", label: "Existencia de gravámenes", value: "", source: "Certificado de gravamen", confidence: "alta", origin: "extraido" },
    { id: "situacion-predial", label: "Situación de predial", value: "", source: "Predial", confidence: "alta", origin: "extraido" },
    { id: "situacion-servicios", label: "Situación de servicios", value: "Pendiente de revisión", source: "Declarado por cliente", origin: "declarado" },
  ];

  return fields;
}
