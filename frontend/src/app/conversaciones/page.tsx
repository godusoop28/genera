"use client";

import { Avatar, PreviewNotice, Segmented } from "@/components/crm/CrmUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { StateMessage } from "@/components/ui/StateMessage";
import { controlLabels, controlTones, formatTime, timeAgo } from "@/lib/crm/labels";
import { sendTeamMessage, setConversationControl, useCrm, type CrmState } from "@/lib/crm/store";
import { cn } from "@/lib/utils";
import type { ConversationControl, CrmConversation } from "@/types/crm";
import { AlertTriangle, ArrowLeft, Bot, Pause, Play, Send, UserRound } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState } from "react";

type Filter = "all" | "attention" | ConversationControl;

export default function ConversacionesPage() {
  return (
    <Suspense fallback={null}>
      <ConversationsScreen />
    </Suspense>
  );
}

function ConversationsScreen() {
  const crm = useCrm();
  return (
    <PageContainer
      title="Conversaciones"
      subtitle="Lo que el asistente de WhatsApp habla con los clientes: qué entendió, a quién canalizó y qué falta atender."
      action={
        <span className="inline-flex h-10 items-center gap-2 rounded-full bg-app-bg px-4 text-sm font-medium text-muted">
          <span className="h-2 w-2 rounded-full bg-medium-grey" aria-hidden />
          Bot sin conectar
        </span>
      }
    >
      <PreviewNotice>
        Conversaciones de ejemplo. El bot se conectará por WhatsApp Cloud API (Meta) cuando el número quede verificado; mientras tanto nada de esto se envía.
      </PreviewNotice>
      {crm ? <Inbox crm={crm} /> : <StateMessage kind="loading" title="Cargando conversaciones…" />}
    </PageContainer>
  );
}

