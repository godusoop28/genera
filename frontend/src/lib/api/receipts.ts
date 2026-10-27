import { apiClient, uploadFiles } from "@/lib/api/client";

// Control de Cambios #31: acuse de recepción de documentos firmado por el
// representante legal. Contrato del backend: receipts.web.ReceiptController.

export type ReceiptStatus = "DRAFT" | "PENDING_SIGNATURE" | "SIGNED" | "SENT";
export type ReceiptSignatureMethod = "ELECTRONIC_SIMPLE" | "UPLOADED";

export interface ReceivedItem {
  name: string;
  participant: string | null;
  versionNumber: number;
  receivedAt: string | null;
  current: boolean;
  status: string;
}

export interface Receipt {
  id: string;
  sequenceNumber: number;
  number: string;
  status: ReceiptStatus;
  clientName: string;
  clientEmail: string | null;
  responsibleEntity: string;
  representativeName: string | null;
  textsApproved: boolean;
  privacyNoticeVersion: number | null;
  items: ReceivedItem[];
  signatureMethod: ReceiptSignatureMethod | null;
  signedByName: string | null;
  signedAt: string | null;
  sentTo: string | null;
  sentChannel: string | null;
  sentAt: string | null;
  sentByName: string | null;
  sendDetail: string | null;
  requestedSignatureAt: string | null;
  requestedByName: string | null;
  generatedByName: string;
  generatedAt: string;
}

export interface ReceiptPreview {
  items: ReceivedItem[];
  newSinceLast: number;
  hasReceipts: boolean;
  textsApproved: boolean;
  responsibleEntity: string;
  representativeName: string | null;
  clientEmail: string | null;
}

export interface ReceiptSettings {
  responsibleEntity: string;
  representativeName: string | null;
  representativeTitle: string | null;
  responsibilityText: string;
  privacyText: string;
  approved: boolean;
  approvedByName: string | null;
  approvedAt: string | null;
  updatedByName: string | null;
}

export function listReceipts(expedienteId: string) {
  return apiClient.get<Receipt[]>(`/internal/expedientes/${expedienteId}/receipts`);
}

export function getReceiptPreview(expedienteId: string) {
  return apiClient.get<ReceiptPreview>(`/internal/expedientes/${expedienteId}/receipts/preview`);
}

export function generateReceipt(expedienteId: string) {
  return apiClient.post<Receipt>(`/internal/expedientes/${expedienteId}/receipts`);
}

export function requestReceiptSignature(receiptId: string) {
  return apiClient.post<Receipt>(`/internal/receipts/${receiptId}/request-signature`);
}

export function signReceipt(receiptId: string, password: string, signaturePng: string, title?: string) {
  return apiClient.post<Receipt>(`/internal/receipts/${receiptId}/sign`, { password, signaturePng, title });
}

export function uploadSignedReceipt(receiptId: string, file: File) {
  return uploadFiles<Receipt>(`/internal/receipts/${receiptId}/signed-file`, "file", [file]);
}

export function sendReceipt(receiptId: string, email?: string) {
  return apiClient.post<Receipt>(`/internal/receipts/${receiptId}/send`, { email });
}

export function getReceiptDownload(receiptId: string, which: "draft" | "signed") {
  return apiClient.get<{ url: string }>(`/internal/receipts/${receiptId}/download?which=${which}`);
}

export function getReceiptSettings() {
  return apiClient.get<ReceiptSettings>("/internal/receipts/settings");
}

export function updateReceiptSettings(input: { responsibleEntity: string; representativeName?: string; representativeTitle?: string; responsibilityText: string; privacyText: string }) {
  return apiClient.put<ReceiptSettings>("/internal/receipts/settings", input);
}

export function approveReceiptTexts(approved: boolean) {
  return apiClient.post<ReceiptSettings>("/internal/receipts/settings/approval", { approved });
}
