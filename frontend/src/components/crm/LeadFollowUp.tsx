"use client";

import { errorText } from "@/components/crm/CrmUi";
import { CompleteTaskModal, TaskModal } from "@/components/crm/LeadForms";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { useToast } from "@/components/ui/Toast";
import {
  cancelLeadTask,
  changeLeadStage,
  linkLeadExpediente,
  shareLeadProperty,
  type AdvisorRouting,
  type LeadDetail,
  type LeadItem,
  type LeadTask,
} from "@/lib/api/crm";
import { formatDateTime, formatInstantMx, stageHints, stageLabels, taskResultLabels, taskTypeEmoji, taskTypeLabels } from "@/lib/crm/labels";
import { cn } from "@/lib/utils";
import { AlertTriangle, CalendarPlus, CheckCircle2, FolderOpen, Home, Lightbulb, Link2, Unlink } from "lucide-react";
import Link from "next/link";
import { useState } from "react";

/** Control de Cambios #30: la etapa que sugieren las actividades (el asesor decide). */
export function SuggestedStageBanner({ detail, canManage, onChange }: { detail: LeadDetail; canManage: boolean; onChange: (d: LeadDetail) => void }) {
  const { showToast } = useToast();
  const [saving, setSaving] = useState(false);
  const stage = detail.suggestedStage;
  if (!stage || !canManage) return null;
  return (
    <p className="mb-5 flex flex-wrap items-center gap-2.5 rounded-xl border border-gold/40 bg-gold/10 px-4 py-3 text-sm text-obsessed">
      <Lightbulb className="h-4 w-4 shrink-0 text-dark-gold" aria-hidden />
      <span className="min-w-0 flex-1">
        Por las actividades registradas, este lead parece estar en <strong className="font-semibold">{stageLabels[stage]}</strong> ({stageHints[stage].toLowerCase()}).
      </span>
      <Button
        size="sm"
        disabled={saving}
        onClick={async () => {
          setSaving(true);
          try {
            onChange(await changeLeadStage(detail.lead.id, stage));
            showToast(`Etapa: ${stageLabels[stage]}`);
          } catch (err) {
            showToast(errorText(err));
          } finally {
            setSaving(false);
          }
        }}
      >
        Mover a {stageLabels[stage]}
      </Button>
    </p>
  );
}

