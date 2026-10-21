import { apiClient } from "@/lib/api/client";

// CRM (Módulo 3): leads, dashboard, ruteo de asesores y asistente de WhatsApp.
// Contrato del backend: leads.web.InternalLeadController y
// assistant.web.InternalConversationController.

export type LeadStage = "NEW" | "CONTACTED" | "APPOINTMENT" | "VISITED" | "NEGOTIATION" | "WON" | "LOST";
export type LeadSource = "WHATSAPP_BOT" | "PORTAL" | "CAMPAIGN" | "WEBSITE" | "REFERRAL" | "WALK_IN" | "PHONE";
export type LeadInterest = "BUY" | "RENT" | "SELL" | "LEASE_OUT" | "INVEST" | "OTHER";
export type AssignmentMethod = "PROPERTY_OWNER" | "CONTINUITY" | "ROULETTE" | "GUARD" | "MANUAL";
export type AssignmentStatus = "ASSIGNED" | "PENDING_OWNER" | "PENDING_SCHEDULE" | "UNASSIGNED";
export type LeadGroup = "ALL" | "UNASSIGNED" | "PENDING" | "NEW" | "OPEN" | "WON" | "LOST";
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
  assignmentStatus: AssignmentStatus;
  assignmentRule: string | null;
  pendingReason: string | null;
  nextShiftAt: string | null;
  /** Asesor responsable de la PROPIEDAD (21Online), distinto del que atiende el lead. */
  propertyAdvisor: { id: string; name: string } | null;
  coordinationNote: string | null;
}

