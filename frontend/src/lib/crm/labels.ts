import type { AbsenceKind, AdvisorStatus, AssignmentMethod, AssignmentStatus, ConversationControl, LeadInterest, LeadSource, LeadStage, ShiftKind } from "@/lib/api/crm";

type Tone = "gold" | "obsessed" | "success" | "warning" | "danger" | "info" | "manual" | "neutral";

/** Embudo de seguimiento del lead, en orden. */
export const stageOrder: LeadStage[] = ["NEW", "CONTACTED", "APPOINTMENT", "VISITED", "NEGOTIATION", "WON", "LOST"];

export const stageLabels: Record<LeadStage, string> = {
  NEW: "Nuevo",
  CONTACTED: "Contactado",
  APPOINTMENT: "Cita agendada",
  VISITED: "Visitó",
  NEGOTIATION: "Negociación",
  WON: "Cerrado ganado",
  LOST: "Perdido",
};

export const stageTones: Record<LeadStage, Tone> = {
  NEW: "warning",
  CONTACTED: "info",
  APPOINTMENT: "gold",
  VISITED: "manual",
  NEGOTIATION: "obsessed",
  WON: "success",
  LOST: "danger",
};

export const sourceLabels: Record<LeadSource, string> = {
  WHATSAPP_BOT: "WhatsApp (asistente)",
  PORTAL: "Portal inmobiliario",
  CAMPAIGN: "Campaña",
  WEBSITE: "Sitio web",
  REFERRAL: "Referido",
  WALK_IN: "Oficina",
  PHONE: "Llamada",
};

export const interestLabels: Record<LeadInterest, string> = {
  BUY: "Comprar",
  RENT: "Rentar",
  SELL: "Vender su propiedad",
  LEASE_OUT: "Poner en renta su propiedad",
  INVEST: "Invertir",
  OTHER: "Información",
};

export const assignmentLabels: Record<AssignmentMethod, string> = {
  PROPERTY_OWNER: "Asesor de la propiedad",
  CONTINUITY: "Continuidad",
  ROULETTE: "Ronda",
  GUARD: "Guardia",
  MANUAL: "Manual",
};

export const assignmentHints: Record<AssignmentMethod, string> = {
  PROPERTY_OWNER: "Es el asesor responsable de la propiedad en 21Online",
  CONTINUITY: "El cliente ya tenía un lead activo con este asesor",
  ROULETTE: "Se repartió en la ronda entre los asesores en turno",
  GUARD: "El asesor de la propiedad no estaba disponible y se canalizó a la guardia",
  MANUAL: "Lo asignó una persona desde el panel",
};

export const assignmentStatusLabels: Record<AssignmentStatus, string> = {
  ASSIGNED: "Asignado",
  PENDING_OWNER: "Espera al asesor de la propiedad",
  PENDING_SCHEDULE: "Pendiente por horario",
  UNASSIGNED: "Sin asignar",
};

export const assignmentStatusTones: Record<AssignmentStatus, Tone> = {
  ASSIGNED: "success",
  PENDING_OWNER: "info",
  PENDING_SCHEDULE: "warning",
  UNASSIGNED: "danger",
};

export const advisorStatusLabels: Record<AdvisorStatus, string> = {
  AVAILABLE: "Disponible ahora",
  OFF_SHIFT: "Fuera de horario",
  ABSENT: "Ausente",
  PAUSED: "En pausa",
  LIMIT_REACHED: "Límite alcanzado",
  NO_CALENDAR: "Sin calendario",
};

export const advisorStatusTones: Record<AdvisorStatus, Tone> = {
  AVAILABLE: "success",
  OFF_SHIFT: "neutral",
  ABSENT: "manual",
  PAUSED: "warning",
  LIMIT_REACHED: "info",
  NO_CALENDAR: "danger",
};

export const shiftKindLabels: Record<ShiftKind, string> = { REGULAR: "Jornada", GUARD: "Guardia" };

export const absenceKindLabels: Record<AbsenceKind, string> = {
  VACATION: "Vacaciones",
  PERMISSION: "Permiso",
  REST: "Descanso",
  UNAVAILABLE: "No disponible",
};

