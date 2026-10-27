import { apiClient } from "@/lib/api/client";

// Control de Cambios #28: encuestas de satisfacción NPS por punto de contacto.
// Contrato del backend: surveys.web.InternalSurveyController y PublicSurveyController.

export type Touchpoint =
  | "REGISTRATION"
  | "DOCUMENT_PREP"
  | "DOCUMENTS_UPLOADED"
  | "PRIVACY_SIGNED"
  | "AI_VALIDATION"
  | "DOCUMENT_UPDATE"
  | "CONTRACT_SENT"
  | "CONTRACT_SIGNED"
  | "NOTARY_SENT"
  | "CLOSING"
  | "FIRST_VISIT"
  | "PROPOSAL"
  | "NEGOTIATION"
  | "IN_PERSON_SIGNING"
  | "KEY_HANDOVER"
  | "ISSUE_RESOLVED"
  | "POST_SALE";
export type SurveyChannel = "WHATSAPP" | "EMAIL" | "SYSTEM" | "ADVISOR_LINK" | "QR";
export type SurveyStatus = "SCHEDULED" | "SENT" | "ANSWERED" | "EXPIRED" | "SKIPPED" | "FAILED";
export type NpsCategory = "DETRACTOR" | "PASSIVE" | "PROMOTER";

export interface PublicSurvey {
  token: string | null;
  touchpointNumber: number;
  touchpointLabel: string;
  moment: string;
  introMessage: string | null;
  askComment: boolean;
  clientFirstName: string | null;
  status: string;
  score: number | null;
  category: NpsCategory | null;
  followUpQuestion: string | null;
  comment: string | null;
}

export interface SurveyItem {
  id: string;
  token: string | null;
  link: string | null;
  touchpoint: Touchpoint;
  touchpointNumber: number;
  touchpointLabel: string;
  channel: SurveyChannel;
  status: SurveyStatus;
  expedienteId: string | null;
  expedienteFolio: string | null;
  leadId: string | null;
  clientName: string | null;
  advisorName: string | null;
  sendAt: string;
  sentAt: string | null;
  remindersSent: number;
  deliveryDetail: string | null;
  score: number | null;
  category: NpsCategory | null;
  comment: string | null;
  answeredAt: string | null;
  createdByName: string | null;
  createdAt: string;
  /** El cliente ya recibió otra encuesta hace poco (regla anti-saturación). */
  recentlySurveyed: boolean;
}

export interface SurveyHistory {
  canSeeScores: boolean;
  surveys: SurveyItem[];
}

export interface TouchpointConfig {
  touchpoint: Touchpoint;
  number: number;
  label: string;
  systemTriggered: boolean;
  active: boolean;
  channel: SurveyChannel;
  delayMinutes: number;
  askComment: boolean;
  introMessage: string | null;
  staticLink: string;
  updatedByName: string | null;
  updatedAt: string;
}

export interface SurveySettings {
  minDaysBetween: number;
  maxPerPeriod: number;
  periodDays: number;
  reminderEnabled: boolean;
  reminderAfterHours: number;
  maxReminders: number;
  expireDays: number;
  advisorsSeeOwn: boolean;
  updatedByName: string | null;
}

export interface SurveyConfig {
  touchpoints: TouchpointConfig[];
  settings: SurveySettings;
}

export interface NpsScore {
  nps: number;
  responses: number;
  promoters: number;
  passives: number;
  detractors: number;
}

export interface NpsDashboard {
  from: string;
  to: string;
  global: NpsScore;
  sent: number;
  responseRate: number;
  byTouchpoint: { touchpoint: Touchpoint; number: number; label: string; systemTriggered: boolean; score: NpsScore; sent: number }[];
  byAdvisor: { advisorId: string | null; name: string; score: NpsScore }[];
  trendGranularity: "WEEK" | "MONTH";
  trend: { period: string; label: string; score: NpsScore }[];
  comments: {
    surveyId: string;
    score: number;
    category: NpsCategory;
    comment: string;
    touchpoint: Touchpoint;
    touchpointLabel: string;
    advisorName: string | null;
    clientName: string | null;
    expedienteId: string | null;
    expedienteFolio: string | null;
    leadId: string | null;
    channel: SurveyChannel;
    answeredAt: string;
  }[];
  byChannel: Record<SurveyChannel, number>;
}

function query(params: object): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== "") search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}

// --- Público (sin sesión) -----------------------------------------------------

export function getPublicSurvey(token: string) {
  return apiClient.get<PublicSurvey>(`/public/surveys/${token}`);
}

export function answerSurvey(token: string, score: number) {
  return apiClient.post<PublicSurvey>(`/public/surveys/${token}/answer`, { score });
}

export function commentSurvey(token: string, comment: string) {
  return apiClient.post<PublicSurvey>(`/public/surveys/${token}/comment`, { comment });
}

export function getStaticSurvey(touchpoint: string) {
  return apiClient.get<PublicSurvey>(`/public/surveys/touchpoints/${touchpoint}`);
}

export function answerStaticSurvey(touchpoint: string, score: number, name?: string) {
  return apiClient.post<PublicSurvey>(`/public/surveys/touchpoints/${touchpoint}`, { score, name });
}

export function getPortalSurveys(portalToken: string) {
  return apiClient.get<PublicSurvey[]>(`/public/expedientes/${portalToken}/surveys`);
}

/** Punto de contacto 2: el cliente terminó de leer cómo preparar sus documentos. */
export function markDocumentPrepDone(portalToken: string) {
  return apiClient.post<PublicSurvey[]>(`/public/expedientes/${portalToken}/survey-touchpoints/document-prep`);
}

// --- Personal ------------------------------------------------------------------

export function getSurveyConfig() {
  return apiClient.get<SurveyConfig>("/internal/surveys/config");
}

export function updateTouchpointConfig(touchpoint: Touchpoint, input: { active: boolean; channel: SurveyChannel; delayMinutes: number; askComment: boolean; introMessage?: string }) {
  return apiClient.put<TouchpointConfig>(`/internal/surveys/config/touchpoints/${touchpoint}`, input);
}

export function updateSurveySettings(input: Omit<SurveySettings, "updatedByName">) {
  return apiClient.put<SurveySettings>("/internal/surveys/config/settings", input);
}

export function getExpedienteSurveys(expedienteId: string) {
  return apiClient.get<SurveyHistory>(`/internal/expedientes/${expedienteId}/surveys`);
}

export function getLeadSurveys(leadId: string) {
  return apiClient.get<SurveyHistory>(`/internal/surveys${query({ leadId })}`);
}

export interface CreateSurveyInput {
  touchpoint: Touchpoint;
  channel: SurveyChannel;
  expedienteId?: string;
  leadId?: string;
  clientName?: string;
  clientPhone?: string;
  clientEmail?: string;
}

export function createSurvey(input: CreateSurveyInput) {
  return apiClient.post<SurveyItem>("/internal/surveys", input);
}

export function getNpsDashboard(params: { from?: string; to?: string; advisorId?: string; touchpoint?: Touchpoint | ""; category?: NpsCategory | "" }) {
  return apiClient.get<NpsDashboard>(`/internal/surveys/dashboard${query(params)}`);
}
