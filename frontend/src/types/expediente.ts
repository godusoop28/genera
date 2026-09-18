// Tipos del prototipo — Módulo 1: Gestión documental y contratos.
// Todo el estado se maneja en memoria/localStorage (ver src/context). No hay backend.

// ---------- Usuarios internos / roles / permisos ----------

export type PermissionId =
  | "crear_expediente"
  | "ver_expedientes_propios"
  | "ver_todos_expedientes"
  | "generar_liga"
  | "revisar_documentos"
  | "aceptar_documentos"
  | "devolver_documentos"
  | "rechazar_documentos"
  | "editar_datos_extraidos"
  | "preparar_contrato"
  | "generar_contrato"
  | "enviar_correo"
  | "firmar_recepcion"
  | "decidir_inmueble"
  | "gestionar_usuarios"
  | "gestionar_permisos";

export type RoleId = "administrador" | "asesor" | "revisor";

export interface Role {
  id: RoleId;
  name: string;
  description: string;
  permissions: PermissionId[];
}

export type UserStatus = "active" | "inactive";

export interface InternalUser {
  id: string;
  name: string;
  roleId: RoleId;
  status: UserStatus;
  email?: string;
  lastActivity: string;
}

// ---------- Configuración del expediente ----------

export type ContractTypeId = "intermediacion_exclusiva" | "arrendamiento" | "otro";

export type OwnerCount = 1 | 2 | 3;

export type AccreditationType = "escritura_publica" | "contrato_privado";

export type PersonType = "fisica" | "moral";

export type SignerCharacter = "propietario" | "copropietario" | "apoderado";

export type PropertyType = "casa" | "departamento" | "terreno" | "comercial";

export const propertyTypeLabels: Record<PropertyType, string> = {
  casa: "Casa",
  departamento: "Departamento",
  terreno: "Terreno",
  comercial: "Comercial",
};

export type PropertyLegalStatus = "libre_gravamen" | "con_gravamen" | "en_revision";

export type MaritalStatus = "soltero" | "casado";

export const maritalStatusLabels: Record<MaritalStatus, string> = {
  soltero: "Soltero(a)",
  casado: "Casado(a)",
};

export type MaritalPropertyRegime = "bienes_mancomunados" | "separacion_bienes";

export const maritalPropertyRegimeLabels: Record<MaritalPropertyRegime, string> = {
  bienes_mancomunados: "Régimen conyugal (bienes mancomunados)",
  separacion_bienes: "Separación de bienes",
};

export interface ExpedienteConfig {
  contractType: ContractTypeId;
  ownerCount: OwnerCount;
  personType: PersonType;
  signerCharacter: SignerCharacter;
  accreditation: AccreditationType;
  condominiumRegime: boolean;
  propertyType: PropertyType;
  legalStatus: PropertyLegalStatus;
  civilStatus: MaritalStatus;
  maritalPropertyRegime?: MaritalPropertyRegime;
}

// ---------- Propietarios ----------

export interface Owner {
  id: string;
  label: string; // "Propietario 1", "Propietario 2"...
}

// ---------- Documentos ----------

export type DocumentStatus =
  | "pending"
  | "uploaded"
  | "processing"
  | "ready_for_review"
  | "accepted"
  | "returned"
  | "rejected"
  | "replaced";

export const documentStatusLabels: Record<DocumentStatus, string> = {
  pending: "Pendiente",
  uploaded: "Cargado",
  processing: "Procesando",
  ready_for_review: "Listo para revisión",
  accepted: "Aceptado",
  returned: "Devuelto para corrección",
  rejected: "Rechazado",
  replaced: "Sustituido",
};

export type DocumentCategory =
  | "identidad"
  | "fiscal"
  | "propiedad"
  | "cumplimiento"
  | "contrato"
  | "anexos"
  | "cierre";

export interface DocumentPage {
  id: string;
  dataUrl: string; // object URL simulado (FileReader), no se persiste en localStorage
  name: string;
}

export interface DocumentRequirement {
  id: string;
  name: string;
  description?: string;
  category: DocumentCategory;
  required: boolean;
  conditional?: boolean;
  ownerId?: string;
  custom?: boolean;
}

export interface UploadedDocument {
  requirementId: string;
  status: DocumentStatus;
  pages: DocumentPage[];
  uploadedAt?: string;
  review?: DocumentReview;
}

export type ReturnReason =
  | "imagen_borrosa"
  | "documento_incompleto"
  | "documento_vencido"
  | "informacion_ilegible"
  | "documento_incorrecto"
  | "falta_pagina"
  | "otro";

export const returnReasonLabels: Record<ReturnReason, string> = {
  imagen_borrosa: "Imagen borrosa",
  documento_incompleto: "Documento incompleto",
  documento_vencido: "Documento vencido",
  informacion_ilegible: "Información ilegible",
  documento_incorrecto: "Documento incorrecto",
  falta_pagina: "Falta una página",
  otro: "Otro",
};

export interface DocumentReview {
  decision: "accepted" | "returned" | "rejected";
  reason?: ReturnReason;
  comment?: string;
  reviewedAt: string;
  reviewedBy: string;
}

// ---------- Privacidad ----------

export interface PrivacyConsent {
  mainConsent: boolean;
  secondaryConsent: boolean; // finalidades secundarias, independiente
  signedAt?: string;
  signatureDataUrl?: string;
}

// ---------- Datos manuales del cliente ----------

export type CivilStatus = "Soltero(a)" | "Casado(a)" | "Unión libre" | "Divorciado(a)" | "Viudo(a)";

