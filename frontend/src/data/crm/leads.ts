import type { AssignmentMethod, CrmConversation, CrmLead, LeadActivity, LeadInterest, LeadSource, LeadStage } from "@/types/crm";
import { crmAdvisors } from "./properties";

// Leads y conversaciones FICTICIOS para el prototipo del CRM: nombres y
// teléfonos inventados (555). Las fechas se calculan respecto al momento en
// que se abre la página para que los filtros de periodo siempre tengan datos.

const HOUR = 3_600_000;

interface Seed {
  name: string;
  phone: string;
  source: LeadSource;
  sourceDetail?: string;
  interest: LeadInterest;
  stage: LeadStage;
  propertyId: number | null;
  advisorId: string | null;
  assignment: AssignmentMethod | null;
  summary: string;
  budget?: number;
  hoursAgo: number;
  conversationId?: string;
  lostReason?: string;
}

const seeds: Seed[] = [
  { name: "Daniela Ruiz Peña", phone: "+52 777 555 0142", source: "WHATSAPP_BOT", interest: "BUY", stage: "NEW", propertyId: 634294, advisorId: "adv-aivy", assignment: "PROPERTY_OWNER", summary: "Pregunta por la casa GN 14 en Burgos; quiere saber si aceptan crédito Infonavit.", budget: 6_000_000, hoursAgo: 0.4, conversationId: "conv-1" },
  { name: "Luis Fernando Ortega", phone: "+52 777 555 0198", source: "PORTAL", sourceDetail: "Inmuebles24", interest: "RENT", stage: "NEW", propertyId: 634274, advisorId: "adv-garanda", assignment: "PROPERTY_OWNER", summary: "Busca departamento en renta cerca del centro, mudanza en noviembre.", budget: 8_000, hoursAgo: 1.2 },
  { name: "Mariana Sotelo", phone: "+52 777 555 0115", source: "WHATSAPP_BOT", interest: "BUY", stage: "NEW", propertyId: null, advisorId: null, assignment: null, summary: "Busca casa de 3 recámaras en Jiutepec o Temixco, hasta 3.5 millones.", budget: 3_500_000, hoursAgo: 2, conversationId: "conv-2" },
  { name: "Roberto Campos Islas", phone: "+52 777 555 0173", source: "CAMPAIGN", sourceDetail: "Facebook Ads · Casas Cuernavaca", interest: "BUY", stage: "CONTACTED", propertyId: 632450, advisorId: "adv-ealanis", assignment: "PROPERTY_OWNER", summary: "Interesado en Jardines de Cuernavaca; pidió más fotos del jardín.", budget: 14_000_000, hoursAgo: 5 },
  { name: "Ana Karen Velasco", phone: "+52 777 555 0127", source: "WHATSAPP_BOT", interest: "SELL", stage: "CONTACTED", propertyId: null, advisorId: "adv-gcruz", assignment: "ROULETTE", summary: "Quiere vender su casa en Lomas de Cortés y pregunta qué documentos necesita.", hoursAgo: 7, conversationId: "conv-3" },
  { name: "Jorge Iván Mendoza", phone: "+52 777 555 0164", source: "WEBSITE", sourceDetail: "century21genera.com", interest: "BUY", stage: "APPOINTMENT", propertyId: 631618, advisorId: "adv-opineda", assignment: "PROPERTY_OWNER", summary: "Cita para el sábado a las 11:00 en Olimpo 5.", budget: 8_000_000, hoursAgo: 20 },
  { name: "Paola Jiménez Lara", phone: "+52 777 555 0189", source: "PORTAL", sourceDetail: "Mercado Libre", interest: "BUY", stage: "CONTACTED", propertyId: 634331, advisorId: "adv-garanda", assignment: "PROPERTY_OWNER", summary: "Pregunta si el precio es negociable.", budget: 3_100_000, hoursAgo: 26 },
  { name: "Héctor Salgado", phone: "+52 777 555 0136", source: "WHATSAPP_BOT", interest: "RENT", stage: "APPOINTMENT", propertyId: 634037, advisorId: "adv-gcruz", assignment: "PROPERTY_OWNER", summary: "Renta en Jiutepec para familia de 4; tiene mascota pequeña.", budget: 13_000, hoursAgo: 30, conversationId: "conv-4" },
  { name: "Claudia Ríos", phone: "+52 777 555 0151", source: "REFERRAL", sourceDetail: "Recomendación de cliente", interest: "INVEST", stage: "NEGOTIATION", propertyId: 633844, advisorId: "adv-gcruz", assignment: "MANUAL", summary: "Busca local para inversión; ofertó 7.1 millones.", budget: 7_100_000, hoursAgo: 52 },
  { name: "Fernando Aguilar", phone: "+52 777 555 0108", source: "CAMPAIGN", sourceDetail: "Facebook Ads · Casas Cuernavaca", interest: "BUY", stage: "VISITED", propertyId: 634365, advisorId: "adv-ealanis", assignment: "PROPERTY_OWNER", summary: "Visitó la casa en Acapantzingo; lo está pensando con su esposa.", budget: 4_800_000, hoursAgo: 60 },
  { name: "Itzel Navarro", phone: "+52 777 555 0177", source: "WHATSAPP_BOT", interest: "BUY", stage: "LOST", propertyId: 623767, advisorId: "adv-egarcia", assignment: "PROPERTY_OWNER", summary: "Buscaba algo más cerca de Cuernavaca.", budget: 1_300_000, hoursAgo: 75, lostReason: "Ubicación no le convenció" },
  { name: "Ricardo Esquivel", phone: "+52 777 555 0193", source: "PORTAL", sourceDetail: "Vivanuncios", interest: "BUY", stage: "CONTACTED", propertyId: 626033, advisorId: "adv-mcastilleja", assignment: "PROPERTY_OWNER", summary: "Pide recorrido virtual antes de agendar.", budget: 7_500_000, hoursAgo: 80 },
  { name: "Sofía Hernández Brito", phone: "+52 777 555 0120", source: "WHATSAPP_BOT", interest: "BUY", stage: "APPOINTMENT", propertyId: null, advisorId: "adv-cmarin", assignment: "ROULETTE", summary: "Busca casa con alberca en Cuernavaca, presupuesto 10–14 millones.", budget: 14_000_000, hoursAgo: 96 },
  { name: "Emilio Bautista", phone: "+52 777 555 0159", source: "WALK_IN", sourceDetail: "Oficina Alta Tensión", interest: "SELL", stage: "WON", propertyId: null, advisorId: "adv-aivy", assignment: "MANUAL", summary: "Firmó contrato de intermediación; se abrió expediente.", hoursAgo: 120 },
  { name: "Gabriela Tapia", phone: "+52 777 555 0185", source: "WHATSAPP_BOT", interest: "RENT", stage: "WON", propertyId: 632014, advisorId: "adv-garanda", assignment: "PROPERTY_OWNER", summary: "Firmó contrato de arrendamiento por 12 meses.", budget: 26_000, hoursAgo: 150 },
  { name: "Andrés Vela", phone: "+52 777 555 0131", source: "CAMPAIGN", sourceDetail: "Google Ads · Terrenos", interest: "INVEST", stage: "CONTACTED", propertyId: 634390, advisorId: "adv-ealanis", assignment: "PROPERTY_OWNER", summary: "Pregunta uso de suelo del terreno.", budget: 1_600_000, hoursAgo: 170 },
  { name: "Valeria Quintero", phone: "+52 777 555 0146", source: "PORTAL", sourceDetail: "Inmuebles24", interest: "BUY", stage: "LOST", propertyId: 609011, advisorId: "adv-cmarin", assignment: "PROPERTY_OWNER", summary: "Fuera de su presupuesto.", budget: 20_000_000, hoursAgo: 200, lostReason: "Presupuesto" },
  { name: "Miguel Ángel Toledo", phone: "+52 777 555 0168", source: "WHATSAPP_BOT", interest: "BUY", stage: "VISITED", propertyId: 630981, advisorId: "adv-aivy", assignment: "PROPERTY_OWNER", summary: "Visitó Acacias 304; pidió avalúo.", budget: 12_000_000, hoursAgo: 240 },
  { name: "Lucía Ferrer", phone: "+52 777 555 0102", source: "WEBSITE", sourceDetail: "century21genera.com", interest: "RENT", stage: "NEGOTIATION", propertyId: 631379, advisorId: "adv-joropeza", assignment: "PROPERTY_OWNER", summary: "Negociando depósito y fecha de entrada.", budget: 35_000, hoursAgo: 260 },
  { name: "Óscar Pineda Luna", phone: "+52 777 555 0111", source: "WHATSAPP_BOT", interest: "BUY", stage: "CONTACTED", propertyId: 633748, advisorId: "adv-aivy", assignment: "PROPERTY_OWNER", summary: "Primera vivienda; quiere saber gastos de escrituración.", budget: 3_000_000, hoursAgo: 300 },
  { name: "Karla Domínguez", phone: "+52 777 555 0155", source: "REFERRAL", sourceDetail: "Asesor de otra oficina", interest: "BUY", stage: "APPOINTMENT", propertyId: 614064, advisorId: "adv-cmarin", assignment: "PROPERTY_OWNER", summary: "Cliente referido de CDMX, viene el próximo fin de semana.", budget: 14_000_000, hoursAgo: 340 },
  { name: "Santiago Ibarra", phone: "+52 777 555 0179", source: "PORTAL", sourceDetail: "Lamudi", interest: "BUY", stage: "NEW", propertyId: 626153, advisorId: "adv-iherrera", assignment: "PROPERTY_OWNER", summary: "Solicitó información desde el portal.", hoursAgo: 380 },
  { name: "Brenda Olvera", phone: "+52 777 555 0124", source: "WHATSAPP_BOT", interest: "SELL", stage: "NEGOTIATION", propertyId: null, advisorId: "adv-pmancilla", assignment: "ROULETTE", summary: "Quiere vender departamento heredado; revisando comisión.", hoursAgo: 420 },
  { name: "Tomás Reyes", phone: "+52 777 555 0161", source: "CAMPAIGN", sourceDetail: "Facebook Ads · Rentas", interest: "RENT", stage: "LOST", propertyId: 634037, advisorId: "adv-gcruz", assignment: "PROPERTY_OWNER", summary: "Ya rentó en otro lugar.", hoursAgo: 480, lostReason: "Rentó con otra inmobiliaria" },
  { name: "Renata Galindo", phone: "+52 777 555 0187", source: "WHATSAPP_BOT", interest: "BUY", stage: "WON", propertyId: 631603, advisorId: "adv-pmancilla", assignment: "PROPERTY_OWNER", summary: "Firmó manifestación de intención.", budget: 5_500_000, hoursAgo: 560 },
  { name: "Diego Carrillo", phone: "+52 777 555 0105", source: "PORTAL", sourceDetail: "Mercado Libre", interest: "BUY", stage: "CONTACTED", propertyId: 633123, advisorId: "adv-joropeza", assignment: "PROPERTY_OWNER", summary: "Pregunta por el mantenimiento del condominio.", budget: 2_000_000, hoursAgo: 620 },
];

