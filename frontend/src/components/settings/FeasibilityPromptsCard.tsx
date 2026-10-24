"use client";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { useToast } from "@/components/ui/Toast";
import {
  getFeasibilityPrompts,
  getFeasibilityPromptVersion,
  getFeasibilityPromptVersions,
  restoreFeasibilityPrompt,
  updateFeasibilityPrompt,
  type FeasibilityPrompt,
  type FeasibilityPromptKey,
  type FeasibilityPromptVersion,
} from "@/lib/api/feasibility";
import { errorText } from "@/lib/errors";
import { formatDateTime } from "@/lib/labels";
import { useCan } from "@/lib/permissions";
import { cn } from "@/lib/utils";
import { History, Loader2, RotateCcw, Save } from "lucide-react";
import { useEffect, useState } from "react";

/**
 * Los dos prompts maestros de la factibilidad con IA ("Analista de Factibilidad 3.0" y
 * "Presentación de factibilidad"). El administrador los mejora sin liberar código; cada guardado
 * es una versión nueva y se puede volver a cualquiera, incluido el texto original del cliente.
 */
export function FeasibilityPromptsCard() {
  const can = useCan();
  const { showToast } = useToast();
  const allowed = can("SETTINGS_MANAGE");
  const [prompts, setPrompts] = useState<FeasibilityPrompt[] | null>(null);
  const [active, setActive] = useState<FeasibilityPromptKey>("ANALYST");
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);
  const [versions, setVersions] = useState<FeasibilityPromptVersion[] | null>(null);
  const [preview, setPreview] = useState<{ version: number; content: string } | null>(null);

  useEffect(() => {
    if (!allowed) return;
    getFeasibilityPrompts()
      .then((list) => {
        setPrompts(list);
        setDrafts(Object.fromEntries(list.map((p) => [p.key, p.content])));
      })
      .catch((err) => showToast(errorText(err)));
  }, [allowed, showToast]);

  if (!allowed) return null;

  const current = prompts?.find((p) => p.key === active) ?? null;
  const draft = drafts[active] ?? "";
  const dirty = current !== null && draft.trim() !== current.content.trim();

  const replace = (saved: FeasibilityPrompt) => {
    setPrompts((list) => (list ?? []).map((p) => (p.key === saved.key ? saved : p)));
    setDrafts((d) => ({ ...d, [saved.key]: saved.content }));
    setVersions(null);
    setPreview(null);
  };

  const save = async () => {
    setSaving(true);
    try {
      replace(await updateFeasibilityPrompt(active, draft.trim(), note.trim() || undefined));
      setNote("");
      showToast("Prompt guardado. Las próximas factibilidades lo usarán.");
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  const loadVersions = async () => {
    try {
      setVersions(await getFeasibilityPromptVersions(active));
    } catch (err) {
      showToast(errorText(err));
    }
  };

  const restore = async (version: number) => {
    setSaving(true);
    try {
      replace(await restoreFeasibilityPrompt(active, version));
      showToast(version === 0 ? "Se restauró el prompt original del cliente." : `Se restauró la versión ${version}.`);
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card>
      <CardHeader
        title="Prompts de la factibilidad con IA"
        description="Instrucciones que sigue la IA al generar la factibilidad de un expediente y al responder el chat de consultas. Puedes mejorarlas cuando quieras: cada guardado crea una versión nueva y siempre puedes volver a una anterior o al texto original."
      />
      {prompts === null ? (
        <p className="text-sm text-muted">Cargando…</p>
      ) : (
        <div className="flex flex-col gap-3">
          <div className="flex flex-wrap gap-2" role="tablist">
            {prompts.map((p) => (
              <button
                key={p.key}
                type="button"
                role="tab"
                aria-selected={p.key === active}
                onClick={() => {
                  setActive(p.key);
                  setVersions(null);
                  setPreview(null);
                }}
                className={cn(
                  "rounded-full border px-3 py-1.5 text-sm",
                  p.key === active ? "border-gold bg-gold/15 font-medium text-obsessed" : "border-border text-muted hover:bg-app-bg",
                )}
              >
                {p.title}
              </button>
            ))}
          </div>
          {current ? (
            <>
              <div className="flex flex-wrap items-center gap-2 text-xs text-muted">
                <Badge tone={current.original ? "neutral" : "gold"}>{current.original ? "Texto original del cliente" : `Versión ${current.version}`}</Badge>
                {current.updatedByName ? (
                  <span>
                    Guardado por {current.updatedByName}, {formatDateTime(current.updatedAt)}
                  </span>
                ) : null}
                <span>{draft.length.toLocaleString("es-MX")} caracteres</span>
              </div>
              <textarea
                value={draft}
                onChange={(e) => setDrafts((d) => ({ ...d, [active]: e.target.value }))}
                rows={22}
                maxLength={100000}
                spellCheck={false}
                aria-label={current.title}
                className="w-full rounded-xl border border-border bg-card px-3.5 py-2.5 font-mono text-xs leading-relaxed text-obsessed outline-none focus:border-gold"
              />
              <input
                value={note}
                onChange={(e) => setNote(e.target.value)}
                maxLength={200}
                placeholder="Qué cambiaste (opcional), p. ej. «Más peso a comparables con alberca»"
                className="w-full rounded-xl border border-border bg-card px-3.5 py-2 text-sm text-obsessed outline-none focus:border-gold"
              />
              <div className="flex flex-wrap items-center gap-2">
                <Button size="sm" onClick={save} disabled={saving || !dirty || draft.trim().length < 200}>
                  {saving ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden /> : <Save className="h-4 w-4" aria-hidden />} Guardar versión nueva
                </Button>
                {dirty ? (
                  <Button size="sm" variant="ghost" onClick={() => setDrafts((d) => ({ ...d, [active]: current.content }))}>
                    Descartar cambios
                  </Button>
                ) : null}
                <Button size="sm" variant="secondary" onClick={loadVersions}>
                  <History className="h-4 w-4" aria-hidden /> Historial
                </Button>
                {!current.original ? (
                  <Button size="sm" variant="secondary" onClick={() => restore(0)} disabled={saving}>
                    <RotateCcw className="h-4 w-4" aria-hidden /> Restaurar original
                  </Button>
                ) : null}
              </div>
              {versions ? (
                <div className="rounded-lg bg-app-bg px-3 py-2 text-sm">
                  <p className="mb-2 font-medium text-obsessed">Versiones</p>
                  <ul className="flex flex-col gap-1.5">
                    {versions.map((v) => (
                      <li key={v.version} className="flex flex-wrap items-center gap-2">
                        <span className="w-24 font-medium text-obsessed">{v.version === 0 ? "Original" : `Versión ${v.version}`}</span>
                        {v.current ? <Badge tone="success">Vigente</Badge> : null}
                        <span className="text-xs text-muted">
                          {v.createdAt ? `${formatDateTime(v.createdAt)} · ${v.createdByName ?? ""}` : "Texto que entregó el cliente"}
                          {v.note ? ` · ${v.note}` : ""}
                        </span>
                        <button
                          type="button"
                          className="text-xs text-info-text underline"
                          onClick={() =>
                            getFeasibilityPromptVersion(active, v.version)
                              .then(setPreview)
                              .catch((err) => showToast(errorText(err)))
                          }
                        >
                          Ver
                        </button>
                        {!v.current ? (
                          <button type="button" className="text-xs text-info-text underline" disabled={saving} onClick={() => restore(v.version)}>
                            Usar esta versión
                          </button>
                        ) : null}
                      </li>
                    ))}
                  </ul>
                  {preview ? (
                    <pre className="mt-3 max-h-72 overflow-auto whitespace-pre-wrap rounded-lg border border-border bg-card p-3 text-xs text-obsessed">
                      {preview.content}
                    </pre>
                  ) : null}
                </div>
              ) : null}
              <details className="rounded-lg border border-border px-3 py-2 text-sm">
                <summary className="cursor-pointer text-muted">Reglas fijas que el sistema agrega al final (no se editan)</summary>
                <p className="mt-2 text-xs text-muted">
                  Le indican a la IA el formato exacto que el sistema necesita para armar el Word y el PowerPoint, que no haga preguntas y que no
                  incluya datos personales. También se agrega si el análisis es de venta o de renta.
                </p>
                <pre className="mt-2 max-h-60 overflow-auto whitespace-pre-wrap text-xs text-obsessed">{current.systemRules}</pre>
              </details>
            </>
          ) : null}
        </div>
      )}
    </Card>
  );
}