export interface ManualClientData {
  civilStatus?: CivilStatus;
  authorizedPrice?: number;
  email?: string;
  phone?: string;
  notificationAddress?: string;
  visitInstructions?: string;
  marketingDataAuthorized?: boolean; // fines mercadotécnicos/publicitarios
  receiveAdsAuthorized?: boolean; // recibir publicidad
  additionalServicesRequested?: string;
  bedrooms?: number;
  bathrooms?: number;
  parkingSpots?: number;
  conservationStatus?: string;
  availableServices?: string;
  relevantFeatures?: string;
  contractSignatureDate?: string;
}

// ---------- Campos extraídos (IA simulada) ----------

export type FieldSource =
  | "INE"
  | "Escritura"
  | "Poder notarial"
  | "Constancia fiscal"
  | "Predial"
  | "Certificado de gravamen"
  | "Régimen de condominio"
  | "Declarado por cliente"
  | "Calculado";

export type FieldConfidence = "alta" | "media" | "revisar";

export type FieldOrigin = "extraido" | "editado" | "declarado" | "calculado" | "no_aplica";

export interface ExtractedField {
  id: string;
  label: string;
  value: string;
  source: FieldSource;
  confidence?: FieldConfidence;
  origin: FieldOrigin;
  notApplicable?: boolean;
}

// ---------- Contrato ----------

export interface ContractCalculations {
  priceNumber: number;
  priceWritten: string;
  commission: number;
  vat: number;
  totalCommissionVat: number;
  penalty: number;
  exclusivityDays: number;
  exclusivityEndDate: string;
}

export interface ContractData {
  profecoNumber: string;
  profecoDate: string;
  intermediaryLegalName: string;
  intermediaryCommercialName: string;
  legalRepresentative: string;
  signatureDate?: string;
}

// ---------- Cumplimiento ----------

export interface ComplianceItem {
  id: string;
  label: string;
  done: boolean;
}

// ---------- Actividad ----------

export type ActivityType =
  | "expediente_creado"
  | "liga_generada"
  | "liga_revocada"
  | "liga_regenerada"
  | "aviso_aceptado"
  | "documentos_enviados"
  | "ia_procesada"
  | "documento_devuelto"
  | "documento_rechazado"
  | "documento_reemplazado"
  | "documento_aceptado"
  | "recepcion_firmada"
  | "contrato_preparado"
  | "correo_enviado"
  | "inmueble_aceptado"
  | "inmueble_rechazado"
  | "cierre_actualizado";

export interface ActivityItem {
  id: string;
  type: ActivityType;
  message: string;
  at: string;
}

// ---------- Decisión de inmueble ----------

export interface PropertyDecision {
  decision: "pending" | "accepted" | "rejected";
  reason?: string;
  decidedAt?: string;
  decidedBy?: string;
}

// ---------- Correo simulado ----------

export interface EmailSimulation {
  to: string;
  subject: string;
  message: string;
  attachments: string[];
  sentAt: string;
}

// ---------- Cierre de venta ----------

export type ClosingStatus = "pending" | "in_progress" | "completed";

export interface ClosingDocument {
  id: string;
  name: string;
  addedAt: string;
}

export interface ClosingInfo {
  status: ClosingStatus;
  documents: ClosingDocument[];
  notes: string;
  contractSigned: boolean;
  copyDelivered: boolean;
  deliveryDate?: string;
  deliveryMethod?: "correo" | "fisico" | "otro";
}

// ---------- Servicios adicionales (Anexo D) ----------

export interface AdditionalService {
  id: string;
  description: string;
  cost: number;
  acceptedByClient: boolean;
}

// ---------- Expediente ----------

export type ExpedienteStatus =
  | "draft"
  | "waiting_privacy"
  | "waiting_documents"
  | "documents_received"
  | "under_review"
  | "corrections_requested"
  | "documents_approved"
  | "contract_preparation"
  | "ready_for_signature"
  | "property_accepted"
  | "property_rejected"
  | "closed";

export const expedienteStatusLabels: Record<ExpedienteStatus, string> = {
  draft: "Borrador",
  waiting_privacy: "Esperando aceptación de privacidad",
  waiting_documents: "Esperando documentos",
  documents_received: "Documentos recibidos",
  under_review: "En revisión",
  corrections_requested: "Correcciones solicitadas",
  documents_approved: "Documentación aprobada",
  contract_preparation: "Contrato en preparación",
  ready_for_signature: "Listo para firma",
  property_accepted: "Propiedad aceptada",
  property_rejected: "Propiedad rechazada",
  closed: "Cerrado",
};

export interface Expediente {
  id: string;
  folio: string;
  ownerName: string;
  ownerCount: OwnerCount;
  status: ExpedienteStatus;
  createdAt: string;
  updatedAt: string;
  config: ExpedienteConfig;
  owners: Owner[];
  propertyAddress?: string;
  documentRequirements: DocumentRequirement[];
  documents: Record<string, UploadedDocument>;
  privacyConsent: PrivacyConsent;
  manualData: ManualClientData;
  extractedFields: ExtractedField[];
  contract: ContractData;
  activity: ActivityItem[];
  propertyDecision: PropertyDecision;
  emails: EmailSimulation[];
  closing: ClosingInfo;
  additionalServices: AdditionalService[];
  receptionSignedAt?: string;
  receptionSignedBy?: string;
  linkStatus: "active" | "revoked";
  linkId: string;
}