function advisorName(id: string | null): string {
  return crmAdvisors.find((a) => a.id === id)?.name ?? "—";
}

function buildActivities(seed: Seed, id: string, createdAt: number): LeadActivity[] {
  const at = (offsetHours: number) => new Date(createdAt + offsetHours * HOUR).toISOString();
  const list: LeadActivity[] = [
    { id: `${id}-a0`, at: at(0), kind: "CREATED", text: seed.source === "WHATSAPP_BOT" ? "Llegó por WhatsApp (asistente)." : "Lead registrado." },
  ];
  if (seed.advisorId) {
    const how =
      seed.assignment === "PROPERTY_OWNER" ? "por ser el asesor de la propiedad en 21Online" : seed.assignment === "ROULETTE" ? "por la ruleta de asignación" : "manualmente";
    list.push({ id: `${id}-a1`, at: at(0.02), kind: "ASSIGNED", text: `Asignado a ${advisorName(seed.advisorId)} ${how}.` });
    list.push({ id: `${id}-a2`, at: at(0.03), kind: "NOTICE", text: "Aviso por WhatsApp enviado al asesor." });
  }
  const stageOrder: LeadStage[] = ["CONTACTED", "APPOINTMENT", "VISITED", "NEGOTIATION"];
  const reached = seed.stage === "WON" || seed.stage === "LOST" ? stageOrder.length : stageOrder.indexOf(seed.stage) + 1;
  for (let i = 0; i < reached && i < Math.min(3, seed.hoursAgo / 8); i++) {
    list.push({ id: `${id}-s${i}`, at: at(2 + i * 6), kind: "STAGE", text: `Etapa cambiada a "${stageText[stageOrder[i]]}".`, by: advisorName(seed.advisorId) });
  }
  if (seed.stage === "WON" || seed.stage === "LOST") {
    list.push({
      id: `${id}-end`,
      at: at(Math.max(1, seed.hoursAgo - 2)),
      kind: "STAGE",
      text: seed.stage === "WON" ? 'Etapa cambiada a "Cerrado ganado".' : `Marcado como perdido: ${seed.lostReason ?? "sin motivo"}.`,
      by: advisorName(seed.advisorId),
    });
  }
  return list.filter((a) => new Date(a.at).getTime() <= Date.now()).reverse();
}

