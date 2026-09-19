// Prototipo visual: datos de prueba en memoria para navegar "Expedientes"
// sin depender de que el backend (localhost:8080) esté corriendo. Se activa
// junto con SKIP_AUTH_FOR_PROTOTYPE en AuthProvider. Poner DEMO_MODE en false
// para volver a consumir el backend real en src/lib/api/expedientes.ts,
// activity.ts, closing.ts, compliance.ts y public-link.ts.
import { ApiError } from "@/lib/api/client";
import type { CreateExpedienteRequest } from "@/lib/api/expedientes";
import type { PublicLinkResponse } from "@/lib/api/public-link";
import type {
  ActivityResponse,
  ClosingCaseResponse,
  ClosingNoteResponse,
  ComplianceChecklistResponse,
  ExpedienteResponse,
  ParticipantResponse,
  PageResponse,
  RequirementResponse,
} from "@/lib/api/types";

export const DEMO_MODE = false;

function expediente(data: Omit<ExpedienteResponse, "propertyAddress" | "decisionReason" | "decidedByUserId" | "decidedAt"> & { propertyAddress?: string | null }): ExpedienteResponse {
  return {
    propertyAddress: null,
    decisionReason: null,
    decidedByUserId: null,
    decidedAt: null,
    ...data,
  };
}

const demoExpedientes: ExpedienteResponse[] = [
  expediente({
    id: "demo-1",
    folio: "EXP-2026-000001",
    ownerDisplayName: "Juan Pérez López",
    status: "UNDER_REVIEW",
    personType: "FISICA",
    signerCharacter: "PROPIETARIO",
    accreditationType: "ESCRITURA_PUBLICA",
    condominiumRegime: false,
    propertyCaseType: "HOUSING",
    declaredLegalStatus: "LIBRE_GRAVAMEN",
    propertyAddress: "Calle Roble 456, Col. Nápoles, Benito Juárez, CDMX",
    createdAt: "2026-09-13T15:00:00.000Z",
    updatedAt: "2026-09-14T11:30:00.000Z",
  }),
  expediente({
    id: "demo-2",
    folio: "EXP-2026-000002",
    ownerDisplayName: "María Fernanda Ruiz",
    status: "WAITING_DOCUMENTS",
    personType: "FISICA",
    signerCharacter: "PROPIETARIO",
    accreditationType: "ESCRITURA_PUBLICA",
    condominiumRegime: true,
    propertyCaseType: "DEPARTMENT",
    declaredLegalStatus: "LIBRE_GRAVAMEN",
    propertyAddress: "Av. Reforma 890, Depto. 12B, Cuauhtémoc, CDMX",
    createdAt: "2026-09-14T09:00:00.000Z",
    updatedAt: "2026-09-15T09:00:00.000Z",
  }),
  expediente({
    id: "demo-3",
    folio: "EXP-2026-000003",
    ownerDisplayName: "Carlos Ramírez",
    status: "CORRECTIONS_REQUESTED",
    personType: "FISICA",
    signerCharacter: "COPROPIETARIO",
    accreditationType: "CONTRATO_PRIVADO",
    condominiumRegime: false,
    propertyCaseType: "HOUSING",
    declaredLegalStatus: "CON_GRAVAMEN",
    propertyAddress: "Cerrada de los Pinos 12, Coyoacán, CDMX",
    createdAt: "2026-09-10T09:00:00.000Z",
    updatedAt: "2026-09-12T09:00:00.000Z",
  }),
  expediente({
    id: "demo-4",
    folio: "EXP-2026-000004",
    ownerDisplayName: "Lucía Hernández Soto",
    status: "DRAFT",
    personType: "FISICA",
    signerCharacter: "PROPIETARIO",
    accreditationType: "ESCRITURA_PUBLICA",
    condominiumRegime: false,
    propertyCaseType: "RESIDENTIAL_LAND",
    declaredLegalStatus: "EN_REVISION",
    createdAt: "2026-09-10T09:00:00.000Z",
    updatedAt: "2026-09-10T09:00:00.000Z",
  }),
  expediente({
    id: "demo-5",
    folio: "EXP-2026-000005",
    ownerDisplayName: "Roberto Álvarez Mena",
    status: "READY_FOR_SIGNATURE",
    personType: "MORAL",
    signerCharacter: "APODERADO",
    accreditationType: "ESCRITURA_PUBLICA",
    condominiumRegime: false,
    propertyCaseType: "COMMERCIAL",
    declaredLegalStatus: "LIBRE_GRAVAMEN",
    propertyAddress: "Blvd. Manuel Ávila Camacho 200, Local 3, Naucalpan, Edomex",
    createdAt: "2026-09-08T09:00:00.000Z",
    updatedAt: "2026-09-16T09:00:00.000Z",
  }),
];

