"use client";

import { Avatar, LoadError, Segmented, errorText, useApi } from "@/components/crm/CrmUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { useToast } from "@/components/ui/Toast";
import {
  getAssistantStatus,
  getConversation,
  listConversations,
  replyToConversation,
  resetSimulation,
  resolveEscalation,
  setConversationControl,
  simulateMessage,
  type AssistantStatus,
  type ConversationControl,
  type ConversationDetail,
  type ConversationItem,
} from "@/lib/api/crm";
import { controlLabels, controlTones, formatTime, timeAgo } from "@/lib/crm/labels";
import { useCan } from "@/lib/permissions";
import { cn } from "@/lib/utils";
import { AlertTriangle, ArrowLeft, Bot, Check, FlaskConical, Pause, Play, RotateCcw, Send, UserRound } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";

type Filter = "all" | "attention" | ConversationControl;
const POLL_MS = 4000;

export default function ConversacionesPage() {
  return (
    <Suspense fallback={null}>
      <ConversationsScreen />
    </Suspense>
  );
}

const modeText: Record<AssistantStatus["mode"], string> = { OFF: "Asistente apagado", TEST_ONLY: "Solo pruebas", ON: "Asistente activo" };

function ConversationsScreen() {
  const status = useApi(() => getAssistantStatus(), []);
  const s = status.data;
  return (
    <PageContainer
      title="Conversaciones"
      subtitle="Lo que el asistente de WhatsApp habla con los clientes: qué entendió, a quién canalizó y qué falta atender."
      action={
        s ? (
          <span
            className={cn(
              "inline-flex h-10 items-center gap-2 rounded-full px-4 text-sm font-medium",
              s.mode === "ON" ? "bg-success-bg text-success-text" : s.mode === "TEST_ONLY" ? "bg-info-bg text-info-text" : "bg-app-bg text-muted",
            )}
          >
            <span className={cn("h-2 w-2 rounded-full", s.mode === "ON" ? "bg-success-text" : s.mode === "TEST_ONLY" ? "bg-info-text" : "bg-medium-grey")} aria-hidden />
            {modeText[s.mode]}
          </span>
        ) : null
      }
    >
      {s ? <SetupNotice status={s} /> : null}
      <Inbox />
    </PageContainer>
  );
}

/** Lo que falta configurar para que el bot funcione con clientes reales. */
function SetupNotice({ status }: { status: AssistantStatus }) {
  const missing = [
    !status.aiConfigured && "la llave de OpenAI (AI_API_KEY)",
    !status.inventoryAvailable && "la llave de 21Online (ONLINE21_API_KEY) para consultar propiedades",
    !status.whatsappConfigured && "WhatsApp Cloud API (WHATSAPP_PHONE_NUMBER_ID y WHATSAPP_ACCESS_TOKEN)",
    !status.webhookConfigured && "el webhook de Meta (WHATSAPP_VERIFY_TOKEN y WHATSAPP_APP_SECRET)",
  ].filter(Boolean) as string[];
  if (missing.length === 0) return null;
  return (
    <p className="mb-5 flex items-start gap-2.5 rounded-xl border border-info-text/20 bg-info-bg px-4 py-3 text-sm text-info-text">
      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
      <span>
        <strong className="font-semibold">Falta configurar en el servidor:</strong> {missing.join("; ")}.
        {status.aiConfigured ? " Mientras tanto puedes probar al asistente con el simulador." : ""}
      </span>
    </p>
  );
}

