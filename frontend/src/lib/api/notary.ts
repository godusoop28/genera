import { apiClient } from "@/lib/api/client";

/** Control de Cambios #23: documentos por etapa y envíos a notaría (inicial y de cierre). */

export type NotarySubmissionKind = "INITIAL" | "CLOSING";

export interface NotaryVersion {
  id: string;
  versionNumber: number;
  uploadedAt: string;
  current: boolean;
  hasPdf: boolean;
}

export interface NotaryStageDocument {
  documentId: string;
  type: string;
  name: string;
  participantId: string | null;
  required: boolean;
  status: string;
  exception: boolean;
  markedPending: boolean;
  versions: NotaryVersion[];
  /** Debe entregarse actualizado, vigente y al corriente para el cierre. */
  closingRequired: boolean;
  /** La versión vigente está confirmada como vigente y al corriente. */
  closingValid: boolean;
  closingCheckValid: boolean | null;
  closingCheckedVersion: number | null;
  closingCheckNote: string | null;
  closingCheckedByName: string | null;
  closingCheckedAt: string | null;
}

export interface NotarySubmissionItem {
  documentId: string;
  documentName: string;
  versionId: string;
  versionNumber: number;
}

export interface NotarySubmission {
  id: string;
  kind: NotarySubmissionKind;
  sentAt: string;
  sentByName: string | null;
  recipientEmail: string | null;
  note: string | null;
  warnings: string | null;
  items: NotarySubmissionItem[];
}

export interface NotaryOverview {
  closingActive: boolean;
  offerAcceptedAt: string | null;
  offerAcceptedByName: string | null;
  offerNote: string | null;
  documents: NotaryStageDocument[];
  submissions: NotarySubmission[];
}

export function getNotaryOverview(expedienteId: string) {
  return apiClient.get<NotaryOverview>(`/internal/expedientes/${expedienteId}/notary`);
}

export function registerOfferAccepted(expedienteId: string, note: string) {
  return apiClient.post<NotaryOverview>(`/internal/expedientes/${expedienteId}/notary/offer-accepted`, { note });
}

export function clearOfferAccepted(expedienteId: string) {
  return apiClient.delete<NotaryOverview>(`/internal/expedientes/${expedienteId}/notary/offer-accepted`);
}

export function setClosingCheck(documentId: string, valid: boolean, note: string) {
  return apiClient.put<void>(`/internal/documents/${documentId}/closing-check`, { valid, note });
}

export function sendToNotary(
  expedienteId: string,
  request: {
    kind: NotarySubmissionKind;
    documents: { documentId: string; versionId: string }[];
    recipientEmail?: string;
    note?: string;
    confirmWarnings?: boolean;
  },
) {
  return apiClient.post<NotarySubmission>(`/internal/expedientes/${expedienteId}/notary/submissions`, request);
}
