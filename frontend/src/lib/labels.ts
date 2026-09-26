// Etiquetas en español para todo valor que llega del backend como código
// (HOUSING, OWNER, APODERADO...). Ninguna pantalla debe mostrar el código
// crudo al usuario.
import type {
  BackendAccreditationType,
  BackendCivilStatus,
  BackendDocumentStatus,
  BackendIdDocumentType,
  BackendMaritalRegime,
  BackendParticipantRole,
  BackendPersonType,
  BackendPropertyCaseType,
  BackendPropertyLegalStatus,
  BackendSignerCharacter,
  ContractGenerationStatus,
  ReturnReasonCode,
} from "@/lib/api/types";

export const personTypeLabels: Record<BackendPersonType, string> = {
  FISICA: "Persona física",
  MORAL: "Persona moral (empresa)",
};

export const signerCharacterLabels: Record<BackendSignerCharacter, string> = {
  PROPIETARIO: "Firma el propietario",
  COPROPIETARIO: "Firman los copropietarios",
  APODERADO: "Firma un apoderado",
  REPRESENTANTE_LEGAL: "Firma el representante legal",
};

export const accreditationLabels: Record<BackendAccreditationType, string> = {
  ESCRITURA_PUBLICA: "Escritura pública",
  CONTRATO_PRIVADO: "Contrato privado ratificado",
};

export const propertyTypeLabels: Record<BackendPropertyCaseType, string> = {
  HOUSING: "Casa",
  DEPARTMENT: "Departamento",
  RESIDENTIAL_LAND: "Terreno para casa habitación",
  COMMERCIAL: "Comercial",
};

export const legalStatusLabels: Record<BackendPropertyLegalStatus, string> = {
  LIBRE_GRAVAMEN: "Libre de gravamen",
  CON_GRAVAMEN: "Con gravamen",
  EN_REVISION: "En revisión",
};

export const participantRoleLabels: Record<BackendParticipantRole, string> = {
  OWNER: "Propietario principal",
  CO_OWNER: "Copropietario",
  ATTORNEY: "Apoderado",
  LEGAL_REPRESENTATIVE: "Representante legal",
};

export const companyOwnerLabel = "Empresa titular";

export const civilStatusLabels: Record<BackendCivilStatus, string> = {
  SOLTERO: "Soltero(a)",
  CASADO: "Casado(a)",
  UNION_LIBRE: "Unión libre",
  DIVORCIADO: "Divorciado(a)",
  VIUDO: "Viudo(a)",
};

export const maritalRegimeLabels: Record<BackendMaritalRegime, string> = {
  SOCIEDAD_CONYUGAL: "Sociedad conyugal (bienes mancomunados)",
  SEPARACION_DE_BIENES: "Separación de bienes",
};

export const idDocumentLabels: Record<BackendIdDocumentType, string> = {
  INE: "Credencial para votar (INE)",
  PASAPORTE: "Pasaporte",
  CEDULA_PROFESIONAL: "Cédula profesional",
  FM2_RESIDENTE: "Tarjeta de residente",
};

export const documentStatusLabels: Record<BackendDocumentStatus, string> = {
  PENDING: "Sin cargar",
  UPLOADED: "Cargado, procesando",
  READY_FOR_REVIEW: "Por revisar",
  ACCEPTED: "Aceptado",
  RETURNED: "Devuelto al cliente",
  REJECTED: "Rechazado",
  REPLACED: "Reemplazado",
  NOT_APPLICABLE: "No aplica",
};

export const documentStatusTone: Record<BackendDocumentStatus, "neutral" | "info" | "warning" | "success" | "danger"> = {
  PENDING: "neutral",
  UPLOADED: "info",
  READY_FOR_REVIEW: "info",
  ACCEPTED: "success",
  RETURNED: "warning",
  REJECTED: "danger",
  REPLACED: "neutral",
  NOT_APPLICABLE: "neutral",
};

export const returnReasonLabels: Record<ReturnReasonCode, string> = {
  BLURRY_IMAGE: "Imagen borrosa",
  INCOMPLETE_DOCUMENT: "Documento incompleto",
  EXPIRED_DOCUMENT: "Documento vencido",
  ILLEGIBLE_INFORMATION: "Información ilegible",
  WRONG_DOCUMENT: "No es el documento solicitado",
  MISSING_PAGE: "Falta una página",
  OTHER: "Otro (explicar en el comentario)",
};