export interface LeadAssignmentEntry {
  id: string;
  outcome: "ASSIGNED" | "PENDING" | "KEPT" | "CONFLICT" | "REASSIGNED";
  advisor: { id: string; name: string } | null;
  reason: AssignmentMethod | null;
  rule: string;
  shiftId: string | null;
  roundMode: string | null;
  detail: string | null;
  actorName: string | null;
  createdAt: string;
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
  assignments: LeadAssignmentEntry[];
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

export function assignLead(id: string, advisorId: string, exceptionReason?: string) {
  return apiClient.post<LeadDetail>(`/internal/leads/${id}/assign`, { advisorId, exceptionReason });
}

export function resendLeadNotice(id: string) {
  return apiClient.post<LeadDetail>(`/internal/leads/${id}/resend-notice`);
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
  exceptionReason?: string;
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
  pendingAssignment: number;
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

export type AdvisorStatus = "AVAILABLE" | "OFF_SHIFT" | "ABSENT" | "PAUSED" | "LIMIT_REACHED" | "NO_CALENDAR";

export interface AdvisorRouting {
  userId: string;
  name: string;
  email: string;
  roleCode: string;
  receivesLeads: boolean;
  weight: number;
  dailyLimit: number | null;
  online21UserId: number | null;
  whatsappPhone: string | null;
  status: AdvisorStatus;
  statusDetail: string;
  currentShiftEnd: string | null;
  currentShiftKind: "REGULAR" | "GUARD" | null;
  nextShift: string | null;
  needsCalendar: boolean;
  legacyShift: string | null;
  assignedToday: number;
  openLeads: number;
  wonLeads: number;
}

export type AdvisorRoutingInput = Pick<AdvisorRouting, "receivesLeads" | "weight" | "dailyLimit" | "online21UserId" | "whatsappPhone">;

export function listAdvisors() {
  return apiClient.get<AdvisorRouting[]>("/internal/crm/advisors");
}

export function updateAdvisor(userId: string, input: AdvisorRoutingInput) {
  return apiClient.put<AdvisorRouting>(`/internal/crm/advisors/${userId}`, input);
}

export type RoundMode = "EQUAL" | "WEIGHTED";
export type OwnerOffShiftPolicy = "RESPECT_OWNER" | "ROUTE_TO_GUARD";

export interface AssignmentSettings {
  roundMode: RoundMode;
  ownerOffShiftPolicy: OwnerOffShiftPolicy;
  updatedByName: string | null;
  pendingLeads: number;
  nextCoverage: string | null;
  noUpcomingCoverage: boolean;
  timezone: string;
}

export function getAssignmentSettings() {
  return apiClient.get<AssignmentSettings>("/internal/crm/assignment-settings");
}

export function updateAssignmentSettings(roundMode: RoundMode, ownerOffShiftPolicy: OwnerOffShiftPolicy) {
  return apiClient.put<AssignmentSettings>("/internal/crm/assignment-settings", { roundMode, ownerOffShiftPolicy });
}

// ---------------------------------------------------------------------------
// Calendario de turnos (todas las fechas y horas son de Ciudad de México,
// como texto "YYYY-MM-DDTHH:mm:ss" sin zona: no dependen del dispositivo).
// ---------------------------------------------------------------------------

export type ShiftKind = "REGULAR" | "GUARD";
export type ShiftRecurrence = "ONCE" | "WEEKLY";
export type AbsenceKind = "VACATION" | "PERMISSION" | "REST" | "UNAVAILABLE";
export type EditScope = "OCCURRENCE" | "SERIES";

export interface LocalRange {
  start: string;
  end: string;
}

export interface ShiftOccurrence {
  shiftId: string;
  advisorId: string;
  advisorName: string;
  kind: ShiftKind;
  assignable: boolean;
  recurrence: ShiftRecurrence;
  date: string;
  start: string;
  end: string;
  notes: string | null;
  absentParts: LocalRange[];
  fullyAbsent: boolean;
  days: number[];
  seriesStart: string;
  seriesEnd: string | null;
  createdByName: string | null;
  updatedByName: string | null;
}

export interface CalendarAbsence {
  id: string;
  advisorId: string;
  advisorName: string;
  kind: AbsenceKind;
  start: string;
  end: string;
  notes: string | null;
  createdByName: string | null;
}

export interface PersonNow {
  advisorId: string;
  name: string;
  kind: ShiftKind;
  assignable: boolean;
  start: string;
  end: string;
}

export interface CalendarAdvisor {
  userId: string;
  name: string;
  roleCode: string;
  status: AdvisorStatus;
  statusDetail: string;
  nextShift: string | null;
  needsCalendar: boolean;
  legacyShift: string | null;
}

export interface CalendarView {
  timezone: string;
  today: string;
  now: string;
  from: string;
  to: string;
  canManage: boolean;
  canEditOwn: boolean;
  advisors: CalendarAdvisor[];
  occurrences: ShiftOccurrence[];
  absences: CalendarAbsence[];
  gaps: LocalRange[];
  onShiftNow: PersonNow[];
  nextUp: PersonNow[];
  nextCoverage: string | null;
}

export interface ShiftInput {
  advisorId: string;
  kind: ShiftKind;
  recurrence: ShiftRecurrence;
  days: number[];
  startDate: string;
  endDate: string | null;
  startTime: string;
  endTime: string;
  assignable: boolean;
  notes: string | null;
}

export interface AbsenceInput {
  advisorId: string;
  kind: AbsenceKind;
  start: string;
  end: string;
  notes: string | null;
}

export interface CalendarAuditEntry {
  id: string;
  entity: string;
  entityId: string | null;
  advisorName: string | null;
  action: string;
  detail: string;
  actorName: string | null;
  createdAt: string;
}

export function getCalendar(from: string, to: string) {
  return apiClient.get<CalendarView>(`/internal/crm/calendar${query({ from, to })}`);
}

export function getCalendarAudit() {
  return apiClient.get<CalendarAuditEntry[]>("/internal/crm/calendar/audit");
}

export function createShift(input: ShiftInput) {
  return apiClient.post<{ id: string }>("/internal/crm/calendar/shifts", input);
}

export function updateShift(id: string, input: ShiftInput, scope: EditScope, date: string) {
  return apiClient.put<{ id: string }>(`/internal/crm/calendar/shifts/${id}${query({ scope, date })}`, input);
}

export function deleteShift(id: string, scope: EditScope, date: string) {
  return apiClient.delete<void>(`/internal/crm/calendar/shifts/${id}${query({ scope, date })}`);
}

export function copyShifts(sourceFrom: string, days: number, targetFrom: string, advisorId?: string) {
  return apiClient.post<{ created: number; skipped: number; skippedDetails: string[] }>("/internal/crm/calendar/copy", { sourceFrom, days, targetFrom, advisorId });
}

export function createAbsence(input: AbsenceInput) {
  return apiClient.post<{ id: string }>("/internal/crm/calendar/absences", input);
}

export function updateAbsence(id: string, input: AbsenceInput) {
  return apiClient.put<void>(`/internal/crm/calendar/absences/${id}`, input);
}

export function deleteAbsence(id: string) {
  return apiClient.delete<void>(`/internal/crm/calendar/absences/${id}`);
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

// --- Asesores ↔ usuarios de 21Online --------------------------------------------

export interface Online21User {
  id: number;
  name: string;
  email: string | null;
  position: string | null;
  mobilePhone: string | null;
  whatsapp: string | null;
  /** Con quién de Genera está vinculado (null = nadie). */
  linkedUserId: string | null;
  linkedUserName: string | null;
}

export interface Online21SyncResult {
  configured: boolean;
  /** Vínculos nuevos hechos por correo ("Ana → Ana López"). */
  linked: string[];
  /** Personal vinculado a un usuario que ya no aparece en 21Online. */
  brokenLinks: string[];
  /** Asesores sin usuario de 21Online. */
  unmatched: string[];
  users: Online21User[];
}

export function listOnline21Users() {
  return apiClient.get<Online21User[]>("/internal/crm/advisors/online21-users");
}

/** Vincula por correo a quien aún no lo esté (también corre solo cada 6 horas). */
export function syncAdvisorsWithOnline21() {
  return apiClient.post<Online21SyncResult>("/internal/crm/advisors/online21-sync");
}