/** Actividades programadas (con las vencidas destacadas) y realizadas. */
export function TasksCard({ detail, advisors, canManage, onChanged }: { detail: LeadDetail; advisors: AdvisorRouting[]; canManage: boolean; onChanged: () => void }) {
  const { showToast } = useToast();
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<LeadTask | null>(null);
  const [completing, setCompleting] = useState<LeadTask | null>(null);
  const [showDone, setShowDone] = useState(false);
  const pending = detail.tasks.filter((t) => t.status === "SCHEDULED").sort((a, b) => (a.scheduledAt ?? "").localeCompare(b.scheduledAt ?? ""));
  const done = detail.tasks.filter((t) => t.status !== "SCHEDULED");
  const lead = detail.lead;

  const saved = (message: string) => () => {
    showToast(message);
    onChanged();
  };

  return (
    <Card>
      <CardHeader
        title="Actividades"
        description="Llamadas, visitas, propuestas… Las programadas aparecen en la agenda."
        action={
          canManage ? (
            <Button size="sm" onClick={() => setCreating(true)}>
              <CalendarPlus className="h-4 w-4" aria-hidden /> Nueva actividad
            </Button>
          ) : null
        }
      />
      {pending.length === 0 ? (
        <p className="text-sm text-muted">{lead.stage === "WON" || lead.stage === "LOST" ? "Sin actividades pendientes." : "No hay actividades programadas. Programa la siguiente para no perder el seguimiento."}</p>
      ) : (
        <ul className="space-y-2">
          {pending.map((t) => (
            <li key={t.id} className={cn("rounded-xl border p-3 text-sm", t.overdue ? "border-danger-text/40 bg-danger-bg" : "border-border")}>
              <div className="flex flex-wrap items-center gap-2">
                <span aria-hidden>{taskTypeEmoji[t.type]}</span>
                <span className="font-medium text-obsessed">{t.title ?? taskTypeLabels[t.type]}</span>
                {t.overdue ? (
                  <Badge tone="danger">
                    <AlertTriangle aria-hidden /> Vencida sin resultado
                  </Badge>
                ) : null}
                <span className={cn("ml-auto text-xs", t.overdue ? "font-medium text-danger-text" : "text-muted")}>{t.scheduledAt ? formatInstantMx(t.scheduledAt) : "—"}</span>
              </div>
              <p className="mt-1 text-xs text-muted">
                {taskTypeLabels[t.type]}
                {t.durationMinutes ? ` · ${t.durationMinutes} min` : ""}
                {t.advisor ? ` · ${t.advisor.name}` : ""}
                {t.propertyTitle || t.propertyKey ? ` · ${t.propertyTitle ?? t.propertyKey}` : ""}
                {t.reminderMinutes ? ` · recordatorio ${t.reminderMinutes >= 1440 ? "1 día" : t.reminderMinutes >= 60 ? "1 h" : `${t.reminderMinutes} min`} antes` : ""}
              </p>
              {t.notesBefore ? <p className="mt-1 whitespace-pre-wrap text-obsessed">{t.notesBefore}</p> : null}
              {canManage ? (
                <div className="mt-2 flex flex-wrap gap-2">
                  <Button size="sm" variant="secondary" onClick={() => setCompleting(t)}>
                    <CheckCircle2 className="h-4 w-4" aria-hidden /> Registrar resultado
                  </Button>
                  <Button size="sm" variant="ghost" onClick={() => setEditing(t)}>
                    Reprogramar
                  </Button>
                  <Button
                    size="sm"
                    variant="ghost"
                    className="text-danger-text"
                    onClick={async () => {
                      try {
                        await cancelLeadTask(t.id);
                        saved("Actividad cancelada")();
                      } catch (err) {
                        showToast(errorText(err));
                      }
                    }}
                  >
                    Cancelar
                  </Button>
                </div>
              ) : null}
            </li>
          ))}
        </ul>
      )}
      {done.length ? (
        <div className="mt-4 border-t border-border pt-3">
          <button type="button" onClick={() => setShowDone((v) => !v)} className="text-sm font-medium text-dark-gold hover:text-obsessed">
            {showDone ? "Ocultar" : "Ver"} realizadas y canceladas ({done.length})
          </button>
          {showDone ? (
            <ul className="mt-3 space-y-2">
              {done.map((t) => (
                <li key={t.id} className="rounded-xl bg-app-bg p-3 text-sm">
                  <div className="flex flex-wrap items-center gap-2">
                    <span aria-hidden>{taskTypeEmoji[t.type]}</span>
                    <span className="font-medium text-obsessed">{t.title ?? taskTypeLabels[t.type]}</span>
                    {t.status === "CANCELLED" ? <Badge tone="neutral">Cancelada</Badge> : t.result ? <Badge tone={t.result === "NO_ANSWER" || t.result === "REJECTED" ? "warning" : "success"}>{taskResultLabels[t.result]}</Badge> : null}
                    <span className="ml-auto text-xs text-muted">{t.scheduledAt ? formatInstantMx(t.scheduledAt) : ""}</span>
                  </div>
                  {t.outcomeNotes ? <p className="mt-1 whitespace-pre-wrap text-obsessed">{t.outcomeNotes}</p> : null}
                  <p className="mt-1 text-xs text-muted">
                    {t.actualMinutes ? `${t.actualMinutes} min · ` : ""}
                    {t.advisor ? t.advisor.name : ""}
                    {t.nextAction ? ` · Siguiente: ${t.nextAction}` : ""}
                  </p>
                </li>
              ))}
            </ul>
          ) : null}
        </div>
      ) : null}
      <TaskModal
        open={creating || editing !== null}
        onClose={() => {
          setCreating(false);
          setEditing(null);
        }}
        leadId={lead.id}
        task={editing}
        advisors={advisors}
        defaultProperty={lead.property ? { key: lead.property.key, title: lead.property.title } : null}
        onSaved={saved(editing ? "Actividad reprogramada" : "Actividad guardada")}
      />
      <CompleteTaskModal task={completing} onClose={() => setCompleting(null)} onSaved={saved("Resultado registrado")} />
    </Card>
  );
}

/** Inmuebles del portafolio que se le mostraron o propusieron. */
export function SharedPropertiesCard({ detail, canManage, onChange }: { detail: LeadDetail; canManage: boolean; onChange: (d: LeadDetail) => void }) {
  const { showToast } = useToast();
  const [reference, setReference] = useState("");
  const [note, setNote] = useState("");
  const [saving, setSaving] = useState(false);
  return (
    <Card>
      <CardHeader title="Inmuebles compartidos" description="Propiedades que se le han mostrado o propuesto." />
      {detail.sharedProperties.length === 0 ? (
        <p className="text-sm text-muted">Todavía no se le ha compartido ninguna propiedad.</p>
      ) : (
        <ul className="space-y-2">
          {detail.sharedProperties.map((p) => (
            <li key={p.id} className="flex items-start gap-2.5 text-sm">
              <Home className="mt-0.5 h-4 w-4 shrink-0 text-muted" aria-hidden />
              <div className="min-w-0">
                <p className="text-obsessed">
                  {p.online21Id ? (
                    <Link href={`/propiedades/21online/${p.online21Id}`} className="font-medium hover:text-dark-gold">
                      {p.title ?? p.key}
                    </Link>
                  ) : (
                    <span className="font-medium">{p.title ?? p.key}</span>
                  )}
                </p>
                <p className="text-xs text-muted">
                  {formatDateTime(p.createdAt)}
                  {p.actorName ? ` · ${p.actorName}` : ""}
                  {p.note ? ` · ${p.note}` : ""}
                </p>
              </div>
            </li>
          ))}
        </ul>
      )}
      {canManage ? (
        <form
          className="mt-4 grid grid-cols-1 gap-2 border-t border-border pt-3 sm:grid-cols-[1fr_1fr_auto] sm:items-end"
          onSubmit={async (e) => {
            e.preventDefault();
            if (!reference.trim()) return;
            setSaving(true);
            try {
              onChange(await shareLeadProperty(detail.lead.id, reference.trim(), note.trim() || undefined));
              setReference("");
              setNote("");
              showToast("Propiedad registrada");
            } catch (err) {
              showToast(errorText(err));
            } finally {
              setSaving(false);
            }
          }}
        >
          <Input label="Propiedad" placeholder="Número o clave (GN 14)" value={reference} onChange={(e) => setReference(e.target.value)} />
          <Input label="Nota (opcional)" placeholder="Ej. le gustó, pidió otra visita" value={note} onChange={(e) => setNote(e.target.value)} />
          <Button type="submit" variant="secondary" disabled={saving || !reference.trim()}>
            Agregar
          </Button>
        </form>
      ) : null}
    </Card>
  );
}