/** Días ISO (1 = lunes). */
export const dayNames = ["", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"];
export const dayShort = ["", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"];
const monthShort = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];

export const BUSINESS_TIMEZONE_LABEL = "Hora de Ciudad de México (America/Mexico_City)";

// --- Fechas de negocio como texto (sin zona del dispositivo) -----------------

/** "2026-10-12" + n días, con aritmética en UTC para no depender de la zona del navegador. */
export function addDays(date: string, n: number): string {
  const [y, m, d] = date.slice(0, 10).split("-").map(Number);
  const t = new Date(Date.UTC(y, m - 1, d + n));
  return t.toISOString().slice(0, 10);
}

/** Día ISO de la semana (1 = lunes … 7 = domingo). */
export function isoDay(date: string): number {
  const [y, m, d] = date.slice(0, 10).split("-").map(Number);
  const w = new Date(Date.UTC(y, m - 1, d)).getUTCDay();
  return w === 0 ? 7 : w;
}

const monthLong = ["enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"];

/** Primer día del mes de la fecha, desplazado n meses ("2026-10-12", 1 → "2026-11-01"). */
export function addMonths(date: string, n: number): string {
  const [y, m] = date.slice(0, 10).split("-").map(Number);
  const t = new Date(Date.UTC(y, m - 1 + n, 1));
  return t.toISOString().slice(0, 10);
}

/** "octubre de 2026". */
export function monthTitle(date: string): string {
  const [y, m] = date.slice(0, 10).split("-").map(Number);
  return `${monthLong[m - 1]} de ${y}`;
}

export function weekStart(date: string): string {
  return addDays(date, 1 - isoDay(date));
}

/** Minutos desde la medianoche de "YYYY-MM-DDTHH:mm[:ss]". */
export function minutesOf(local: string): number {
  return Number(local.slice(11, 13)) * 60 + Number(local.slice(14, 16));
}

export function hhmm(local: string): string {
  return local.slice(11, 16);
}

/** "lun 12 oct". */
export function formatDayLabel(date: string): string {
  const [, m, d] = date.slice(0, 10).split("-").map(Number);
  return `${dayShort[isoDay(date)].toLowerCase()} ${d} ${monthShort[m - 1]}`;
}

/** "lun 12 oct, 14:00". */
export function formatLocal(local: string): string {
  return `${formatDayLabel(local)}, ${hhmm(local)}`;
}

/** Colores por asesor (estables por id; contrastan en tema claro y oscuro). */
const advisorPalette = [
  { bg: "#e8f0fb", border: "#3b6fb6", text: "#1f3f6b" },
  { bg: "#e9f6ee", border: "#2f8a55", text: "#1b4f31" },
  { bg: "#fbefe3", border: "#c4772b", text: "#6b3d10" },
  { bg: "#f3e9f8", border: "#8a4fb0", text: "#4d2466" },
  { bg: "#fde8ec", border: "#c2445e", text: "#6e1f31" },
  { bg: "#e6f5f5", border: "#2c8c8c", text: "#164b4b" },
  { bg: "#f6f1df", border: "#a3862a", text: "#5a4813" },
  { bg: "#ecebfa", border: "#5a57c2", text: "#2d2b6b" },
];

export function advisorColor(id: string) {
  let h = 0;
  for (let i = 0; i < id.length; i++) h = (h * 31 + id.charCodeAt(i)) >>> 0;
  return advisorPalette[h % advisorPalette.length];
}

export const controlLabels: Record<ConversationControl, string> = {
  AI: "IA activa",
  HUMAN: "Atención humana",
  PAUSED: "IA en pausa",
};

export const controlTones: Record<ConversationControl, Tone> = {
  AI: "success",
  HUMAN: "info",
  PAUSED: "neutral",
};

export const ONLINE21_BASE_URL = "https://plus.21onlinemx.com";

const mxn = new Intl.NumberFormat("es-MX", { style: "currency", currency: "MXN", maximumFractionDigits: 0 });

export function formatPrice(amount: number): string {
  return mxn.format(amount);
}

const dateTime = new Intl.DateTimeFormat("es-MX", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });
const timeOnly = new Intl.DateTimeFormat("es-MX", { hour: "2-digit", minute: "2-digit" });

export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso));
}

const mxDateTime = new Intl.DateTimeFormat("es-MX", { weekday: "short", day: "numeric", month: "short", hour: "2-digit", minute: "2-digit", timeZone: "America/Mexico_City" });

/** Un instante del servidor mostrado SIEMPRE en hora de Ciudad de México. */
export function formatInstantMx(iso: string): string {
  return mxDateTime.format(new Date(iso));
}

export function formatTime(iso: string): string {
  return timeOnly.format(new Date(iso));
}

/** "hace 5 min", "hace 3 h", "hace 2 días". */
export function timeAgo(iso: string, now = Date.now()): string {
  const minutes = Math.max(0, Math.round((now - new Date(iso).getTime()) / 60_000));
  if (minutes < 1) return "ahora";
  if (minutes < 60) return `hace ${minutes} min`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `hace ${hours} h`;
  const days = Math.round(hours / 24);
  return `hace ${days} día${days === 1 ? "" : "s"}`;
}

export function whatsappLink(phone: string): string {
  return `https://wa.me/${phone.replace(/\D/g, "")}`;
}
