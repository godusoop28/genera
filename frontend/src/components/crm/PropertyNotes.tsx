"use client";

import { errorText } from "@/components/crm/CrmUi";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { useToast } from "@/components/ui/Toast";
import { updatePropertyNotes, type CapturedProperty } from "@/lib/api/properties";
import { Lock, Pencil, Tag, X } from "lucide-react";
import { useState } from "react";

const MAX_TAGS = 15;

export function TagChips({ tags, className }: { tags: string[]; className?: string }) {
  if (tags.length === 0) return null;
  return (
    <ul className={className ?? "flex flex-wrap gap-1.5"} aria-label="Etiquetas">
      {tags.map((t) => (
        <li key={t} className="inline-flex items-center gap-1 rounded-full bg-gold/15 px-2.5 py-0.5 text-xs font-medium text-dark-gold">
          <Tag className="h-3 w-3" aria-hidden /> {t}
        </li>
      ))}
    </ul>
  );
}

/** Notas internas y etiquetas: solo se ven en Genera, nunca se mandan a 21Online. */
export function PropertyNotes({ property: p, onChange }: { property: CapturedProperty; onChange: (p: CapturedProperty) => void }) {
  const { showToast } = useToast();
  const [editing, setEditing] = useState(false);
  const [notes, setNotes] = useState("");
  const [tags, setTags] = useState<string[]>([]);
  const [draftTag, setDraftTag] = useState("");
  const [saving, setSaving] = useState(false);
  const tagList = p.tags ?? [];

  const start = () => {
    setNotes(p.privateNotes ?? "");
    setTags(tagList);
    setDraftTag("");
    setEditing(true);
  };

  const addTag = () => {
    const t = draftTag.trim().replace(/\s+/g, " ").slice(0, 40);
    if (t && tags.length < MAX_TAGS && !tags.some((x) => x.toLowerCase() === t.toLowerCase())) setTags([...tags, t]);
    setDraftTag("");
  };

  const save = async () => {
    setSaving(true);
    try {
      onChange(await updatePropertyNotes(p.id, notes, draftTag.trim() ? [...tags, draftTag.trim()] : tags));
      setEditing(false);
      showToast("Notas internas guardadas");
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card>
      <CardHeader
        title="Notas internas"
        description={
          <span className="inline-flex items-center gap-1">
            <Lock className="h-3.5 w-3.5" aria-hidden /> Solo se ven en Genera; no se publican ni se mandan a 21Online.
          </span>
        }
        action={
          p.canEdit && !editing ? (
            <Button variant="ghost" size="sm" onClick={start}>
              <Pencil className="h-4 w-4" aria-hidden /> Editar
            </Button>
          ) : null
        }
      />
      {editing ? (
        <div className="space-y-3">
          <label htmlFor="private-notes" className="sr-only">
            Notas internas
          </label>
          <textarea
            id="private-notes"
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            rows={4}
            maxLength={4000}
            placeholder="Ej. El dueño acepta negociar hasta 5%. Llaves con el portero."
            className="w-full resize-y rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20"
          />
          <div>
            <label htmlFor="new-tag" className="text-sm font-medium text-obsessed">
              Etiquetas
            </label>
            <ul className="mt-2 flex flex-wrap gap-1.5">
              {tags.map((t) => (
                <li key={t} className="inline-flex items-center gap-1 rounded-full bg-gold/15 py-0.5 pl-2.5 pr-1 text-xs font-medium text-dark-gold">
                  {t}
                  <button
                    type="button"
                    onClick={() => setTags(tags.filter((x) => x !== t))}
                    aria-label={`Quitar etiqueta ${t}`}
                    className="rounded-full p-0.5 hover:bg-gold/25 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gold/40"
                  >
                    <X className="h-3 w-3" aria-hidden />
                  </button>
                </li>
              ))}
            </ul>
            <div className="mt-2 flex gap-2">
              <input
                id="new-tag"
                value={draftTag}
                onChange={(e) => setDraftTag(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === ",") {
                    e.preventDefault();
                    addTag();
                  }
                }}
                maxLength={40}
                disabled={tags.length >= MAX_TAGS}
                placeholder={tags.length >= MAX_TAGS ? `Máximo ${MAX_TAGS} etiquetas` : "Ej. urge vender (Enter para agregar)"}
                className="h-10 min-w-0 flex-1 rounded-xl border border-border bg-card px-3.5 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20"
              />
              <Button variant="secondary" onClick={addTag} disabled={!draftTag.trim() || tags.length >= MAX_TAGS}>
                Agregar
              </Button>
            </div>
          </div>
          <div className="flex gap-2">
            <Button onClick={save} disabled={saving}>
              {saving ? "Guardando…" : "Guardar"}
            </Button>
            <Button variant="ghost" onClick={() => setEditing(false)} disabled={saving}>
              Cancelar
            </Button>
          </div>
        </div>
      ) : p.privateNotes || tagList.length > 0 ? (
        <div className="space-y-3">
          <TagChips tags={tagList} />
          {p.privateNotes ? <p className="whitespace-pre-line text-sm text-obsessed">{p.privateNotes}</p> : null}
        </div>
      ) : (
        <p className="text-sm text-muted">{p.canEdit ? "Sin notas. Anota aquí lo que el equipo debe saber y no va en el anuncio." : "Sin notas."}</p>
      )}
    </Card>
  );
}
