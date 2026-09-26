// Portal público del cliente y liga personal de firma: sin JWT, el token en la
// URL es la credencial (ver backend AGENTS §90-97). No requiere setAccessToken.
import { apiClient, uploadFiles } from "@/lib/api/client";
import type {
  BackendCivilStatus,
  BackendMaritalRegime,
  DocumentVersionResponse,
  PrivacyConsentResponse,
  PrivacyNoticeResponse,
  PublicClientDataResponse,
  PublicDocumentResponse,
  PublicExpedienteResponse,
  PublicParticipantResponse,
  SigningViewResponse,
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
  return apiClient.get<PublicClientDataResponse>(`/public/expedientes/${token}/client-data`);
}

export function updateClientData(token: string, data: Partial<PublicClientDataResponse>) {
  return apiClient.put<PublicClientDataResponse>(`/public/expedientes/${token}/client-data`, data);
}

export function listPublicParticipants(token: string) {
  return apiClient.get<PublicParticipantResponse[]>(`/public/expedientes/${token}/participants`);
}

export function declareCivilStatus(
  token: string,
  participantId: string,
  civilStatus: BackendCivilStatus,
  maritalRegime: BackendMaritalRegime | null,
) {
  return apiClient.put<PublicParticipantResponse>(`/public/expedientes/${token}/participants/${participantId}/civil-status`, {
    civilStatus,
    maritalRegime,
  });
}

export function submitDocuments(token: string) {
  return apiClient.post<SubmitDocumentsResponse>(`/public/expedientes/${token}/submit`);
}

export function listPublicDocuments(token: string) {
  return apiClient.get<PublicDocumentResponse[]>(`/public/expedientes/${token}/documents`);
}

export function uploadPublicDocumentVersion(token: string, documentId: string, files: File[]) {
  return uploadFiles<DocumentVersionResponse>(`/public/expedientes/${token}/documents/${documentId}/versions`, "files", files);
}

// --- Firma del contrato (liga personal de cada firmante) ---

export function getSigningView(token: string) {
  return apiClient.get<SigningViewResponse>(`/public/signatures/${token}`);
}

export function signContract(
  token: string,
  request: { typedName: string; signatureImageBase64: string; documentSha256: string; accepted: boolean },
) {
  return apiClient.post<{ signed: boolean; signedAt: string; documentSha256: string }>(`/public/signatures/${token}`, request);
}