function Inbox({ crm }: { crm: CrmState }) {
  const params = useSearchParams();
  const router = useRouter();
  const [filter, setFilter] = useState<Filter>("all");
  const selectedId = params.get("c");

  const matches = (c: CrmConversation) => filter === "all" || (filter === "attention" ? !!c.needsAttention : c.control === filter);
  const list = [...crm.conversations].filter(matches).sort((a, b) => last(b).at.localeCompare(last(a).at));
  const selected = crm.conversations.find((c) => c.id === selectedId) ?? null;
  const select = (id: string | null) => router.replace(id ? `/conversaciones?c=${id}` : "/conversaciones", { scroll: false });

  return (
    <div className="space-y-4">
      <div className={cn(selected && "hidden lg:block")}>
        <Segmented
          label="Filtrar conversaciones"
          value={filter}
          onChange={setFilter}
          options={[
            { id: "all", label: "Todas", count: crm.conversations.length },
            { id: "attention", label: "Requieren atención", count: crm.conversations.filter((c) => c.needsAttention).length },
            { id: "AI", label: controlLabels.AI, count: crm.conversations.filter((c) => c.control === "AI").length },
            { id: "HUMAN", label: controlLabels.HUMAN, count: crm.conversations.filter((c) => c.control === "HUMAN").length },
            { id: "PAUSED", label: controlLabels.PAUSED, count: crm.conversations.filter((c) => c.control === "PAUSED").length },
          ]}
        />
      </div>

      <div className="grid grid-cols-1 gap-4 lg:h-[calc(100dvh-20rem)] lg:min-h-[520px] lg:grid-cols-[320px_minmax(0,1fr)] 2xl:grid-cols-[320px_minmax(0,1fr)_300px]">
        <section aria-label="Lista de conversaciones" className={cn("min-h-0 overflow-y-auto rounded-xl border border-border bg-card", selected && "hidden lg:block")}>
          {list.length === 0 ? (
            <p className="p-6 text-center text-sm text-muted">Sin conversaciones en este filtro.</p>
          ) : (
            <ul className="divide-y divide-border">
              {list.map((c) => {
                const msg = last(c);
                const active = c.id === selected?.id;
                return (
                  <li key={c.id}>
                    <button
                      type="button"
                      onClick={() => select(c.id)}
                      aria-current={active ? "true" : undefined}
                      className={cn(
                        "flex w-full gap-3 px-4 py-3 text-left transition-colors focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-inset focus-visible:ring-gold/30",
                        active ? "bg-gold/10" : "hover:bg-app-bg",
                      )}
                    >
                      <Avatar name={c.contactName} />
                      <span className="min-w-0 flex-1">
                        <span className="flex items-baseline justify-between gap-2">
                          <span className="truncate text-sm font-semibold text-obsessed">{c.contactName}</span>
                          <span className="shrink-0 text-xs text-muted">{timeAgo(msg.at)}</span>
                        </span>
                        <span className="block truncate text-xs text-muted">
                          {msg.from === "BOT" ? "IA: " : msg.from === "TEAM" ? "Equipo: " : ""}
                          {msg.text}
                        </span>
                        <span className="mt-1.5 flex flex-wrap gap-1.5">
                          <Badge tone={controlTones[c.control]} className="py-0.5">
                            {controlLabels[c.control]}
                          </Badge>
                          {c.needsAttention ? (
                            <Badge tone="warning" className="py-0.5">
                              <AlertTriangle aria-hidden /> Atención
                            </Badge>
                          ) : null}
                        </span>
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </section>

        {selected ? (
          <>
            <ChatPane conversation={selected} onBack={() => select(null)} />
            <ContextPane conversation={selected} crm={crm} />
          </>
        ) : (
          <div className="hidden items-center justify-center rounded-xl border border-dashed border-border bg-card lg:flex 2xl:col-span-2">
            <p className="text-sm text-muted">Elige una conversación para ver el historial.</p>
          </div>
        )}
      </div>
    </div>
  );
}

function last(c: CrmConversation) {
  return c.messages[c.messages.length - 1];
}

function ChatPane({ conversation, onBack }: { conversation: CrmConversation; onBack: () => void }) {
  const [text, setText] = useState("");
  const send = () => {
    if (!text.trim()) return;
    sendTeamMessage(conversation.id, text.trim());
    setText("");
  };
  return (
    <section aria-label={`Conversación con ${conversation.contactName}`} className="flex min-h-[480px] flex-col overflow-hidden rounded-xl border border-border bg-card lg:min-h-0">
      <header className="flex items-center gap-3 border-b border-border px-4 py-3">
        <button
          type="button"
          onClick={onBack}
          aria-label="Volver a la lista"
          className="-ml-1 rounded-lg p-2 text-obsessed hover:bg-app-bg focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30 lg:hidden"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden />
        </button>
        <Avatar name={conversation.contactName} />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold text-obsessed">{conversation.contactName}</p>
          <p className="text-xs tabular-nums text-muted">{conversation.phone}</p>
        </div>
        <Badge tone={controlTones[conversation.control]}>{controlLabels[conversation.control]}</Badge>
      </header>

      <ol className="flex-1 space-y-3 overflow-y-auto bg-app-bg/60 px-4 py-4">
        {conversation.messages.map((m) => {
          const mine = m.from !== "CLIENT";
          return (
            <li key={m.id} className={cn("flex", mine ? "justify-end" : "justify-start")}>
              <div
                className={cn(
                  "max-w-[80%] rounded-2xl px-3.5 py-2 text-sm shadow-[0_1px_1px_rgba(0,0,0,0.04)]",
                  m.from === "CLIENT" ? "rounded-bl-md bg-card text-obsessed" : m.from === "BOT" ? "rounded-br-md bg-gold/20 text-obsessed" : "rounded-br-md bg-brand-ink text-white",
                )}
              >
                <span className={cn("mb-0.5 flex items-center gap-1 text-[0.68rem] font-medium uppercase tracking-wide", m.from === "TEAM" ? "text-white/70" : "text-muted")}>
                  {m.from === "BOT" ? <Bot className="h-3 w-3" aria-hidden /> : m.from === "TEAM" ? <UserRound className="h-3 w-3" aria-hidden /> : null}
                  {m.from === "CLIENT" ? "Cliente" : m.from === "BOT" ? "Asistente" : "Equipo"} · {formatTime(m.at)}
                </span>
                <span className="whitespace-pre-wrap break-words">{m.text}</span>
              </div>
            </li>
          );
        })}
      </ol>

      <form
        className="flex items-end gap-2 border-t border-border p-3"
        onSubmit={(e) => {
          e.preventDefault();
          send();
        }}
      >
        <label htmlFor="team-reply" className="sr-only">
          Responder como equipo
        </label>
        <textarea
          id="team-reply"
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              send();
            }
          }}
          rows={1}
          placeholder="Responder como equipo (pausa la IA en esta conversación)"
          className="max-h-32 min-h-10 flex-1 resize-none rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20"
        />
        <Button type="submit" disabled={!text.trim()} aria-label="Enviar">
          <Send className="h-4 w-4" aria-hidden />
        </Button>
      </form>
    </section>
  );
}

function ContextPane({ conversation, crm }: { conversation: CrmConversation; crm: CrmState }) {
  const lead = conversation.leadId ? crm.leads.find((l) => l.id === conversation.leadId) : undefined;
  const advisor = lead?.advisorId ? crm.advisors.find((a) => a.id === lead.advisorId) : undefined;
  return (
    <aside aria-label="Contexto" className="hidden min-h-0 space-y-4 overflow-y-auto rounded-xl border border-border bg-card p-4 2xl:block">
      <div>
        <p className="text-xs font-medium uppercase tracking-wide text-muted">Lo que entendió</p>
        <p className="mt-1 text-sm text-obsessed">{conversation.intent}</p>
      </div>
      {conversation.needsAttention ? (
        <p className="flex items-start gap-2 rounded-xl bg-warning-bg px-3 py-2 text-sm text-warning-text">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
          {conversation.needsAttention}
        </p>
      ) : null}
      <div>
        <p className="text-xs font-medium uppercase tracking-wide text-muted">Asesor</p>
        <p className="mt-1 text-sm text-obsessed">{advisor?.name ?? "Sin asignar"}</p>
      </div>
      {lead ? (
        <Link href={`/leads/${lead.id}`} className="inline-flex text-sm font-medium text-dark-gold hover:text-obsessed">
          Ver lead →
        </Link>
      ) : null}
      <div className="border-t border-border pt-4">
        <p className="mb-2 text-xs font-medium uppercase tracking-wide text-muted">Control de la IA</p>
        {conversation.control === "AI" ? (
          <Button variant="secondary" size="sm" onClick={() => setConversationControl(conversation.id, "PAUSED")}>
            <Pause className="h-4 w-4" aria-hidden /> Pausar IA
          </Button>
        ) : (
          <Button variant="secondary" size="sm" onClick={() => setConversationControl(conversation.id, "AI")}>
            <Play className="h-4 w-4" aria-hidden /> Reanudar IA
          </Button>
        )}
        <p className="mt-2 text-xs text-muted">Con la IA en pausa el cliente solo recibe lo que escriba el equipo.</p>
      </div>
    </aside>
  );
}
