"use client";

import { buildDemoConversations, buildDemoLeads } from "@/data/crm/leads";
import { crmAdvisors, crmProperties } from "@/data/crm/properties";
import type { ConversationControl, CrmAdvisor, CrmConversation, CrmLead, CrmProperty, LeadActivity, LeadStage } from "@/types/crm";
import { useSyncExternalStore } from "react";
import { stageLabels } from "./labels";

// Almacén en memoria del prototipo del CRM. Solo vive mientras la pestaña
// está abierta: los cambios (etapa, reasignación, notas) se pierden al
// recargar. Cuando exista el backend, estas funciones se sustituyen por
// llamadas a la API con las mismas firmas.

export interface CrmState {
  leads: CrmLead[];
  conversations: CrmConversation[];
  advisors: CrmAdvisor[];
  properties: CrmProperty[];
}

let state: CrmState | null = null;
const listeners = new Set<() => void>();

function current(): CrmState {
  if (!state) {
    const now = Date.now();
    state = { leads: buildDemoLeads(now), conversations: buildDemoConversations(now), advisors: crmAdvisors, properties: crmProperties };
  }
  return state;
}

function update(next: (s: CrmState) => CrmState) {
  state = next(current());
  listeners.forEach((l) => l());
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/**
 * Estado del CRM. Devuelve null durante el render del servidor: los datos de
 * ejemplo usan la hora actual y no deben generarse en el servidor (evita
 * diferencias de hidratación).
 */
export function useCrm(): CrmState | null {
  return useSyncExternalStore(subscribe, current, () => null);
}

const ACTOR = "Tú";

function activity(kind: LeadActivity["kind"], text: string): LeadActivity {
  return { id: `act-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`, at: new Date().toISOString(), kind, text, by: ACTOR };
}

function patchLead(id: string, patch: (lead: CrmLead) => CrmLead) {
  update((s) => ({ ...s, leads: s.leads.map((l) => (l.id === id ? patch(l) : l)) }));
}

export function changeLeadStage(id: string, stage: LeadStage, lostReason?: string) {
  patchLead(id, (lead) => ({
    ...lead,
    stage,
    lostReason: stage === "LOST" ? lostReason : undefined,
    lastContactAt: new Date().toISOString(),
    activities: [
      activity("STAGE", stage === "LOST" ? `Marcado como perdido: ${lostReason || "sin motivo"}.` : `Etapa cambiada a "${stageLabels[stage]}".`),
      ...lead.activities,
    ],
  }));
}

export function reassignLead(id: string, advisorId: string) {
  const advisor = current().advisors.find((a) => a.id === advisorId);
  patchLead(id, (lead) => ({
    ...lead,
    advisorId,
    assignment: "MANUAL",
    activities: [activity(lead.advisorId ? "REASSIGNED" : "ASSIGNED", `Asignado manualmente a ${advisor?.name ?? "otro asesor"}.`), ...lead.activities],
  }));
}

export function addLeadNote(id: string, text: string) {
  patchLead(id, (lead) => ({ ...lead, activities: [activity("NOTE", text), ...lead.activities] }));
}

export function setConversationControl(id: string, control: ConversationControl) {
  update((s) => ({ ...s, conversations: s.conversations.map((c) => (c.id === id ? { ...c, control } : c)) }));
}

export function sendTeamMessage(id: string, text: string) {
  update((s) => ({
    ...s,
    conversations: s.conversations.map((c) =>
      c.id === id
        ? {
            ...c,
            control: "HUMAN",
            messages: [...c.messages, { id: `m-${Date.now()}`, from: "TEAM", text, at: new Date().toISOString() }],
          }
        : c,
    ),
  }));
}

export function toggleAdvisor(id: string, patch: Partial<Pick<CrmAdvisor, "active" | "weight" | "dailyLimit">>) {
  update((s) => ({ ...s, advisors: s.advisors.map((a) => (a.id === id ? { ...a, ...patch } : a)) }));
}