function Inbox() {
  const params = useSearchParams();
  const router = useRouter();
  const can = useCan();
  const [filter, setFilter] = useState<Filter>("all");
  const selectedId = params.get("c");
  const list = useApi(() => listConversations(), []);
  const reloadList = list.reload;

  useEffect(() => {
    const t = window.setInterval(reloadList, POLL_MS * 2);
    return () => window.clearInterval(t);
  }, [reloadList]);

  const select = (id: string | null) => router.replace(id ? `/conversaciones?c=${id}` : "/conversaciones", { scroll: false });
  const items = list.data ?? [];
  const matches = (c: ConversationItem) => filter === "all" || (filter === "attention" ? !!c.needsAttention || c.openEscalations > 0 : c.control === filter);
  const visible = items.filter(matches);

  return (
    <div className="space-y-4">
      <div className={cn("flex flex-wrap items-center justify-between gap-3", selectedId && "hidden lg:flex")}>
        <Segmented
          label="Filtrar conversaciones"
          value={filter}
          onChange={setFilter}
          options={[
            { id: "all", label: "Todas", count: items.length },
            { id: "attention", label: "Requieren atención", count: items.filter((c) => c.needsAttention || c.openEscalations > 0).length },
            { id: "AI", label: controlLabels.AI, count: items.filter((c) => c.control === "AI").length },
            { id: "HUMAN", label: controlLabels.HUMAN, count: items.filter((c) => c.control === "HUMAN").length },
            { id: "PAUSED", label: controlLabels.PAUSED, count: items.filter((c) => c.control === "PAUSED").length },
          ]}
        />
        {can("CONVERSATION_MANAGE") ? <SimulatorButton onOpened={(id) => { reloadList(); select(id); }} /> : null}
      </div>

      {list.error && !list.data ? (
        <LoadError message={list.error} onRetry={list.reload} />
      ) : (
        <div className="grid grid-cols-1 gap-4 lg:h-[calc(100dvh-20rem)] lg:min-h-[520px] lg:grid-cols-[320px_minmax(0,1fr)] 2xl:grid-cols-[320px_minmax(0,1fr)_300px]">
          <section aria-label="Lista de conversaciones" className={cn("min-h-0 overflow-y-auto rounded-xl border border-border bg-card", selectedId && "hidden lg:block")}>
            {!list.data ? (
              <p className="p-6 text-center text-sm text-muted">Cargando…</p>
            ) : visible.length === 0 ? (
              <p className="p-6 text-center text-sm text-muted">{items.length === 0 ? "Aún no hay conversaciones. Cuando un cliente escriba por WhatsApp aparecerá aquí." : "Sin conversaciones en este filtro."}</p>
            ) : (
              <ul className="divide-y divide-border">
                {visible.map((c) => (
                  <li key={c.id}>
                    <button
                      type="button"
                      onClick={() => select(c.id)}
                      aria-current={c.id === selectedId ? "true" : undefined}
                      className={cn(
                        "flex w-full gap-3 px-4 py-3 text-left transition-colors focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-inset focus-visible:ring-gold/30",
                        c.id === selectedId ? "bg-gold/10" : "hover:bg-app-bg",
                      )}
                    >
                      <Avatar name={c.contactName ?? "?"} />
                      <span className="min-w-0 flex-1">
                        <span className="flex items-baseline justify-between gap-2">
                          <span className="truncate text-sm font-semibold text-obsessed">{c.contactName ?? c.phone ?? "Contacto"}</span>
                          {c.lastMessageAt ? <span className="shrink-0 text-xs text-muted">{timeAgo(c.lastMessageAt)}</span> : null}
                        </span>
                        <span className="block truncate text-xs text-muted">
                          {c.lastMessageRole === "BOT" ? "IA: " : c.lastMessageRole === "TEAM" ? "Equipo: " : ""}
                          {c.lastMessage}
                        </span>
                        <span className="mt-1.5 flex flex-wrap gap-1.5">
                          <Badge tone={controlTones[c.control]} className="py-0.5">
                            {controlLabels[c.control]}
                          </Badge>
                          {c.test ? (
                            <Badge tone="manual" className="py-0.5">
                              <FlaskConical aria-hidden /> Prueba
                            </Badge>
                          ) : null}
                          {c.needsAttention || c.openEscalations > 0 ? (
                            <Badge tone="warning" className="py-0.5">
                              <AlertTriangle aria-hidden /> Atención
                            </Badge>
                          ) : null}
                        </span>
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>

          {selectedId ? (
            <ConversationPanes id={selectedId} onBack={() => select(null)} onChanged={reloadList} />
          ) : (
            <div className="hidden items-center justify-center rounded-xl border border-dashed border-border bg-card lg:flex 2xl:col-span-2">
              <p className="text-sm text-muted">Elige una conversación para ver el historial.</p>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function SimulatorButton({ onOpened }: { onOpened: (id: string) => void }) {
  const { showToast } = useToast();
  const [busy, setBusy] = useState(false);
  return (
    <Button
      variant="secondary"
      disabled={busy}
      onClick={async () => {
        setBusy(true);
        try {
          const res = await simulateMessage("Hola");
          if (res.conversationId) onOpened(res.conversationId);
        } catch (err) {
          showToast(errorText(err));
        } finally {
          setBusy(false);
        }
      }}
    >
      <FlaskConical className="h-4 w-4" aria-hidden /> Probar el asistente
    </Button>
  );
}

function ConversationPanes({ id, onBack, onChanged }: { id: string; onBack: () => void; onChanged: () => void }) {
  const detail = useApi(() => getConversation(id), [id]);
  const reload = detail.reload;
  useEffect(() => {
    const t = window.setInterval(reload, POLL_MS);
    return () => window.clearInterval(t);
  }, [reload]);

  if (detail.error && !detail.data) {
    return (
      <div className="2xl:col-span-2">
        <LoadError message={detail.error} onRetry={detail.reload} />
      </div>
    );
  }
  if (!detail.data) {
    return <div className="flex items-center justify-center rounded-xl border border-border bg-card p-6 text-sm text-muted 2xl:col-span-2">Cargando…</div>;
  }
  const update = (next: ConversationDetail) => {
    detail.setData(next);
    onChanged();
  };
  return (
    <>
      <ChatPane detail={detail.data} onBack={onBack} onUpdate={update} />
      <ContextPane detail={detail.data} onUpdate={update} onResolved={reload} />
    </>
  );
}

function ChatPane({ detail, onBack, onUpdate }: { detail: ConversationDetail; onBack: () => void; onUpdate: (d: ConversationDetail) => void }) {
  const c = detail.conversation;
  const can = useCan();
  const { showToast } = useToast();
  const [text, setText] = useState("");
  const [sending, setSending] = useState(false);
  const bottom = useRef<HTMLLIElement>(null);
  const count = detail.messages.length;

  useEffect(() => {
    bottom.current?.scrollIntoView({ block: "end" });
  }, [count]);

  const send = async () => {
    const value = text.trim();
    if (!value) return;
    setSending(true);
    try {
      if (c.test && c.control === "AI") {
        // En la conversación de prueba escribes como si fueras el cliente.
        await simulateMessage(value);
      } else {
        onUpdate(await replyToConversation(c.id, value));
      }
      setText("");
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setSending(false);
    }
  };

  const asClient = c.test && c.control === "AI";
  return (
    <section aria-label={`Conversación con ${c.contactName ?? "contacto"}`} className="flex min-h-[480px] flex-col overflow-hidden rounded-xl border border-border bg-card lg:min-h-0">
      <header className="flex items-center gap-3 border-b border-border px-4 py-3">
        <button
          type="button"
          onClick={onBack}
          aria-label="Volver a la lista"
          className="-ml-1 rounded-lg p-2 text-obsessed hover:bg-app-bg focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30 lg:hidden"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden />
        </button>
        <Avatar name={c.contactName ?? "?"} />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-semibold text-obsessed">{c.contactName ?? "Contacto"}</p>
          <p className="text-xs tabular-nums text-muted">{c.test ? "Conversación de prueba (no se envía por WhatsApp)" : c.phone}</p>
        </div>
        <Badge tone={controlTones[c.control]}>{controlLabels[c.control]}</Badge>
      </header>

      <ol className="flex-1 space-y-3 overflow-y-auto bg-app-bg/60 px-4 py-4">
        {detail.messages.map((m) => {
          const mine = m.role !== "CLIENT";
          return (
            <li key={m.id} className={cn("flex", mine ? "justify-end" : "justify-start")}>
              <div
                className={cn(
                  "max-w-[80%] rounded-2xl px-3.5 py-2 text-sm shadow-[0_1px_1px_rgba(0,0,0,0.04)]",
                  m.role === "CLIENT" ? "rounded-bl-md bg-card text-obsessed" : m.role === "BOT" ? "rounded-br-md bg-gold/20 text-obsessed" : "rounded-br-md bg-brand-ink text-white",
                )}
              >
                <span className={cn("mb-0.5 flex items-center gap-1 text-[0.68rem] font-medium uppercase tracking-wide", m.role === "TEAM" ? "text-white/70" : "text-muted")}>
                  {m.role === "BOT" ? <Bot className="h-3 w-3" aria-hidden /> : m.role === "TEAM" ? <UserRound className="h-3 w-3" aria-hidden /> : null}
                  {m.role === "CLIENT" ? "Cliente" : m.role === "BOT" ? "Asistente" : "Equipo"} · {formatTime(m.createdAt)}
                </span>
                <span className="whitespace-pre-wrap break-words">{m.text}</span>
                {m.status === "FAILED" ? <span className="mt-1 block text-[0.7rem] text-danger-text">No se envió: {m.error}</span> : null}
              </div>
            </li>
          );
        })}
        <li ref={bottom} aria-hidden />
      </ol>

      {can("CONVERSATION_MANAGE") ? (
        <form
          className="flex items-end gap-2 border-t border-border p-3"
          onSubmit={(e) => {
            e.preventDefault();
            void send();
          }}
        >
          <label htmlFor="team-reply" className="sr-only">
            {asClient ? "Escribir como cliente" : "Responder como equipo"}
          </label>
          <textarea
            id="team-reply"
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                void send();
              }
            }}
            rows={1}
            placeholder={asClient ? "Escribe como si fueras el cliente…" : "Responder como equipo (pausa la IA en esta conversación)"}
            className="max-h-32 min-h-10 flex-1 resize-none rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20"
          />
          <Button type="submit" disabled={!text.trim() || sending} aria-label="Enviar">
            <Send className="h-4 w-4" aria-hidden />
          </Button>
        </form>
      ) : null}
    </section>
  );
}

function ContextPane({ detail, onUpdate, onResolved }: { detail: ConversationDetail; onUpdate: (d: ConversationDetail) => void; onResolved: () => void }) {
  const router = useRouter();
  const c = detail.conversation;
  const can = useCan();
  const { showToast } = useToast();
  const open = detail.escalations.filter((e) => e.status === "OPEN");
  const setControl = async (control: ConversationControl) => {
    try {
      onUpdate(await setConversationControl(c.id, control));
    } catch (err) {
      showToast(errorText(err));
    }
  };
  return (
    <aside aria-label="Contexto" className="hidden min-h-0 space-y-4 overflow-y-auto rounded-xl border border-border bg-card p-4 2xl:block">
      <div>
        <p className="text-xs font-medium uppercase tracking-wide text-muted">Lo que entendió</p>
        <p className="mt-1 text-sm text-obsessed">{c.intent}</p>
      </div>
      {detail.summary ? (
        <div>
          <p className="text-xs font-medium uppercase tracking-wide text-muted">Resumen para el asesor</p>
          <p className="mt-1 text-sm text-obsessed">{detail.summary}</p>
        </div>
      ) : null}
      {c.needsAttention ? (
        <p className="flex items-start gap-2 rounded-xl bg-warning-bg px-3 py-2 text-sm text-warning-text">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
          {c.needsAttention}
        </p>
      ) : null}
      {open.length > 0 ? (
        <div>
          <p className="mb-2 text-xs font-medium uppercase tracking-wide text-muted">Pendientes</p>
          <ul className="space-y-2">
            {open.map((e) => (
              <li key={e.id} className="rounded-xl border border-border p-2.5 text-sm">
                <p className="text-obsessed">{e.reason}</p>
                {can("CONVERSATION_MANAGE") ? (
                  <button
                    type="button"
                    className="mt-1 inline-flex items-center gap-1 text-xs font-medium text-dark-gold hover:text-obsessed"
                    onClick={async () => {
                      await resolveEscalation(e.id);
                      onResolved();
                    }}
                  >
                    <Check className="h-3.5 w-3.5" aria-hidden /> Marcar atendido
                  </button>
                ) : null}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
      {c.leadId ? (
        <Link href={`/leads/${c.leadId}`} className="inline-flex text-sm font-medium text-dark-gold hover:text-obsessed">
          Ver lead →
        </Link>
      ) : null}
      {can("CONVERSATION_MANAGE") ? (
        <div className="space-y-2 border-t border-border pt-4">
          <p className="text-xs font-medium uppercase tracking-wide text-muted">Control de la IA</p>
          {c.control === "AI" ? (
            <Button variant="secondary" size="sm" onClick={() => void setControl("PAUSED")}>
              <Pause className="h-4 w-4" aria-hidden /> Pausar IA
            </Button>
          ) : (
            <Button variant="secondary" size="sm" onClick={() => void setControl("AI")}>
              <Play className="h-4 w-4" aria-hidden /> Reanudar IA
            </Button>
          )}
          <p className="text-xs text-muted">Con la IA en pausa el cliente solo recibe lo que escriba el equipo. Al reanudarla no contesta los mensajes que llegaron durante la pausa.</p>
          {c.test ? (
            <Button
              variant="ghost"
              size="sm"
              onClick={async () => {
                await resetSimulation();
                showToast("Conversación de prueba reiniciada");
                router.replace("/conversaciones");
              }}
            >
              <RotateCcw className="h-4 w-4" aria-hidden /> Reiniciar prueba
            </Button>
          ) : null}
        </div>
      ) : null}
    </aside>
  );
}
