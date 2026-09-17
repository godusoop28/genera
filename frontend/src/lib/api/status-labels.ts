import type { BackendExpedienteStatus } from "@/lib/api/types";

type Tone = "neutral" | "warning" | "gold" | "obsessed" | "success" | "danger" | "info";

export const backendStatusLabels: Record<BackendExpedienteStatus, string> = {
  DRAFT: "Borrador",
  WAITING_PRIVACY: "Esperando privacidad",
  WAITING_DOCUMENTS: "Esperando documentos",
  DOCUMENTS_RECEIVED: "Documentos recibidos",
  UNDER_REVIEW: "En revisión",
  CORRECTIONS_REQUESTED: "Correcciones solicitadas",
  DOCUMENTS_APPROVED: "Documentos aprobados",
  RECEPTION_SIGNED: "Recepción firmada",
  CONTRACT_PREPARATION: "Preparación de contrato",
  READY_FOR_SIGNATURE: "Listo para firma",
  PROPERTY_ACCEPTED: "Inmueble aceptado",
  PROPERTY_REJECTED: "Inmueble rechazado",
  CLOSED: "Cerrado",
};

export const backendStatusTone: Record<BackendExpedienteStatus, Tone> = {
  DRAFT: "neutral",
  WAITING_PRIVACY: "warning",
  WAITING_DOCUMENTS: "warning",
  DOCUMENTS_RECEIVED: "info",
  UNDER_REVIEW: "gold",
  CORRECTIONS_REQUESTED: "warning",
  DOCUMENTS_APPROVED: "success",
  RECEPTION_SIGNED: "info",
  CONTRACT_PREPARATION: "info",
  READY_FOR_SIGNATURE: "gold",
  PROPERTY_ACCEPTED: "success",
  PROPERTY_REJECTED: "danger",
  CLOSED: "obsessed",
};
