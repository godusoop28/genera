import { apiClient } from "@/lib/api/client";

// CRM (Módulo 3): leads, dashboard, ruteo de asesores y asistente de WhatsApp.
// Contrato del backend: leads.web.InternalLeadController y
// assistant.web.InternalConversationController.

export type LeadStage = "NEW" | "CONTACTED" | "APPOINTMENT" | "VISITED" | "NEGOTIATION" | "WON" | "LOST";
export type LeadSource = "WHATSAPP_BOT" | "PORTAL" | "CAMPAIGN" | "WEBSITE" | "REFERRAL" | "WALK_IN" | "PHONE";
export type LeadInterest = "BUY" | "RENT" | "SELL" | "LEASE_OUT" | "INVEST" | "OTHER";
export type AssignmentMethod = "PROPERTY_OWNER" | "ROULETTE" | "MANUAL";
export type LeadGroup = "ALL" | "UNASSIGNED" | "NEW" | "OPEN" | "WON" | "LOST";
export type ActivityKind = "CREATED" | "ASSIGNED" | "REASSIGNED" | "STAGE" | "NOTE" | "BOT" | "NOTICE" | "FOLLOW_UP";

export interface LeadProperty {
  online21Id: number | null;
  key: string | null;
  title: string | null;
}

export interface LeadItem {
  id: string;
  name: string;
  phone: string | null;
  email: string | null;
  source: LeadSource;
  sourceDetail: string | null;
  interest: LeadInterest;
  stage: LeadStage;
  property: LeadProperty | null;
  advisor: { id: string; name: string } | null;
  assignmentMethod: AssignmentMethod | null;
  summary: string | null;
  budget: number | null;
  lostReason: string | null;
  createdAt: string;
  lastActivityAt: string;
  conversationId: string | null;
}

export interface LeadActivity {
  id: string;
  kind: ActivityKind;
  text: string;
  actorName: string | null;
  createdAt: string;
}

export interface LeadDetail {
  lead: LeadItem;
  activities: LeadActivity[];
  online21SyncStatus: string;
  advisorNoticeStatus: string;
}

export interface LeadPage {
  items: LeadItem[];
  total: number;
  page: number;
  totalPages: number;
  counts: Record<LeadGroup, number>;
}

export interface LeadQuery {
  q?: string;
  group?: LeadGroup;
  advisorId?: string;
  source?: LeadSource | "";
  from?: string;
  page?: number;
  size?: number;
}

function query(params: object): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== "") search.set(key, String(value));
  }
  const text = search.toString();
  return text ? `?${text}` : "";
}

export function listLeads(params: LeadQuery) {
  return apiClient.get<LeadPage>(`/internal/leads${query(params)}`);
}

export function getLead(id: string) {
  return apiClient.get<LeadDetail>(`/internal/leads/${id}`);
}

export function changeLeadStage(id: string, stage: LeadStage, lostReason?: string) {
  return apiClient.post<LeadDetail>(`/internal/leads/${id}/stage`, { stage, lostReason });
}

export function assignLead(id: string, advisorId: string) {
  return apiClient.post<LeadDetail>(`/internal/leads/${id}/assign`, { advisorId });
}

export function addLeadNote(id: string, text: string) {
  return apiClient.post<LeadDetail>(`/internal/leads/${id}/notes`, { text });
}

export interface CreateLeadInput {
  name: string;
  phone?: string;
  email?: string;
  source: LeadSource;
  sourceDetail?: string;
  interest: LeadInterest;
  propertyReference?: string;
  summary?: string;
  budget?: number;
  advisorId?: string;
  autoAssign: boolean;
}

export function createLead(input: CreateLeadInput) {
  return apiClient.post<LeadDetail>("/internal/leads", input);
}

export interface CrmDashboard {
  from: string | null;
  total: number;
  assigned: number;
  unassigned: number;
  open: number;
  won: number;
  closed: number;
  staleNew: number;
  byStage: Record<LeadStage, number>;
  bySource: Partial<Record<LeadSource, number>>;
  byAdvisor: { advisorId: string; name: string; leads: number; won: number }[];
  topProperties: { online21Id: number | null; key: string | null; title: string | null; leads: number }[];
  recent: LeadItem[];
}

export function getCrmDashboard(from?: string) {
  return apiClient.get<CrmDashboard>(`/internal/crm/dashboard${query({ from })}`);
}

export interface AdvisorRouting {
  userId: string;
  name: string;
  email: string;
  roleCode: string;
  receivesLeads: boolean;
  weight: number;
  dailyLimit: number | null;
  shiftStart: string | null;
  shiftEnd: string | null;
  online21UserId: number | null;
  whatsappPhone: string | null;
  assignedToday: number;
  openLeads: number;
  wonLeads: number;
}

export type AdvisorRoutingInput = Pick<AdvisorRouting, "receivesLeads" | "weight" | "dailyLimit" | "shiftStart" | "shiftEnd" | "online21UserId" | "whatsappPhone">;

export function listAdvisors() {
  return apiClient.get<AdvisorRouting[]>("/internal/crm/advisors");
}

export function updateAdvisor(userId: string, input: AdvisorRoutingInput) {
  return apiClient.put<AdvisorRouting>(`/internal/crm/advisors/${userId}`, input);
}

// ---------------------------------------------------------------------------
// Asistente de WhatsApp
// ---------------------------------------------------------------------------

export type ConversationControl = "AI" | "HUMAN" | "PAUSED";

export interface ConversationItem {
  id: string;
  contactName: string | null;
  phone: string | null;
  test: boolean;
  control: ConversationControl;
  intent: string;
  handoffState: string;
  leadId: string | null;
  needsAttention: string | null;
  lastMessage: string | null;
  lastMessageRole: string | null;
  lastMessageAt: string | null;
  openEscalations: number;
}

export interface ConversationMessage {
  id: string;
  role: "CLIENT" | "BOT" | "TEAM" | "SYSTEM";
  text: string;
  status: string;
  error: string | null;
  createdAt: string;
}

export interface ConversationDetail {
  conversation: ConversationItem;
  summary: string | null;
  messages: ConversationMessage[];
  escalations: { id: string; type: string; reason: string; status: string; createdAt: string }[];
}

export interface AssistantStatus {
  mode: "OFF" | "TEST_ONLY" | "ON";
  aiConfigured: boolean;
  model: string | null;
  whatsappConfigured: boolean;
  webhookConfigured: boolean;
  inventoryAvailable: boolean;
}

export function getAssistantStatus() {
  return apiClient.get<AssistantStatus>("/internal/assistant/status");
}

export function listConversations() {
  return apiClient.get<ConversationItem[]>("/internal/conversations");
}

export function getConversation(id: string) {
  return apiClient.get<ConversationDetail>(`/internal/conversations/${id}`);
}

export function setConversationControl(id: string, control: ConversationControl) {
  return apiClient.post<ConversationDetail>(`/internal/conversations/${id}/control`, { control });
}

export function replyToConversation(id: string, text: string) {
  return apiClient.post<ConversationDetail>(`/internal/conversations/${id}/reply`, { text });
}

export function resolveEscalation(escalationId: string) {
  return apiClient.post<void>(`/internal/conversations/escalations/${escalationId}/resolve`);
}

export function simulateMessage(text: string) {
  return apiClient.post<{ conversationId?: string }>("/internal/assistant/simulator", { text });
}

export function resetSimulation() {
  return apiClient.delete<void>("/internal/assistant/simulator");
}