/** CC#30 parte 7: expediente del inmueble cuando el lead llega a Oferta aceptada. */
export function ExpedienteLinkCard({ detail, canManage, onChange }: { detail: LeadDetail; canManage: boolean; onChange: (d: LeadDetail) => void }) {
  const { showToast } = useToast();
  const [folio, setFolio] = useState("");
  const [saving, setSaving] = useState(false);
  const lead = detail.lead;
  const relevant = lead.expediente || lead.stage === "OFFER_ACCEPTED" || lead.stage === "WON" || lead.stage === "NEGOTIATION";
  if (!relevant) return null;

  const link = async (value: string) => {
    setSaving(true);
    try {
      onChange(await linkLeadExpediente(lead.id, value));
      setFolio("");
      showToast(value ? "Expediente vinculado" : "Expediente desvinculado");
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card className={lead.expediente ? undefined : "border-gold/50 bg-gold/5"}>
      <CardHeader title="Expediente del inmueble" description={lead.expediente ? "Las actividades del lead quedan referenciadas en el expediente." : "Al aceptar la oferta, vincúlalo al expediente de la operación."} />
      {lead.expediente ? (
        <div className="flex flex-wrap items-center gap-2">
          <Link href={`/expedientes/${lead.expediente.id}`} className="inline-flex items-center gap-1.5 text-sm font-medium text-dark-gold hover:text-obsessed">
            <FolderOpen className="h-4 w-4" aria-hidden /> {lead.expediente.folio}
          </Link>
          {canManage ? (
            <Button size="sm" variant="ghost" disabled={saving} onClick={() => void link("")}>
              <Unlink className="h-4 w-4" aria-hidden /> Desvincular
            </Button>
          ) : null}
        </div>
      ) : (
        <>
          {detail.expedienteCandidates.length ? (
            <ul className="mb-3 space-y-1.5">
              {detail.expedienteCandidates.map((c) => (
                <li key={c.id} className="flex items-center gap-2 text-sm">
                  <span className="min-w-0 flex-1 truncate text-obsessed">
                    {c.folio} · {c.ownerDisplayName}
                    {c.propertyKey ? ` · ${c.propertyKey}` : ""}
                  </span>
                  {canManage ? (
                    <Button size="sm" variant="secondary" disabled={saving} onClick={() => void link(c.folio)}>
                      <Link2 className="h-4 w-4" aria-hidden /> Vincular
                    </Button>
                  ) : null}
                </li>
              ))}
            </ul>
          ) : null}
          {canManage ? (
            <div className="flex items-end gap-2">
              <Input label="Folio del expediente" value={folio} onChange={(e) => setFolio(e.target.value)} placeholder="Ej. EXP-2026-0012" containerClassName="min-w-0 flex-1" />
              <Button variant="secondary" disabled={saving || !folio.trim()} onClick={() => void link(folio.trim())}>
                Vincular
              </Button>
            </div>
          ) : null}
          <Link href="/expedientes/nuevo" className="mt-3 inline-flex text-sm font-medium text-dark-gold hover:text-obsessed">
            ¿Aún no existe? Crear expediente
          </Link>
        </>
      )}
    </Card>
  );
}

/** Fechas de entrada y salida de cada etapa (CC#30). */
export function StageHistoryCard({ detail }: { detail: LeadDetail }) {
  if (detail.stageHistory.length === 0) return null;
  return (
    <Card>
      <CardHeader title="Tiempo en cada etapa" />
      <ol className="space-y-2 text-sm">
        {detail.stageHistory.map((s, i) => (
          <li key={`${s.stage}-${i}`} className="flex flex-wrap items-baseline gap-x-2">
            <span className={cn("font-medium", s.exitedAt ? "text-muted" : "text-obsessed")}>{stageLabels[s.stage]}</span>
            <span className="text-xs text-muted">
              {formatDateTime(s.enteredAt)} → {s.exitedAt ? formatDateTime(s.exitedAt) : "actual"} · {s.days} día{s.days === 1 ? "" : "s"}
            </span>
          </li>
        ))}
      </ol>
    </Card>
  );
}

export function leadHasFollowUp(lead: LeadItem): boolean {
  return lead.stage !== "WON" && lead.stage !== "LOST";
}
