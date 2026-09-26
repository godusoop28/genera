import type { BackendExpedienteStatus } from "@/lib/api/types";

type Tone = "neutral" | "warning" | "gold" | "obsessed" | "success" | "danger" | "info";

export const backendStatusLabels: Record<BackendExpedienteStatus, string> = {
  DRAFT: "Nuevo (sin liga para el cliente)",
  WAITING_PRIVACY: "Esperando aviso de privacidad",
  WAITING_DOCUMENTS: "Esperando documentos del cliente",
  DOCUMENTS_RECEIVED: "Documentos recibidos",
  UNDER_REVIEW: "En revisión",
  CORRECTIONS_REQUESTED: "Correcciones pendientes",
  DOCUMENTS_APPROVED: "Documentación aprobada",
  RECEPTION_SIGNED: "Recepción firmada",
  CONTRACT_PREPARATION: "Preparando contrato",
  READY_FOR_SIGNATURE: "Contrato en firma",
  CONTRACT_SIGNED: "Contrato firmado",
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
  CONTRACT_SIGNED: "success",
  PROPERTY_ACCEPTED: "success",
  PROPERTY_REJECTED: "danger",
  CLOSED: "obsessed",
};

/** Qué significa cada estatus, qué sigue y quién debe hacerlo (se muestra en el detalle del expediente). */
export const backendStatusNextStep: Record<BackendExpedienteStatus, { meaning: string; next: string; who: string }> = {
  DRAFT: {
    meaning: "El expediente se creó pero el cliente todavía no tiene acceso.",
    next: "Generar la liga del cliente y compartírsela.",
    who: "Asesor",
  },
  WAITING_PRIVACY: {
    meaning: "El cliente tiene su liga pero no ha aceptado el aviso de privacidad.",
    next: "Que el cliente abra su liga, acepte y firme el aviso de privacidad.",
    who: "Cliente",
  },
  WAITING_DOCUMENTS: {
    meaning: "El cliente aceptó el aviso de privacidad y está cargando sus documentos.",
    next: "Que el cliente cargue todos los documentos obligatorios y los envíe.",
    who: "Cliente",
  },
  DOCUMENTS_RECEIVED: {
    meaning: "El cliente envió su documentación.",
    next: "Revisar cada documento: aceptarlo, devolverlo con el motivo o marcarlo como \"No aplica\".",
    who: "Revisor documental",
  },
  UNDER_REVIEW: {
    meaning: "La documentación se está revisando.",
    next: "Terminar de revisar los documentos pendientes.",
    who: "Revisor documental",
  },
  CORRECTIONS_REQUESTED: {
    meaning: "Hay documentos devueltos, rechazados o nuevos documentos obligatorios sin cargar.",
    next: "Que el cliente cargue las correcciones desde su liga (ve el motivo de cada una); después, revisarlas.",
    who: "Cliente, luego revisor",
  },
  DOCUMENTS_APPROVED: {
    meaning: "Todos los documentos obligatorios están aceptados.",
    next: "Firmar la recepción de documentos.",
    who: "Administrador o director",
  },
  RECEPTION_SIGNED: {
    meaning: "La recepción de documentos está firmada.",
    next: "Capturar los datos del contrato y generarlo para firma.",
    who: "Asesor (datos) y administrador o director (generar)",
  },
  CONTRACT_PREPARATION: {
    meaning: "Se está preparando el contrato (hay un borrador o se corrigieron datos).",
    next: "Completar los datos faltantes y generar el contrato para firma.",
    who: "Asesor y administrador o director",
  },
  READY_FOR_SIGNATURE: {
    meaning: "El contrato se envió a firma.",
    next: "Que cada firmante del cliente firme con su liga personal y que la intermediaria firme desde el sistema.",
    who: "Cliente e intermediaria",
  },
  CONTRACT_SIGNED: {
    meaning: "El contrato quedó firmado por todas las partes, con su constancia de firmas.",
    next: "Entregar un tanto firmado al cliente y decidir sobre el inmueble.",
    who: "Asesor (entrega) y administrador o director (decisión)",
  },
  PROPERTY_ACCEPTED: {
    meaning: "El inmueble fue aceptado.",
    next: "Completar las tareas pendientes del seguimiento posterior a la firma.",
    who: "Asesor",
  },
  PROPERTY_REJECTED: {
    meaning: "El inmueble fue rechazado.",
    next: "Nada más en este expediente.",
    who: "—",
  },
  CLOSED: { meaning: "El expediente está cerrado.", next: "Nada más en este expediente.", who: "—" },
};
