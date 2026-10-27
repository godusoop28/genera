import { apiClient } from "@/lib/api/client";

// CRM (Módulo 3): leads, actividades y agenda (CC#30), catálogo de fuentes
// (CC#29), dashboard, ruteo de asesores y asistente de WhatsApp.
// Contrato del backend: leads.web.InternalLeadController y
// assistant.web.InternalConversationController.

export type LeadStage = "NEW" | "CONTACTED" | "QUALIFIED" | "APPOINTMENT" | "VISITED" | "PROPOSAL" | "NEGOTIATION" | "OFFER_ACCEPTED" | "WON" | "LOST";
/** Canal general; la fuente precisa es sourceCode (catálogo del CC#29). */
export type LeadSource =
  | "WHATSAPP_BOT"
  | "WHATSAPP"
  | "PORTAL"
  | "SOCIAL"
  | "CAMPAIGN"
  | "WEBSITE"
  | "OUTDOOR"
  | "REFERRAL"
  | "WALK_IN"
  | "EVENT"
  | "PHONE"
  | "REACTIVATION"
  | "OTHER";
export type LeadInterest = "BUY" | "RENT" | "SELL" | "LEASE_OUT" | "INVEST" | "OTHER";
export type LeadTemperature = "COLD" | "WARM" | "HOT";
export type LostReason = "PRICE" | "ZONE" | "TIMING" | "COMPETITION" | "NO_RESPONSE" | "OTHER";
export type AssignmentMethod = "PROPERTY_OWNER" | "CONTINUITY" | "ROULETTE" | "GUARD" | "MANUAL";
export type AssignmentStatus = "ASSIGNED" | "PENDING_OWNER" | "PENDING_SCHEDULE" | "UNASSIGNED";
export type LeadGroup = "ALL" | "UNASSIGNED" | "PENDING" | "NEW" | "OPEN" | "STALE" | "WON" | "LOST";
export type ActivityKind = "CREATED" | "ASSIGNED" | "REASSIGNED" | "STAGE" | "NOTE" | "BOT" | "NOTICE" | "FOLLOW_UP" | "TASK" | "PROPERTY_SHARED" | "EXPEDIENTE" | "EDIT";
export type SourceCategory = "PORTALS" | "SOCIAL" | "SEARCH" | "OUTDOOR" | "REFERRALS" | "IN_PERSON" | "REACTIVATION" | "UNKNOWN";
export type TaskType = "CALL" | "WHATSAPP" | "EMAIL" | "VISIT" | "OFFICE_MEETING" | "VIDEO_CALL" | "PROPOSAL" | "OFFER_FOLLOW_UP" | "NOTE" | "OTHER";
export type TaskStatus = "SCHEDULED" | "DONE" | "CANCELLED";
export type TaskResult = "CONTACTED" | "NO_ANSWER" | "RESCHEDULED" | "REJECTED" | "INTERESTED" | "ADVANCED";

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
  /** Presupuesto máximo. */
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
  sourceCode: string | null;
  sourceLabel: string | null;
  sourceCategory: SourceCategory | null;
  firstContactOn: string | null;
  propertyCategoryId: string | null;
  propertySubtypeId: string | null;
  propertyTypeLabel: string | null;
  zone: string | null;
  budgetMin: number | null;
  desiredFeatures: string | null;
  temperature: LeadTemperature | null;
  closeProbability: number | null;
  expectedCloseOn: string | null;
  lostReasonCode: LostReason | null;
  stageEnteredAt: string | null;
  expediente: { id: string; folio: string } | null;
  nextTask: { id: string; type: TaskType; scheduledAt: string; overdue: boolean } | null;
  overdueTasks: number;
  /** Abierto y sin actividad en más de los días configurados. */
  stale: boolean;
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

export interface LeadTask {
  id: string;
  leadId: string;
  leadName: string | null;
  leadStage: LeadStage | null;
  type: TaskType;
  status: TaskStatus;
  title: string | null;
  scheduledAt: string | null;
  durationMinutes: number | null;
  advisor: { id: string; name: string } | null;
  propertyKey: string | null;
  propertyTitle: string | null;
  notesBefore: string | null;
  reminderMinutes: number | null;
  result: TaskResult | null;
  actualMinutes: number | null;
  outcomeNotes: string | null;
  nextAction: string | null;
  nextContactAt: string | null;
  completedAt: string | null;
  overdue: boolean;
  createdByName: string | null;
}

