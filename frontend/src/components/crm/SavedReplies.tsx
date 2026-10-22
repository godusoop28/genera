"use client";

import { errorText, useApi } from "@/components/crm/CrmUi";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { SearchInput } from "@/components/ui/SearchInput";
import { useToast } from "@/components/ui/Toast";
import { createSavedReply, deleteSavedReply, listSavedReplies, updateSavedReply, type SavedReply } from "@/lib/api/crm";
import { cn } from "@/lib/utils";
import { MessageSquareText, Pencil, Plus, Trash2 } from "lucide-react";
import { useEffect, useRef, useState, type ReactNode } from "react";

const textareaClass =
  "w-full resize-y rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20";

/**
 * Botón junto a la caja de respuesta: elige una respuesta guardada para insertarla
 * (se puede editar antes de enviar). Las respuestas son de toda la oficina.
 */
export function SavedRepliesButton({ draft, onPick }: { draft: string; onPick: (text: string) => void }) {
  const replies = useApi(() => listSavedReplies(), []);
  const [open, setOpen] = useState(false);
  const [managing, setManaging] = useState(false);
  const [query, setQuery] = useState("");
  const box = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => {
      if (box.current && !box.current.contains(e.target as Node)) setOpen(false);
    };
    const esc = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("mousedown", close);
    document.addEventListener("keydown", esc);
    return () => {
      document.removeEventListener("mousedown", close);
      document.removeEventListener("keydown", esc);
    };
  }, [open]);

  const q = query.trim().toLowerCase();
  const list = (replies.data ?? []).filter((r) => !q || r.title.toLowerCase().includes(q) || r.text.toLowerCase().includes(q));

  return (
    <div ref={box} className="relative">
      <Button type="button" variant="secondary" onClick={() => setOpen((o) => !o)} aria-expanded={open} aria-label="Respuestas guardadas" title="Respuestas guardadas">
        <MessageSquareText className="h-4 w-4" aria-hidden />
      </Button>
      {open ? (
        <div className="absolute bottom-full right-0 z-20 mb-2 w-[min(22rem,calc(100vw-2rem))] rounded-xl border border-border bg-card p-3 shadow-lg">
          <SearchInput value={query} onChange={setQuery} label="Buscar respuesta" />
          <ul className="mt-2 max-h-64 space-y-1 overflow-y-auto">
            {!replies.data ? (
              <li className="px-2 py-3 text-center text-xs text-muted">{replies.error ?? "Cargando…"}</li>
            ) : list.length === 0 ? (
              <li className="px-2 py-3 text-center text-xs text-muted">{replies.data.length === 0 ? "Aún no hay respuestas guardadas." : "Sin coincidencias."}</li>
            ) : (
              list.map((r) => (
                <li key={r.id}>
                  <button
                    type="button"
                    onClick={() => {
                      onPick(r.text);
                      setOpen(false);
                    }}
                    className="w-full rounded-lg px-2.5 py-2 text-left hover:bg-app-bg focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30"
                  >
                    <span className="block truncate text-sm font-medium text-obsessed">{r.title}</span>
                    <span className="line-clamp-2 text-xs text-muted">{r.text}</span>
                  </button>
                </li>
              ))
            )}
          </ul>
          <div className="mt-2 border-t border-border pt-2">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => {
                setOpen(false);
                setManaging(true);
              }}
            >
              <Pencil className="h-4 w-4" aria-hidden /> Administrar respuestas
            </Button>
          </div>
        </div>
      ) : null}
      {managing ? <ManageSavedReplies replies={replies.data ?? []} draft={draft} onClose={() => setManaging(false)} onChanged={replies.reload} /> : null}
    </div>
  );
}