const demoParticipants: Record<string, ParticipantResponse[]> = {
  "demo-1": [{ id: "demo-1-p1", role: "OWNER", fullName: "Juan Pérez López", ordinal: 1 }],
  "demo-2": [{ id: "demo-2-p1", role: "OWNER", fullName: "María Fernanda Ruiz", ordinal: 1 }],
  "demo-3": [
    { id: "demo-3-p1", role: "OWNER", fullName: "Carlos Ramírez", ordinal: 1 },
    { id: "demo-3-p2", role: "CO_OWNER", fullName: "Ana Ramírez Solís", ordinal: 2 },
  ],
  "demo-4": [{ id: "demo-4-p1", role: "OWNER", fullName: "Lucía Hernández Soto", ordinal: 1 }],
  "demo-5": [{ id: "demo-5-p1", role: "LEGAL_REPRESENTATIVE", fullName: "Roberto Álvarez Mena", ordinal: 1 }],
};

const demoRequirements: Record<string, RequirementResponse[]> = {
  "demo-1": [
    { requirementCode: "INE", type: "Identificación oficial (INE)", required: true, conditional: false, participantId: "demo-1-p1" },
    { requirementCode: "RFC", type: "Constancia de situación fiscal", required: true, conditional: false, participantId: "demo-1-p1" },
    { requirementCode: "ESCRITURA", type: "Escritura pública", required: true, conditional: false, participantId: null },
    { requirementCode: "PREDIAL", type: "Boleta predial", required: true, conditional: false, participantId: null },
    { requirementCode: "CFE", type: "Recibo de CFE", required: true, conditional: false, participantId: null },
    { requirementCode: "ACTA_MATRIMONIO", type: "Acta de matrimonio", required: true, conditional: true, participantId: "demo-1-p1" },
  ],
  "demo-2": [
    { requirementCode: "INE", type: "Identificación oficial (INE)", required: true, conditional: false, participantId: "demo-2-p1" },
    { requirementCode: "ESCRITURA", type: "Escritura pública", required: true, conditional: false, participantId: null },
    { requirementCode: "REGLAMENTO_CONDOMINIO", type: "Reglamento de condominio", required: true, conditional: true, participantId: null },
  ],
  "demo-3": [
    { requirementCode: "INE", type: "Identificación oficial (INE)", required: true, conditional: false, participantId: "demo-3-p1" },
    { requirementCode: "CFE", type: "Recibo de CFE", required: true, conditional: false, participantId: null },
    { requirementCode: "CONTRATO_PRIVADO", type: "Contrato privado de compraventa", required: true, conditional: false, participantId: null },
  ],
  "demo-4": [{ requirementCode: "INE", type: "Identificación oficial (INE)", required: true, conditional: false, participantId: "demo-4-p1" }],
  "demo-5": [
    { requirementCode: "PODER_NOTARIAL", type: "Poder notarial", required: true, conditional: false, participantId: "demo-5-p1" },
    { requirementCode: "ACTA_CONSTITUTIVA", type: "Acta constitutiva", required: true, conditional: false, participantId: null },
  ],
};