export interface StageStep {
  stage: LeadStage;
  enteredAt: string;
  exitedAt: string | null;
  days: number;
  actorName: string | null;
}

export interface SharedProperty {
  id: string;
  online21Id: number | null;
  key: string | null;
  title: string | null;
  note: string | null;
  actorName: string | null;
  createdAt: string;
}

export interface LeadDetail {
  lead: LeadItem;
  activities: LeadActivity[];
  assignments: LeadAssignmentEntry[];
  online21SyncStatus: string;
  advisorNoticeStatus: string;
  tasks: LeadTask[];
  stageHistory: StageStep[];
  sharedProperties: SharedProperty[];
  /** Etapa que sugieren las actividades registradas (null = ninguna). */
  suggestedStage: LeadStage | null;
  /** Expedientes con la misma clave de inmueble (al llegar a Oferta aceptada sin vínculo). */
  expedienteCandidates: { id: string; folio: string; ownerDisplayName: string; propertyKey: string | null }[];
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
  sourceCode?: string;
  temperature?: LeadTemperature | "";
  stage?: LeadStage | "";
  expedienteId?: string;
  from?: string;
  /** Número de 21Online de la propiedad por la que preguntaron. */
  propertyId?: number;
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

export function changeLeadStage(id: string, stage: LeadStage, lostReason?: string, lostReasonCode?: LostReason) {
  return apiClient.post<LeadDetail>(`/internal/leads/${id}/stage`, { stage, lostReason, lostReasonCode });
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

/** Control de Cambios #30: interés y estatus de la ficha. */
export interface LeadProfileInput {
  firstContactOn?: string;
  propertySubtypeId?: string;
  zone?: string;
  budgetMin?: number;
  budgetMax?: number;
  desiredFeatures?: string;
  temperature?: LeadTemperature;
  closeProbability?: number;
  expectedCloseOn?: string;
}

export interface CreateLeadInput {
  name: string;
  phone?: string;
  email?: string;
  source?: LeadSource;
  sourceCode: string;
  sourceDetail?: string;
  interest: LeadInterest;
  propertyReference?: string;
  summary?: string;
  budget?: number;
  advisorId?: string;
  exceptionReason?: string;
  autoAssign: boolean;
  profile?: LeadProfileInput;
}

export function createLead(input: CreateLeadInput) {
  return apiClient.post<LeadDetail>("/internal/leads", input);
}

export interface UpdateLeadInput {
  name: string;
  phone?: string;
  email?: string;
  interest: LeadInterest;
  sourceCode?: string;
  sourceDetail?: string;
  summary?: string;
  propertyReference?: string;
  profile: LeadProfileInput;
}

export function updateLead(id: string, input: UpdateLeadInput) {
  return apiClient.put<LeadDetail>(`/internal/leads/${id}`, input);
}

export function shareLeadProperty(id: string, reference: string, note?: string) {
  return apiClient.post<LeadDetail>(`/internal/leads/${id}/shared-properties`, { reference, note });
}

/** folio vacío = desvincular. */
export function linkLeadExpediente(id: string, folio: string) {
  return apiClient.post<LeadDetail>(`/internal/leads/${id}/expediente`, { folio });
}

// --- Actividades y agenda (CC#30) --------------------------------------------

export interface TaskInput {
  type: TaskType;
  title?: string;
  scheduledAt?: string;
  durationMinutes?: number;
  advisorId?: string;
  propertyKey?: string;
  propertyTitle?: string;
  notesBefore?: string;
  reminderMinutes?: number;
  /** true = ya se realizó (se registra con su resultado). */
  done: boolean;
  result?: TaskResult;
  actualMinutes?: number;
  outcomeNotes?: string;
  nextAction?: string;
  nextContactAt?: string;
  nextType?: TaskType;
}

export interface CompleteTaskInput {
  result: TaskResult;
  actualMinutes?: number;
  outcomeNotes?: string;
  nextAction?: string;
  nextContactAt?: string;
  nextType?: TaskType;
}

export function createLeadTask(leadId: string, input: TaskInput) {
  return apiClient.post<LeadTask>(`/internal/leads/${leadId}/tasks`, input);
}

export function rescheduleLeadTask(taskId: string, input: TaskInput) {
  return apiClient.put<LeadTask>(`/internal/lead-tasks/${taskId}`, input);
}

export function completeLeadTask(taskId: string, input: CompleteTaskInput) {
  return apiClient.post<LeadTask>(`/internal/lead-tasks/${taskId}/complete`, input);
}

export function cancelLeadTask(taskId: string, reason?: string) {
  return apiClient.post<LeadTask>(`/internal/lead-tasks/${taskId}/cancel`, { reason });
}

export interface AgendaQuery {
  from: string;
  to: string;
  advisorId?: string;
  type?: TaskType | "";
  leadId?: string;
  property?: string;
}

export function getAgenda(params: AgendaQuery) {
  return apiClient.get<LeadTask[]>(`/internal/crm/agenda${query(params)}`);
}

// --- Catálogo de fuentes (CC#29) ---------------------------------------------

export interface LeadSourceOption {
  code: string;
  category: SourceCategory;
  label: string;
  description: string | null;
  channel: LeadSource;
  active: boolean;
  requiresDetail: boolean;
  /** La asigna el sistema (asistente de WhatsApp): no aparece en el alta manual. */
  system: boolean;
  sortOrder: number;
}

export function listLeadSources() {
  return apiClient.get<LeadSourceOption[]>("/internal/crm/lead-sources");
}

export function updateLeadSource(code: string, input: { label: string; description?: string; active: boolean; requiresDetail: boolean }) {
  return apiClient.put<LeadSourceOption>(`/internal/crm/lead-sources/${code}`, input);
}

export function createLeadSource(input: { category: SourceCategory; label: string; description?: string; channel?: LeadSource; requiresDetail: boolean }) {
  return apiClient.post<LeadSourceOption>("/internal/crm/lead-sources", input);
}

// --- Dashboard ---------------------------------------------------------------

export interface AdvisorProductivity {
  advisorId: string;
  name: string;
  leads: number;
  won: number;
  converted: number;
  conversionRate: number;
  activeLeads: number;
  tasksDone: number;
  pendingTasks: number;
  overdueTasks: number;
  staleLeads: number;
}

export interface SourceRow {
  code: string;
  label: string;
  category: SourceCategory;
  leads: number;
  share: number;
  converted: number;
  conversionRate: number;
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
  byAdvisor: AdvisorProductivity[];
  topProperties: { online21Id: number | null; key: string | null; title: string | null; leads: number }[];
  recent: LeadItem[];
  converted: number;
  conversionRate: number;
  pipelineValue: number;
  staleLeads: number;
  staleDays: number;
  atRisk: LeadItem[];
  sources: SourceRow[];
  sourceTrend: { months: string[]; series: { code: string; label: string; counts: number[] }[] };
  stageTimes: { stage: LeadStage; averageDays: number; samples: number }[];
  pendingTasks: number;
  overdueTasks: number;
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
  /** Copia de su usuario de 21Online (21Online manda); null si no está vinculado o aún no se sincroniza. */
  online21: Online21Snapshot | null;
}

export interface Online21Snapshot {
  name: string | null;
  email: string | null;
  mobilePhone: string | null;
  officePhone: string | null;
  whatsapp: string | null;
  photoUrl: string | null;
  position: string | null;
  /** Su ficha pública en century21mexico.com. */
  profileUrl: string | null;
  syncedAt: string;
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
  /** Control de Cambios #30: días sin actividad para la alerta "por enfriarse". */
  staleDays: number;
  reminderEmail: boolean;
}

export function getAssignmentSettings() {
  return apiClient.get<AssignmentSettings>("/internal/crm/assignment-settings");
}

export function updateAssignmentSettings(roundMode: RoundMode, ownerOffShiftPolicy: OwnerOffShiftPolicy) {
  return apiClient.put<AssignmentSettings>("/internal/crm/assignment-settings", { roundMode, ownerOffShiftPolicy });
}

export function updateFollowUpSettings(staleDays: number, reminderEmail: boolean) {
  return apiClient.put<AssignmentSettings>("/internal/crm/follow-up-settings", { staleDays, reminderEmail });
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

export interface SavedReply {
  id: string;
  title: string;
  text: string;
  createdBy: string | null;
  updatedAt: string;
}

export function listSavedReplies() {
  return apiClient.get<SavedReply[]>("/internal/conversations/saved-replies");
}

export function createSavedReply(title: string, text: string) {
  return apiClient.post<SavedReply>("/internal/conversations/saved-replies", { title, text });
}

export function updateSavedReply(id: string, title: string, text: string) {
  return apiClient.put<SavedReply>(`/internal/conversations/saved-replies/${id}`, { title, text });
}

export function deleteSavedReply(id: string) {
  return apiClient.delete<void>(`/internal/conversations/saved-replies/${id}`);
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
  photoUrl: string | null;
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
