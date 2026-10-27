import type {
  AbsenceKind,
  AdvisorStatus,
  AssignmentMethod,
  AssignmentStatus,
  ConversationControl,
  LeadInterest,
  LeadSource,
  LeadStage,
  LeadTemperature,
  LostReason,
  ShiftKind,
  SourceCategory,
  TaskResult,
  TaskType,
} from "@/lib/api/crm";

type Tone = "gold" | "obsessed" | "success" | "warning" | "danger" | "info" | "manual" | "neutral";

/** Pipeline de seguimiento del lead, en orden (Control de Cambios #30). */
export const stageOrder: LeadStage[] = ["NEW", "CONTACTED", "QUALIFIED", "APPOINTMENT", "VISITED", "PROPOSAL", "NEGOTIATION", "OFFER_ACCEPTED", "WON", "LOST"];

export const stageLabels: Record<LeadStage, string> = {
  NEW: "Nuevo",
  CONTACTED: "Primer contacto",
  QUALIFIED: "Calificado",
  APPOINTMENT: "Cita agendada",
  VISITED: "Visita realizada",
  PROPOSAL: "Propuesta enviada",
  NEGOTIATION: "Negociación",
  OFFER_ACCEPTED: "Oferta aceptada",
  WON: "Cierre",
  LOST: "Perdido",
};

export const stageHints: Record<LeadStage, string> = {
  NEW: "Recién registrado, sin contacto aún",
  CONTACTED: "Ya se hizo el primer contacto",
  QUALIFIED: "Interés real y capacidad de compra o renta",
  APPOINTMENT: "Visita o reunión programada",
  VISITED: "Ya vio uno o más inmuebles",
  PROPOSAL: "Se envió propuesta o ficha técnica",
  NEGOTIATION: "Hay oferta o contrapropuesta en proceso",
  OFFER_ACCEPTED: "Aceptó las condiciones: se vincula al expediente",
  WON: "La transacción se concretó",
  LOST: "Descartado, con motivo registrado",
};

export const stageTones: Record<LeadStage, Tone> = {
  NEW: "warning",
  CONTACTED: "info",
  QUALIFIED: "info",
  APPOINTMENT: "gold",
  VISITED: "manual",
  PROPOSAL: "manual",
  NEGOTIATION: "obsessed",
  OFFER_ACCEPTED: "success",
  WON: "success",
  LOST: "danger",
};

/** Canal general (la fuente precisa viene del catálogo, CC#29). */
export const sourceLabels: Record<LeadSource, string> = {
  WHATSAPP_BOT: "WhatsApp (asistente)",
  WHATSAPP: "WhatsApp",
  PORTAL: "Portal inmobiliario",
  SOCIAL: "Redes sociales",
  CAMPAIGN: "Campaña",
  WEBSITE: "Sitio web / búsqueda",
  OUTDOOR: "Publicidad exterior",
  REFERRAL: "Referido",
  WALK_IN: "Oficina",
  EVENT: "Evento",
  PHONE: "Llamada",
  REACTIVATION: "Reactivación",
  OTHER: "Otra",
};

export const sourceCategoryLabels: Record<SourceCategory, string> = {
  PORTALS: "Portales inmobiliarios",
  SOCIAL: "Redes sociales y mensajería",
  SEARCH: "Búsqueda y digital",
  OUTDOOR: "Publicidad exterior y física",
  REFERRALS: "Referidos y red de contactos",
  IN_PERSON: "Presencial y eventos",
  REACTIVATION: "Reactivación",
  UNKNOWN: "Sin identificar",
};

export const sourceCategoryOrder: SourceCategory[] = ["PORTALS", "SOCIAL", "SEARCH", "OUTDOOR", "REFERRALS", "IN_PERSON", "REACTIVATION", "UNKNOWN"];

export const temperatureLabels: Record<LeadTemperature, string> = { COLD: "Frío", WARM: "Tibio", HOT: "Caliente" };
export const temperatureTones: Record<LeadTemperature, Tone> = { COLD: "info", WARM: "warning", HOT: "danger" };
export const temperatureDots: Record<LeadTemperature, string> = { COLD: "🔵", WARM: "🟡", HOT: "🔴" };

export const lostReasonLabels: Record<LostReason, string> = {
  PRICE: "Precio",
  ZONE: "Zona",
  TIMING: "Tiempo",
  COMPETITION: "Competencia",
  NO_RESPONSE: "Dejó de responder",
  OTHER: "Otro",
};

export const taskTypeLabels: Record<TaskType, string> = {
  CALL: "Llamada",
  WHATSAPP: "Mensaje WhatsApp",
  EMAIL: "Correo electrónico",
  VISIT: "Visita al inmueble",
  OFFICE_MEETING: "Reunión en oficina",
  VIDEO_CALL: "Videollamada",
  PROPOSAL: "Envío de propuesta",
  OFFER_FOLLOW_UP: "Seguimiento de oferta",
  NOTE: "Nota interna",
  OTHER: "Otro",
};

export const taskTypeEmoji: Record<TaskType, string> = {
  CALL: "📞",
  WHATSAPP: "💬",
  EMAIL: "📧",
  VISIT: "🏠",
  OFFICE_MEETING: "🏢",
  VIDEO_CALL: "🎥",
  PROPOSAL: "📄",
  OFFER_FOLLOW_UP: "🤝",
  NOTE: "📝",
  OTHER: "⚙️",
};

export const taskResultLabels: Record<TaskResult, string> = {
  CONTACTED: "Contactado",
  NO_ANSWER: "No contestó",
  RESCHEDULED: "Reagendó",
  REJECTED: "Rechazó",
  INTERESTED: "Interesado",
  ADVANCED: "Avanzó",
};

export const reminderOptions = [
  { value: "", label: "Sin recordatorio" },
  { value: "15", label: "15 minutos antes" },
  { value: "60", label: "1 hora antes" },
  { value: "1440", label: "1 día antes" },
];

/** Nombre de la fuente del lead: la del catálogo (o el canal en registros viejos). */
export function sourceName(lead: { source: LeadSource; sourceLabel?: string | null; sourceDetail?: string | null }): string {
  const base = lead.sourceLabel ?? sourceLabels[lead.source];
  return lead.sourceDetail ? `${base} · ${lead.sourceDetail}` : base;
}

/** "2026-10-26T15:30" en hora de CDMX → instante ISO (UTC). */
export function mxLocalToIso(local: string): string {
  // Ciudad de México no tiene horario de verano desde 2022: UTC-6 fijo.
  return new Date(`${local.length === 16 ? `${local}:00` : local}-06:00`).toISOString();
}

/** Instante ISO → "2026-10-26T15:30" en hora de CDMX (para inputs datetime-local). */
export function isoToMxLocal(iso: string): string {
  const d = new Date(new Date(iso).getTime() - 6 * 3600_000);
  return d.toISOString().slice(0, 16);
}

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
