// Tipos del CRM (Módulo 3: leads, asesores, dashboard y bot de WhatsApp).
// Por ahora el frontend usa datos de ejemplo (src/data/crm); cuando exista el
// backend, estos tipos son el contrato que deberán devolver sus endpoints.

/** Propiedad tal como se consulta en 21Online (solo lectura: el CRUD es del Módulo 2). */
export interface CrmProperty {
  /** Id numérico de 21Online (p. ej. 634294). */
  id: number;
  /** Clave pública de 21Online (p. ej. "634294--649"). */
  key: string;
  /** Clave interna de la oficina (p. ej. "GN 14"). */
  internalKey: string;
  street: string;
  neighborhood: string;
  municipality: string;
  state: string;
  subtype: string;
  operation: "venta" | "renta";
  price: number;
  currency: "MXN" | "USD";
  advisorId: string;
  /** Fecha de la última modificación en 21Online (ISO). */
  updatedAt: string;
  /** Detalle de la ficha; solo existe cuando ya se consultó la ficha completa. */
  details?: {
    landM2?: number;
    builtM2?: number;
    bedrooms?: number;
    bathrooms?: number;
    parking?: number;
    description?: string;
    features?: string[];
    acceptsCredit?: boolean;
    omniMlsId?: string;
  };
}

export interface CrmAdvisor {
  id: string;
  name: string;
  email?: string;
  active: boolean;
  /** Peso en la ruleta: con peso 10 recibe el doble de leads que con peso 5. */
  weight: number;
  dailyLimit: number | null;
  /** Horario en el que recibe leads de la ruleta (hora de CDMX). */
  shift?: string;
}

export type LeadStage = "NEW" | "CONTACTED" | "APPOINTMENT" | "VISITED" | "NEGOTIATION" | "WON" | "LOST";

export type LeadSource = "WHATSAPP_BOT" | "PORTAL" | "CAMPAIGN" | "WEBSITE" | "REFERRAL" | "WALK_IN";

export type LeadInterest = "BUY" | "RENT" | "SELL" | "INVEST";

/** Cómo llegó el lead a su asesor. */
export type AssignmentMethod = "PROPERTY_OWNER" | "ROULETTE" | "MANUAL";

export interface LeadActivity {
  id: string;
  at: string;
  kind: "CREATED" | "ASSIGNED" | "REASSIGNED" | "STAGE" | "NOTE" | "BOT" | "NOTICE";
  text: string;
  by?: string;
}

export interface CrmLead {
  id: string;
  name: string;
  phone: string;
  email?: string;
  source: LeadSource;
  /** Detalle de la fuente: portal, campaña, etc. */
  sourceDetail?: string;
  interest: LeadInterest;
  stage: LeadStage;
  propertyId: number | null;
  advisorId: string | null;
  assignment: AssignmentMethod | null;
  /** Lo que el cliente dijo que busca (resumen del bot o del asesor). */
  summary?: string;
  budget?: number;
  createdAt: string;
  lastContactAt?: string;
  conversationId?: string;
  lostReason?: string;
  activities: LeadActivity[];
}

export type ConversationControl = "AI" | "HUMAN" | "PAUSED";

export interface ConversationMessage {
  id: string;
  from: "CLIENT" | "BOT" | "TEAM";
  text: string;
  at: string;
}

export interface CrmConversation {
  id: string;
  leadId: string | null;
  contactName: string;
  phone: string;
  control: ConversationControl;
  /** Lo que entendió el bot. */
  intent: string;
  needsAttention?: string;
  messages: ConversationMessage[];
}
