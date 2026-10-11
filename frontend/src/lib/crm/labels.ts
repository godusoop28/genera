import type { AssignmentMethod, ConversationControl, CrmProperty, LeadInterest, LeadSource, LeadStage } from "@/types/crm";

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

/** Etapas abiertas: el lead sigue en seguimiento. */
export const openStages: LeadStage[] = ["NEW", "CONTACTED", "APPOINTMENT", "VISITED", "NEGOTIATION"];

export const sourceLabels: Record<LeadSource, string> = {
  WHATSAPP_BOT: "WhatsApp (asistente)",
  PORTAL: "Portal inmobiliario",
  CAMPAIGN: "Campaña",
  WEBSITE: "Sitio web",
  REFERRAL: "Referido",
  WALK_IN: "Oficina",
};

export const interestLabels: Record<LeadInterest, string> = {
  BUY: "Comprar",
  RENT: "Rentar",
  SELL: "Vender su propiedad",
  INVEST: "Invertir",
};

export const assignmentLabels: Record<AssignmentMethod, string> = {
  PROPERTY_OWNER: "Asesor de la propiedad",
  ROULETTE: "Ruleta",
  MANUAL: "Manual",
};

export const assignmentHints: Record<AssignmentMethod, string> = {
  PROPERTY_OWNER: "Se asignó al asesor que tiene la propiedad en 21Online",
  ROULETTE: "Se repartió entre los asesores activos según su peso y horario",
  MANUAL: "Lo asignó una persona desde el panel",
};

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

const mxn = new Intl.NumberFormat("es-MX", { style: "currency", currency: "MXN", maximumFractionDigits: 0 });

export function formatPrice(amount: number, currency: "MXN" | "USD" = "MXN"): string {
  return currency === "MXN" ? mxn.format(amount) : `${new Intl.NumberFormat("es-MX", { maximumFractionDigits: 0 }).format(amount)} USD`;
}

export function propertyPrice(p: CrmProperty): string {
  return `${formatPrice(p.price, p.currency)}${p.operation === "renta" ? " / mes" : ""}`;
}

export function propertyTitle(p: CrmProperty): string {
  return `${p.subtype} en ${p.operation} · ${p.neighborhood}`;
}

export function propertyAddress(p: CrmProperty): string {
  return `${p.street}, ${p.neighborhood}, ${p.municipality}`;
}

const dateTime = new Intl.DateTimeFormat("es-MX", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });
const dateOnly = new Intl.DateTimeFormat("es-MX", { day: "numeric", month: "short", year: "numeric" });
const timeOnly = new Intl.DateTimeFormat("es-MX", { hour: "2-digit", minute: "2-digit" });

export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso));
}

export function formatDate(iso: string): string {
  return dateOnly.format(new Date(iso));
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