function ManageSavedReplies({ replies, draft, onClose, onChanged }: { replies: SavedReply[]; draft: string; onClose: () => void; onChanged: () => void }) {
  const { showToast } = useToast();
  // null = lista; "new" = alta; id = edición.
  const [editing, setEditing] = useState<string | null>(draft.trim() ? "new" : null);
  const [title, setTitle] = useState("");
  const [text, setText] = useState(draft.trim());
  const [busy, setBusy] = useState(false);

  const edit = (r: SavedReply | null) => {
    setEditing(r ? r.id : "new");
    setTitle(r?.title ?? "");
    setText(r?.text ?? "");
  };

  const run = async (action: () => Promise<unknown>, done: string) => {
    setBusy(true);
    try {
      await action();
      onChanged();
      setEditing(null);
      showToast(done);
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const save = () =>
    run(() => (editing === "new" ? createSavedReply(title.trim(), text.trim()) : updateSavedReply(editing!, title.trim(), text.trim())), "Respuesta guardada");

  return (
    <Modal
      open
      onClose={onClose}
      size="lg"
      title="Respuestas guardadas"
      footer={
        editing ? (
          <>
            <Button variant="ghost" onClick={() => setEditing(null)} disabled={busy}>
              Cancelar
            </Button>
            <Button onClick={save} disabled={busy || !title.trim() || !text.trim()}>
              {busy ? "Guardando…" : "Guardar"}
            </Button>
          </>
        ) : (
          <>
            <Button variant="ghost" onClick={onClose}>
              Cerrar
            </Button>
            <Button onClick={() => edit(null)}>
              <Plus className="h-4 w-4" aria-hidden /> Nueva respuesta
            </Button>
          </>
        )
      }
    >
      {editing ? (
        <div className="space-y-3">
          <Input label="Nombre corto" value={title} maxLength={80} onChange={(e) => setTitle(e.target.value)} placeholder="Ej. Horario de visitas" />
          <div className="flex flex-col gap-1.5">
            <label htmlFor="saved-reply-text" className="text-sm font-medium text-obsessed">
              Mensaje
            </label>
            <textarea id="saved-reply-text" rows={5} maxLength={2000} value={text} onChange={(e) => setText(e.target.value)} className={textareaClass} />
          </div>
          <p className="text-xs text-muted">La ve todo el equipo. Al usarla se puede ajustar antes de enviar.</p>
        </div>
      ) : replies.length === 0 ? (
        <p className="text-sm text-muted">Aún no hay respuestas guardadas. Crea las que más repiten (horarios, requisitos, ubicación de la oficina…).</p>
      ) : (
        <ul className="divide-y divide-border">
          {replies.map((r) => (
            <li key={r.id} className="flex items-start gap-3 py-3">
              <div className="min-w-0 flex-1">
                <p className="text-sm font-medium text-obsessed">{r.title}</p>
                <p className="line-clamp-3 whitespace-pre-line text-xs text-muted">{r.text}</p>
                {r.createdBy ? <p className="mt-1 text-[0.7rem] text-muted">Creada por {r.createdBy}</p> : null}
              </div>
              <div className="flex shrink-0 gap-1">
                <IconButton label={`Editar ${r.title}`} onClick={() => edit(r)} disabled={busy}>
                  <Pencil className="h-4 w-4" aria-hidden />
                </IconButton>
                <IconButton label={`Borrar ${r.title}`} danger onClick={() => run(() => deleteSavedReply(r.id), "Respuesta borrada")} disabled={busy}>
                  <Trash2 className="h-4 w-4" aria-hidden />
                </IconButton>
              </div>
            </li>
          ))}
        </ul>
      )}
    </Modal>
  );
}

function IconButton({ label, onClick, disabled, danger, children }: { label: string; onClick: () => void; disabled?: boolean; danger?: boolean; children: ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      title={label}
      className={cn(
        "flex h-9 w-9 items-center justify-center rounded-lg text-muted hover:bg-app-bg focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30 disabled:opacity-50",
        danger ? "hover:text-danger-text" : "hover:text-obsessed",
      )}
    >
      {children}
    </button>
  );
}