const stageText: Record<LeadStage, string> = {
  NEW: "Nuevo",
  CONTACTED: "Contactado",
  APPOINTMENT: "Cita agendada",
  VISITED: "Visitó",
  NEGOTIATION: "Negociación",
  WON: "Cerrado ganado",
  LOST: "Perdido",
};

export function buildDemoLeads(now = Date.now()): CrmLead[] {
  return seeds.map((seed, index) => {
    const id = `lead-${String(index + 1).padStart(3, "0")}`;
    const createdAt = now - seed.hoursAgo * HOUR;
    const activities = buildActivities(seed, id, createdAt);
    return {
      id,
      name: seed.name,
      phone: seed.phone,
      source: seed.source,
      sourceDetail: seed.sourceDetail,
      interest: seed.interest,
      stage: seed.stage,
      propertyId: seed.propertyId,
      advisorId: seed.advisorId,
      assignment: seed.assignment,
      summary: seed.summary,
      budget: seed.budget,
      createdAt: new Date(createdAt).toISOString(),
      lastContactAt: activities[0]?.at,
      conversationId: seed.conversationId,
      lostReason: seed.lostReason,
      activities,
    };
  });
}

export function buildDemoConversations(now = Date.now()): CrmConversation[] {
  const at = (minutesAgo: number) => new Date(now - minutesAgo * 60_000).toISOString();
  return [
    {
      id: "conv-1",
      leadId: "lead-001",
      contactName: "Daniela Ruiz Peña",
      phone: "+52 777 555 0142",
      control: "AI",
      intent: "Interés en propiedad verificada (GN 14)",
      messages: [
        { id: "m1", from: "CLIENT", text: "Hola, vi esta casa en Facebook, ¿sigue disponible? https://century21mexico.com/propiedad/634294", at: at(26) },
        { id: "m2", from: "BOT", text: "¡Hola! Bienvenida a CENTURY 21 Genera. Soy tu asistente virtual. Sí, la casa en Retorno de Fátima, Burgos (Temixco) sigue disponible: $5,980,000 MXN, 4 recámaras, 4 baños, alberca y 330 m² de construcción.", at: at(25) },
        { id: "m3", from: "CLIENT", text: "¿Aceptan crédito Infonavit?", at: at(24) },
        { id: "m4", from: "BOT", text: "Sí, la propiedad acepta créditos. Te comunico con Aivy Genera, la asesora de esta propiedad, para revisar tu caso y agendar una visita.", at: at(24) },
        { id: "m5", from: "CLIENT", text: "Perfecto, gracias", at: at(23) },
      ],
    },
    {
      id: "conv-2",
      leadId: "lead-003",
      contactName: "Mariana Sotelo",
      phone: "+52 777 555 0115",
      control: "AI",
      intent: "Explorar: compra de casa",
      needsAttention: "Sin asesor disponible en su horario",
      messages: [
        { id: "m1", from: "CLIENT", text: "Buenas noches, busco casa de 3 recámaras", at: at(125) },
        { id: "m2", from: "BOT", text: "¡Hola! Con gusto te ayudo. ¿En qué zona te gustaría y cuál es tu presupuesto aproximado?", at: at(124) },
        { id: "m3", from: "CLIENT", text: "Jiutepec o Temixco, hasta 3.5 millones", at: at(122) },
        { id: "m4", from: "BOT", text: "Encontré estas opciones: casa en Residencial La Palma, Jiutepec ($3,290,000) y casa en Paraje Alarcón, Cuernavaca ($3,000,000). ¿Alguna te interesa para que un asesor te contacte?", at: at(121) },
        { id: "m5", from: "CLIENT", text: "La de La Palma", at: at(118) },
      ],
    },
    {
      id: "conv-3",
      leadId: "lead-005",
      contactName: "Ana Karen Velasco",
      phone: "+52 777 555 0127",
      control: "HUMAN",
      intent: "Quiere vender su propiedad",
      messages: [
        { id: "m1", from: "CLIENT", text: "Hola, quiero vender mi casa, ¿cómo funciona?", at: at(430) },
        { id: "m2", from: "BOT", text: "¡Hola! Te ayudamos a venderla. Un asesor revisará contigo el precio y los documentos. Te asigno a Guadalupe Cruz Molina.", at: at(429) },
        { id: "m3", from: "TEAM", text: "Hola Ana Karen, soy Guadalupe. ¿Me compartes la ubicación de la casa y si tienes las escrituras a la mano?", at: at(400) },
        { id: "m4", from: "CLIENT", text: "Está en Lomas de Cortés, sí tengo escrituras", at: at(380) },
      ],
    },
    {
      id: "conv-4",
      leadId: "lead-008",
      contactName: "Héctor Salgado",
      phone: "+52 777 555 0136",
      control: "PAUSED",
      intent: "Renta en Jiutepec (GM 33)",
      messages: [
        { id: "m1", from: "CLIENT", text: "¿La casa de Tezontepec acepta mascotas?", at: at(1800) },
        { id: "m2", from: "BOT", text: "Déjame confirmarlo con el asesor de la propiedad. Te comunico con Guadalupe Cruz Molina.", at: at(1799) },
        { id: "m3", from: "TEAM", text: "Sí acepta mascotas pequeñas. ¿Te parece una visita el jueves?", at: at(1700) },
      ],
    },
  ];
}