export const contractStatusLabels: Record<ContractGenerationStatus, string> = {
  DRAFT_INCOMPLETE: "Borrador incompleto (no se puede firmar)",
  GENERATED: "Enviado a firma",
  PARTIALLY_SIGNED: "Firmado parcialmente",
  SIGNED: "Firmado por todas las partes",
  DELIVERED: "Firmado y entregado al cliente",
  SUPERSEDED: "Sin efecto (reemplazado)",
};

export const contractStatusTone: Record<ContractGenerationStatus, "neutral" | "info" | "warning" | "success" | "danger" | "gold"> = {
  DRAFT_INCOMPLETE: "warning",
  GENERATED: "gold",
  PARTIALLY_SIGNED: "info",
  SIGNED: "success",
  DELIVERED: "success",
  SUPERSEDED: "neutral",
};

export const roleLabels: Record<string, string> = {
  ADMINISTRATOR: "Administrador",
  DIRECTOR: "Director",
  ADVISOR: "Asesor",
  DOCUMENT_REVIEWER: "Revisor documental",
};

export const complianceLabels: Record<string, string> = {
  PRIVACY_NOTICE_PRESENTED: "Aviso de privacidad vigente",
  CONTRACT_TEMPLATE_CORRECT: "Modelo de contrato vigente",
  MAIN_CONSENT_RECORDED: "El cliente aceptó el aviso de privacidad",
  REQUIRED_DOCUMENTS_ACCEPTED: "Documentos obligatorios aceptados",
  RECEPTION_SIGNED: "Recepción de documentos firmada",
  NO_UNRESOLVED_DATA_CONFLICTS: "Sin diferencias sin resolver entre documentos",
};

export const closingStatusLabels: Record<string, string> = {
  OPEN: "Abierto",
  IN_PROGRESS: "En curso",
  COMPLETED: "Completado",
};

export const notificationStatusLabels: Record<string, string> = {
  QUEUED: "En cola",
  SENT: "Enviado",
  RETRYING: "Falló, se reintentará",
  FAILED: "No se pudo entregar",
  SKIPPED: "No enviado (sin correo)",
};

/** Nombres legibles de los campos que extrae la IA. */
export const extractedFieldLabels: Record<string, string> = {
  fullName: "Nombre completo",
  curp: "CURP",
  electorKey: "Clave de elector",
  address: "Domicilio",
  expirationYear: "Vigencia",
  passportNumber: "Número de pasaporte",
  birthDate: "Fecha de nacimiento",
  rfc: "RFC",
  taxRegime: "Régimen fiscal",
  ownerFullName: "Propietario",
  propertyAddress: "Domicilio del inmueble",
  publicRegistryFolio: "Folio real / datos registrales",
  deedNumber: "Número de escritura",
  notaryNumber: "Número de notaría",
  landArea: "Superficie de terreno",
  builtArea: "Superficie de construcción",
  issueDate: "Fecha de emisión",
  hasLiens: "Gravámenes",
  cadastralKey: "Clave catastral",
  grantorFullName: "Poderdante",
  attorneyFullName: "Apoderado",
  regimeRegistrationFolio: "Folio del régimen",
  spouseFullName: "Cónyuge",
  marriageDate: "Fecha de matrimonio",
  maritalRegime: "Régimen matrimonial",
  buyerFullName: "Comprador",
  sellerFullName: "Vendedor",
  contractDate: "Fecha del contrato",
  companyName: "Razón social",
  instrumentNumber: "Número de instrumento",
  mercantileFolio: "Folio mercantil",
  nationality: "Nacionalidad",
  deedDate: "Fecha de la escritura",
  notaryName: "Notario",
  notaryPlace: "Lugar de la notaría",
  notaryTitle: "Notario o corredor",
  registryDate: "Fecha de inscripción",
  ratificationDate: "Fecha de ratificación",
  instrumentDate: "Fecha del instrumento",
  companyType: "Tipo de sociedad",
  commerceRegistryPlace: "Registro Público de Comercio",
};

export function label(map: Record<string, string>, value: string | null | undefined, fallback = "—"): string {
  if (!value) return fallback;
  return map[value] ?? value;
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return "—";
  const date = value.length === 10 ? new Date(`${value}T12:00:00`) : new Date(value);
  return date.toLocaleDateString("es-MX", { day: "numeric", month: "long", year: "numeric" });
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return "—";
  return new Date(value).toLocaleString("es-MX", { dateStyle: "medium", timeStyle: "short" });
}

export function formatMoney(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === "") return "—";
  const n = typeof value === "number" ? value : Number(value);
  if (Number.isNaN(n)) return String(value);
  return n.toLocaleString("es-MX", { style: "currency", currency: "MXN" });
}
