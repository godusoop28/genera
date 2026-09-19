// Portal público del cliente: sin JWT, el token en la URL es la credencial
// (ver backend AGENTS §90-97). No requiere setAccessToken.
import { apiClient, uploadFiles } from "@/lib/api/client";
import type {
  DocumentResponse,
  DocumentVersionResponse,
  ManualClientDataResponse,
  PrivacyConsentResponse,
  PrivacyNoticeResponse,
  PublicExpedienteResponse,
  SubmitDocumentsResponse,
} from "@/lib/api/types";

export function getPublicExpediente(token: string) {
  return apiClient.get<PublicExpedienteResponse>(`/public/expedientes/${token}`);
}

export function getPrivacyNotice(token: string) {
  return apiClient.get<PrivacyNoticeResponse>(`/public/expedientes/${token}/privacy-notice`);
}

export function recordPrivacyConsent(
  token: string,
  mainConsent: boolean,
  secondaryConsent: boolean,
  signatureBase64Png?: string,
) {
  return apiClient.post<PrivacyConsentResponse>(`/public/expedientes/${token}/privacy-consent`, {
    mainConsent,
    secondaryConsent,
    signatureBase64Png,
  });
}

export function getClientData(token: string) {
  return apiClient.get<ManualClientDataResponse>(`/public/expedientes/${token}/client-data`);
}

export function updateClientData(token: string, data: Partial<ManualClientDataResponse>) {
  return apiClient.put<ManualClientDataResponse>(`/public/expedientes/${token}/client-data`, data);
}

export function submitDocuments(token: string) {
  return apiClient.post<SubmitDocumentsResponse>(`/public/expedientes/${token}/submit`);
}

export function listPublicDocuments(token: string) {
  return apiClient.get<DocumentResponse[]>(`/public/expedientes/${token}/documents`);
}

export function uploadPublicDocumentVersion(token: string, documentId: string, files: File[]) {
  return uploadFiles<DocumentVersionResponse>(`/public/expedientes/${token}/documents/${documentId}/versions`, "files", files);
}