const demoActivity: Record<string, ActivityResponse[]> = {
  "demo-1": [
    { id: "demo-1-a1", occurredAt: "2026-09-13T15:00:00.000Z", category: "EXPEDIENTE", message: "Expediente EXP-2026-000001 creado." },
    { id: "demo-1-a2", occurredAt: "2026-09-13T15:02:00.000Z", category: "LIGA", message: "Liga de recepción documental generada." },
    { id: "demo-1-a3", occurredAt: "2026-09-14T10:05:00.000Z", category: "PRIVACIDAD", message: "Juan Pérez López aceptó el aviso de privacidad." },
    { id: "demo-1-a4", occurredAt: "2026-09-14T10:40:00.000Z", category: "DOCUMENTOS", message: "El cliente envió su documentación." },
    { id: "demo-1-a5", occurredAt: "2026-09-14T11:30:00.000Z", category: "DOCUMENTOS", message: "Todos los documentos requeridos fueron aceptados." },
  ],
  "demo-2": [
    { id: "demo-2-a1", occurredAt: "2026-09-14T09:00:00.000Z", category: "EXPEDIENTE", message: "Expediente EXP-2026-000002 creado." },
    { id: "demo-2-a2", occurredAt: "2026-09-15T09:00:00.000Z", category: "LIGA", message: "Liga de recepción documental generada." },
  ],
  "demo-3": [
    { id: "demo-3-a1", occurredAt: "2026-09-10T09:00:00.000Z", category: "EXPEDIENTE", message: "Expediente EXP-2026-000003 creado." },
    { id: "demo-3-a2", occurredAt: "2026-09-11T09:05:00.000Z", category: "DOCUMENTOS", message: "El cliente envió su documentación." },
    { id: "demo-3-a3", occurredAt: "2026-09-12T09:00:00.000Z", category: "DOCUMENTOS", message: "Documento devuelto para corrección: Recibo de CFE." },
  ],
  "demo-4": [{ id: "demo-4-a1", occurredAt: "2026-09-10T09:00:00.000Z", category: "EXPEDIENTE", message: "Expediente EXP-2026-000004 creado." }],
  "demo-5": [
    { id: "demo-5-a1", occurredAt: "2026-09-08T09:00:00.000Z", category: "EXPEDIENTE", message: "Expediente EXP-2026-000005 creado." },
    { id: "demo-5-a2", occurredAt: "2026-09-16T09:00:00.000Z", category: "CONTRATO", message: "Contrato listo para firma." },
  ],
};

const demoCompliance: Record<string, ComplianceChecklistResponse> = {
  "demo-1": {
    expedienteId: "demo-1",
    allPassed: true,
    items: [
      { code: "IDENTIDAD_VERIFICADA", passed: true, detail: "INE validada contra RENAPO." },
      { code: "PROPIEDAD_ACREDITADA", passed: true, detail: "Escritura pública vigente y sin gravamen." },
      { code: "PREDIAL_AL_CORRIENTE", passed: true, detail: "Sin adeudos registrados." },
    ],
  },
  "demo-2": {
    expedienteId: "demo-2",
    allPassed: false,
    items: [
      { code: "IDENTIDAD_VERIFICADA", passed: true, detail: "INE validada contra RENAPO." },
      { code: "DOCUMENTOS_COMPLETOS", passed: false, detail: "Faltan 2 documentos por recibir." },
    ],
  },
  "demo-3": {
    expedienteId: "demo-3",
    allPassed: false,
    items: [
      { code: "IDENTIDAD_VERIFICADA", passed: true, detail: "INE validada contra RENAPO." },
      { code: "DOCUMENTOS_LEGIBLES", passed: false, detail: "Recibo de CFE devuelto por ilegible." },
    ],
  },
  "demo-4": { expedienteId: "demo-4", allPassed: false, items: [{ code: "DOCUMENTOS_COMPLETOS", passed: false, detail: "Aún no se envía documentación." }] },
  "demo-5": {
    expedienteId: "demo-5",
    allPassed: true,
    items: [
      { code: "IDENTIDAD_VERIFICADA", passed: true, detail: "Poder notarial validado." },
      { code: "PROPIEDAD_ACREDITADA", passed: true, detail: "Acta constitutiva y poder vigentes." },
    ],
  },
};

const demoClosingCases: Record<string, ClosingCaseResponse> = {
  "demo-5": { id: "demo-5-closing", expedienteId: "demo-5", status: "IN_PROGRESS", contractDelivered: true, contractDeliveredAt: "2026-09-16T09:00:00.000Z" },
};

const demoClosingNotes: Record<string, ClosingNoteResponse[]> = {
  "demo-5": [{ id: "demo-5-n1", authorUserId: "demo-user", note: "Cliente confirmó cita de firma para el viernes.", createdAt: "2026-09-16T12:00:00.000Z" }],
};

