"use client";

import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { useToast } from "@/components/ui/Toast";
import { askFeasibilityChat, getFeasibilityChat, type FeasibilityChatMessage } from "@/lib/api/feasibility";
import { errorText } from "@/lib/errors";
import { formatDateTime } from "@/lib/labels";
import { cn } from "@/lib/utils";
import { Loader2, SendHorizontal, Sparkles } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { SimpleMarkdown } from "./SimpleMarkdown";

const SUGGESTIONS = [
  "¿Qué escuelas, hospitales y centros comerciales hay cerca y a qué distancia?",
  "Redacta un mensaje para el propietario explicando el precio recomendado.",
  "¿Cómo respondo si el comprador dice que el precio está alto?",
  "¿Cuánto se rentaría este inmueble y qué rendimiento anual daría?",
];

/**
 * Chat de consultas adicionales sobre una factibilidad: la IA responde con el reporte, los datos
 * del inmueble y búsqueda web. Las respuestas tardan de segundos a un par de minutos; mientras
 * tanto se consulta el chat cada pocos segundos.
 */
export function FeasibilityChat({ reportId, versionNumber, open, onClose }: { reportId: string; versionNumber: number; open: boolean; onClose: () => void }) {
  const { showToast } = useToast();
  const [messages, setMessages] = useState<FeasibilityChatMessage[] | null>(null);
  const [text, setText] = useState("");
  const [sending, setSending] = useState(false);
  const bottom = useRef<HTMLDivElement>(null);

  const pending = (messages ?? []).some((m) => m.status === "PENDING");

  const load = useCallback(
    () =>
      getFeasibilityChat(reportId)
        .then(setMessages)
        .catch((err) => showToast(errorText(err))),
    [reportId, showToast],
  );

  useEffect(() => {
    if (open) load();
  }, [open, load]);

  useEffect(() => {
    if (!open || !pending) return;
    const timer = window.setInterval(load, 4000);
    return () => window.clearInterval(timer);
  }, [open, pending, load]);

  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [messages?.length, pending]);

  const send = async (question: string) => {
    const q = question.trim();
    if (!q) return;
    setSending(true);
    try {
      setMessages(await askFeasibilityChat(reportId, q));
      setText("");
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setSending(false);
    }
  };

  return (
    <Modal open={open} onClose={onClose} size="xl" title={`Consultas sobre la factibilidad v${versionNumber}`}>
      <div className="flex h-[65vh] flex-col">
        <div className="flex-1 space-y-3 overflow-y-auto rounded-lg bg-app-bg p-3">
          {messages === null ? (
            <p className="text-sm text-muted">Cargando…</p>
          ) : messages.length === 0 ? (
            <div className="text-sm text-muted">
              <p className="mb-2">
                Pregunta lo que necesites sobre la propiedad o la factibilidad. La IA usa el reporte, los datos y documentos del expediente y, si hace falta,
                investiga en internet.
              </p>
              <div className="flex flex-col gap-1.5">
                {SUGGESTIONS.map((s) => (
                  <button
                    key={s}
                    type="button"
                    onClick={() => setText(s)}
                    className="rounded-lg border border-border bg-card px-3 py-2 text-left text-obsessed hover:border-gold"
                  >
                    {s}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            messages.map((m) => <Bubble key={m.id} message={m} />)
          )}
          <div ref={bottom} />
        </div>
        <form
          className="mt-3 flex items-end gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            send(text);
          }}
        >
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                if (!sending && !pending) send(text);
              }
            }}
            rows={2}
            maxLength={4000}
            placeholder={pending ? "La IA está respondiendo…" : "Escribe tu pregunta (Enter para enviar, Shift+Enter para nueva línea)"}
            aria-label="Pregunta"
            className="min-h-11 flex-1 resize-none rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm text-obsessed outline-none focus:border-gold"
          />
          <Button type="submit" disabled={sending || pending || !text.trim()} aria-label="Enviar">
            {sending ? <Loader2 className="h-4 w-4 animate-spin" /> : <SendHorizontal className="h-4 w-4" />}
          </Button>
        </form>
        <p className="mt-1.5 text-xs text-muted">Respuestas de apoyo elaboradas con IA: verifica los datos importantes antes de usarlos con el cliente.</p>
      </div>
    </Modal>
  );
}

function Bubble({ message: m }: { message: FeasibilityChatMessage }) {
  const user = m.role === "USER";
  return (
    <div className={cn("flex", user ? "justify-end" : "justify-start")}>
      <div className={cn("max-w-[88%] rounded-2xl px-3.5 py-2.5", user ? "bg-brand-ink text-white" : "border border-border bg-card")}>
        {user ? (
          <p className="whitespace-pre-wrap text-sm">{m.content}</p>
        ) : m.status === "PENDING" ? (
          <p className="flex items-center gap-2 text-sm text-muted">
            <Sparkles className="h-4 w-4 animate-pulse text-gold" /> Analizando… puede tardar hasta un par de minutos si investiga en internet.
          </p>
        ) : m.status === "FAILED" ? (
          <p className="text-sm text-danger-text">{m.error ?? "La IA no pudo responder."} Vuelve a preguntar.</p>
        ) : (
          <>
            <SimpleMarkdown text={m.content ?? ""} />
            {m.sources.length > 0 ? (
              <details className="mt-2 text-xs text-muted">
                <summary className="cursor-pointer">Fuentes ({m.sources.length})</summary>
                <ul className="ml-4 mt-1 list-disc">
                  {m.sources.map((s) => (
                    <li key={s.url}>
                      <a href={s.url} target="_blank" rel="noopener noreferrer" className="text-info-text underline">
                        {s.title || s.url}
                      </a>
                    </li>
                  ))}
                </ul>
              </details>
            ) : null}
          </>
        )}
        <p className={cn("mt-1 text-[10px]", user ? "text-white/60" : "text-muted")}>
          {user && m.authorName ? `${m.authorName} · ` : ""}
          {formatDateTime(m.createdAt)}
        </p>
      </div>
    </div>
  );
}