let demoFolioSeq = demoExpedientes.length + 1;

function delay<T>(value: T): Promise<T> {
  return Promise.resolve(value);
}

export function demoListExpedientes(page = 0, size = 20): Promise<PageResponse<ExpedienteResponse>> {
  const start = page * size;
  const items = demoExpedientes.slice(start, start + size);
  return delay({
    items,
    page,
    size,
    totalElements: demoExpedientes.length,
    totalPages: Math.ceil(demoExpedientes.length / size),
  });
}

export function demoCreateExpediente(request: CreateExpedienteRequest): Promise<ExpedienteResponse> {
  const id = `demo-${Date.now()}`;
  const now = new Date().toISOString();
  const folio = `EXP-2026-${String(demoFolioSeq).padStart(6, "0")}`;
  demoFolioSeq += 1;

  const created = expediente({
    id,
    folio,
    ownerDisplayName: request.ownerDisplayName,
    status: "DRAFT",
    personType: request.personType,
    signerCharacter: request.signerCharacter,
    accreditationType: request.accreditationType,
    condominiumRegime: request.condominiumRegime,
    propertyCaseType: request.propertyCaseType,
    declaredLegalStatus: request.declaredLegalStatus,
    propertyAddress: request.propertyAddress ?? null,
    createdAt: now,
    updatedAt: now,
  });

  demoExpedientes.unshift(created);
  demoParticipants[id] = request.participants.map((p, i) => ({
    id: `${id}-p${i + 1}`,
    role: p.role,
    fullName: p.fullName,
    ordinal: i + 1,
  }));
  demoRequirements[id] = [
    { requirementCode: "INE", type: "Identificación oficial (INE)", required: true, conditional: false, participantId: `${id}-p1` },
  ];
  demoActivity[id] = [{ id: `${id}-a1`, occurredAt: now, category: "EXPEDIENTE", message: `Expediente ${folio} creado.` }];
  demoCompliance[id] = {
    expedienteId: id,
    allPassed: false,
    items: [{ code: "DOCUMENTOS_COMPLETOS", passed: false, detail: "Aún no se envía documentación." }],
  };

  return delay(created);
}

export function demoGetExpediente(id: string): Promise<ExpedienteResponse> {
  const found = demoExpedientes.find((e) => e.id === id);
  if (!found) return Promise.reject(new ApiError("Expediente no encontrado.", 404));
  return delay(found);
}

export function demoGetParticipants(id: string): Promise<ParticipantResponse[]> {
  return delay(demoParticipants[id] ?? []);
}

export function demoGetRequirements(id: string): Promise<RequirementResponse[]> {
  return delay(demoRequirements[id] ?? []);
}

export function demoGetActivity(id: string): Promise<ActivityResponse[]> {
  return delay(demoActivity[id] ?? []);
}

export function demoGetComplianceChecklist(id: string): Promise<ComplianceChecklistResponse> {
  return delay(demoCompliance[id] ?? { expedienteId: id, allPassed: false, items: [] });
}

export function demoGetClosingCase(id: string): Promise<ClosingCaseResponse> {
  const found = demoClosingCases[id];
  if (!found) return Promise.reject(new ApiError("Seguimiento de cierre no iniciado.", 404));
  return delay(found);
}

export function demoGetClosingNotes(id: string): Promise<ClosingNoteResponse[]> {
  return delay(demoClosingNotes[id] ?? []);
}

export function demoAddClosingNote(id: string, note: string): Promise<ClosingNoteResponse> {
  const created: ClosingNoteResponse = {
    id: `${id}-n${(demoClosingNotes[id]?.length ?? 0) + 1}`,
    authorUserId: "demo-user",
    note,
    createdAt: new Date().toISOString(),
  };
  demoClosingNotes[id] = [...(demoClosingNotes[id] ?? []), created];
  return delay(created);
}

export function demoGeneratePublicLink(id: string): Promise<PublicLinkResponse> {
  return delay({
    url: `https://demo.century21genera.local/carga/${id}`,
    expiresAt: new Date(Date.now() + 1000 * 60 * 60 * 24 * 7).toISOString(),
  });
}
