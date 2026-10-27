"use client";

import { LoadError, Segmented, errorText, useApi } from "@/components/crm/CrmUi";
import { CompleteTaskModal, TaskModal } from "@/components/crm/LeadForms";
import { PageContainer } from "@/components/layout/PageContainer";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { Select } from "@/components/ui/Select";
import { StateMessage } from "@/components/ui/StateMessage";
import { useToast } from "@/components/ui/Toast";
import { cancelLeadTask, getAgenda, listAdvisors, listLeads, type LeadTask, type TaskType } from "@/lib/api/crm";
import {
  addDays,
  addMonths,
  advisorColor,
  formatDayLabel,
  formatInstantMx,
  isoDay,
  isoToMxLocal,
  monthTitle,
  mxLocalToIso,
  stageLabels,
  taskResultLabels,
  taskTypeEmoji,
  taskTypeLabels,
  weekStart,
} from "@/lib/crm/labels";
import { useCan } from "@/lib/permissions";
import { cn } from "@/lib/utils";
import { AlertTriangle, ChevronLeft, ChevronRight, Plus, RefreshCw } from "lucide-react";
import Link from "next/link";
import { useMemo, useState } from "react";

type View = "day" | "week" | "month" | "list";

const viewLabels: Record<View, string> = { day: "Día", week: "Semana", month: "Mes", list: "Agenda" };

/** Hoy en Ciudad de México ("2026-10-26"). */
function todayMx(): string {
  return isoToMxLocal(new Date().toISOString()).slice(0, 10);
}

/** Rango [inicio, fin) de fechas locales que muestra cada vista. */
function rangeOf(view: View, anchor: string): { start: string; end: string; title: string } {
  if (view === "day") return { start: anchor, end: addDays(anchor, 1), title: formatDayLabel(anchor) };
  if (view === "week") {
    const start = weekStart(anchor);
    return { start, end: addDays(start, 7), title: `${formatDayLabel(start)} – ${formatDayLabel(addDays(start, 6))}` };
  }
  if (view === "month") {
    const first = addMonths(anchor, 0);
    const start = weekStart(first);
    const last = addDays(addMonths(anchor, 1), -1);
    return { start, end: addDays(last, 8 - isoDay(last)), title: monthTitle(first) };
  }
  return { start: anchor, end: addDays(anchor, 30), title: `Próximos 30 días desde ${formatDayLabel(anchor)}` };
}

function dayOf(task: LeadTask): string {
  return task.scheduledAt ? isoToMxLocal(task.scheduledAt).slice(0, 10) : "";
}

function timeOf(task: LeadTask): string {
  return task.scheduledAt ? isoToMxLocal(task.scheduledAt).slice(11, 16) : "";
}

/**
 * Control de Cambios #30, parte 4: agenda de actividades de los leads, por
 * día, semana, mes o lista, con filtros por asesor, tipo, lead e inmueble.
 * Las actividades vencidas sin resultado siempre se muestran arriba.
 */
export default function AgendaPage() {
  const can = useCan();
  const seesAll = can("LEAD_VIEW_ALL");
  const canManage = can("LEAD_MANAGE");
  const { showToast } = useToast();
  const [view, setView] = useState<View>("week");
  const [anchor, setAnchor] = useState(todayMx);
  const [advisorId, setAdvisorId] = useState("");
  const [type, setType] = useState<TaskType | "">("");
  const [leadId, setLeadId] = useState("");
  const [property, setProperty] = useState("");
  const [selected, setSelected] = useState<LeadTask | null>(null);
  const [completing, setCompleting] = useState<LeadTask | null>(null);
  const [rescheduling, setRescheduling] = useState<LeadTask | null>(null);
  const [creatingAt, setCreatingAt] = useState<string | null>(null);

  const range = rangeOf(view, anchor);
  const agenda = useApi(
    () => getAgenda({ from: mxLocalToIso(`${range.start}T00:00`), to: mxLocalToIso(`${range.end}T00:00`), advisorId, type, leadId, property: property.trim() }),
    [range.start, range.end, advisorId, type, leadId, property],
  );
  const advisors = useApi(() => (seesAll ? listAdvisors() : Promise.resolve([])), [seesAll]);
  const leads = useApi(() => listLeads({ group: "OPEN", size: 200 }), []);
  const leadOptions = useMemo(() => (leads.data?.items ?? []).map((l) => ({ id: l.id, name: l.name })), [leads.data]);

  const tasks = agenda.data ?? [];
  const overdue = tasks.filter((t) => t.overdue);
  const inRange = tasks.filter((t) => {
    const d = dayOf(t);
    return d >= range.start && d < range.end;
  });
  const byDay = useMemo(() => {
    const map = new Map<string, LeadTask[]>();
    for (const t of inRange) {
      const d = dayOf(t);
      map.set(d, [...(map.get(d) ?? []), t]);
    }
    for (const list of map.values()) list.sort((a, b) => (a.scheduledAt ?? "").localeCompare(b.scheduledAt ?? ""));
    return map;
  }, [inRange]);

  const step = (dir: 1 | -1) => {
    if (view === "day") setAnchor((a) => addDays(a, dir));
    else if (view === "week") setAnchor((a) => addDays(a, 7 * dir));
    else if (view === "month") setAnchor((a) => addMonths(a, dir));
    else setAnchor((a) => addDays(a, 30 * dir));
  };

  const changed = (message: string) => () => {
    showToast(message);
    setSelected(null);
    agenda.reload();
  };

  return (
    <PageContainer
      title="Agenda"
      subtitle={seesAll ? "Citas y actividades de seguimiento de todos los asesores." : "Tus citas y actividades de seguimiento."}
      action={
        <>
          <Button variant="secondary" onClick={agenda.reload} aria-label="Actualizar" title="Actualizar">
            <RefreshCw className="h-4 w-4" aria-hidden />
          </Button>
          {canManage ? (
            <Button onClick={() => setCreatingAt(`${anchor}T09:00`)}>
              <Plus className="h-4 w-4" aria-hidden /> Nueva actividad
            </Button>
          ) : null}
        </>
      }
    >
      <div className="mb-4 flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
        <Segmented label="Vista" value={view} onChange={setView} options={(Object.keys(viewLabels) as View[]).map((id) => ({ id, label: viewLabels[id] }))} />
        <div className="flex items-center gap-2">
          <Button variant="secondary" size="sm" onClick={() => step(-1)} aria-label="Anterior">
            <ChevronLeft className="h-4 w-4" aria-hidden />
          </Button>
          <Button variant="secondary" size="sm" onClick={() => setAnchor(todayMx())}>
            Hoy
          </Button>
          <Button variant="secondary" size="sm" onClick={() => step(1)} aria-label="Siguiente">
            <ChevronRight className="h-4 w-4" aria-hidden />
          </Button>
          <h2 className="ml-2 text-base font-semibold capitalize text-obsessed">{range.title}</h2>
        </div>
      </div>

      <div className="mb-5 grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {seesAll ? (
          <Select
            aria-label="Filtrar por asesor"
            value={advisorId}
            onChange={(e) => setAdvisorId(e.target.value)}
            options={[{ value: "", label: "Todos los asesores" }, ...(advisors.data ?? []).map((a) => ({ value: a.userId, label: a.name }))]}
          />
        ) : null}
        <Select
          aria-label="Filtrar por tipo de actividad"
          value={type}
          onChange={(e) => setType(e.target.value as TaskType | "")}
          options={[{ value: "", label: "Todas las actividades" }, ...(Object.keys(taskTypeLabels) as TaskType[]).map((t) => ({ value: t, label: `${taskTypeEmoji[t]} ${taskTypeLabels[t]}` }))]}
        />
        <Select aria-label="Filtrar por lead" value={leadId} onChange={(e) => setLeadId(e.target.value)} options={[{ value: "", label: "Todos los leads" }, ...leadOptions.map((l) => ({ value: l.id, label: l.name }))]} />
        <Input aria-label="Filtrar por inmueble" placeholder="Inmueble (clave o nombre)" value={property} onChange={(e) => setProperty(e.target.value)} />
      </div>

      {overdue.length ? (
        <Card className="mb-5 border-danger-text/30 bg-danger-bg">
          <p className="mb-2 flex items-center gap-2 text-sm font-semibold text-danger-text">
            <AlertTriangle className="h-4 w-4" aria-hidden /> {overdue.length} actividad{overdue.length === 1 ? "" : "es"} vencida{overdue.length === 1 ? "" : "s"} sin resultado
          </p>
          <ul className="flex flex-wrap gap-2">
            {overdue.map((t) => (
              <li key={t.id}>
                <TaskChip task={t} showDay onClick={() => setSelected(t)} />
              </li>
            ))}
          </ul>
        </Card>
      ) : null}

      {agenda.error && !agenda.data ? (
        <LoadError message={agenda.error} onRetry={agenda.reload} />
      ) : !agenda.data ? (
        <StateMessage kind="loading" title="Cargando agenda…" />
      ) : view === "month" ? (
        <MonthGrid range={range} anchor={anchor} byDay={byDay} onSelect={setSelected} onCreate={canManage ? (d) => setCreatingAt(`${d}T09:00`) : undefined} />
      ) : view === "list" ? (
        <AgendaList byDay={byDay} onSelect={setSelected} />
      ) : (
        <DayColumns days={view === "day" ? [range.start] : Array.from({ length: 7 }, (_, i) => addDays(range.start, i))} byDay={byDay} onSelect={setSelected} onCreate={canManage ? (d) => setCreatingAt(`${d}T09:00`) : undefined} />
      )}

      <Modal
        open={selected !== null}
        onClose={() => setSelected(null)}
        title={selected ? `${taskTypeEmoji[selected.type]} ${selected.title ?? taskTypeLabels[selected.type]}` : ""}
        footer={
          selected ? (
            <>
              <Link href={`/leads/${selected.leadId}`} className="mr-auto inline-flex items-center text-sm font-medium text-dark-gold hover:text-obsessed">
                Ver ficha del lead
              </Link>
              {canManage && selected.status === "SCHEDULED" ? (
                <>
                  <Button
                    variant="ghost"
                    className="text-danger-text"
                    onClick={async () => {
                      try {
                        await cancelLeadTask(selected.id);
                        changed("Actividad cancelada")();
                      } catch (err) {
                        showToast(errorText(err));
                      }
                    }}
                  >
                    Cancelar
                  </Button>
                  <Button variant="secondary" onClick={() => setRescheduling(selected)}>
                    Reprogramar
                  </Button>
                  <Button onClick={() => setCompleting(selected)}>Registrar resultado</Button>
                </>
              ) : null}
            </>
          ) : null
        }
      >
        {selected ? (
          <dl className="grid grid-cols-1 gap-3 text-sm sm:grid-cols-2">
            <Detail label="Lead">
              {selected.leadName}
              {selected.leadStage ? <span className="text-muted"> · {stageLabels[selected.leadStage]}</span> : null}
            </Detail>
            <Detail label="Cuándo">
              {selected.scheduledAt ? formatInstantMx(selected.scheduledAt) : "—"}
              {selected.durationMinutes ? ` · ${selected.durationMinutes} min` : ""}
            </Detail>
            <Detail label="Asesor">{selected.advisor?.name ?? "—"}</Detail>
            <Detail label="Estado">
              {selected.status === "DONE" ? (
                <Badge tone="success">{selected.result ? taskResultLabels[selected.result] : "Realizada"}</Badge>
              ) : selected.overdue ? (
                <Badge tone="danger">Vencida sin resultado</Badge>
              ) : (
                <Badge tone="info">Programada</Badge>
              )}
            </Detail>
            {selected.propertyTitle || selected.propertyKey ? <Detail label="Inmueble">{selected.propertyTitle ?? selected.propertyKey}</Detail> : null}
            {selected.notesBefore ? <Detail label="Notas previas">{selected.notesBefore}</Detail> : null}
            {selected.outcomeNotes ? <Detail label="Resumen">{selected.outcomeNotes}</Detail> : null}
          </dl>
        ) : null}
      </Modal>

      <CompleteTaskModal task={completing} onClose={() => setCompleting(null)} onSaved={changed("Resultado registrado")} />
      <TaskModal
        open={rescheduling !== null || creatingAt !== null}
        onClose={() => {
          setRescheduling(null);
          setCreatingAt(null);
        }}
        task={rescheduling}
        leads={leadOptions}
        advisors={advisors.data ?? []}
        defaultStart={creatingAt ?? undefined}
        onSaved={changed(rescheduling ? "Actividad reprogramada" : "Actividad programada")}
      />
    </PageContainer>
  );
}

function Detail({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="mt-0.5 whitespace-pre-wrap text-obsessed">{children}</dd>
    </div>
  );
}

function TaskChip({ task, showDay, onClick, compact }: { task: LeadTask; showDay?: boolean; onClick: () => void; compact?: boolean }) {
  const color = task.advisor ? advisorColor(task.advisor.id) : null;
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "w-full min-w-0 rounded-lg border-l-4 px-2 py-1.5 text-left text-xs transition-shadow hover:shadow-md focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30",
        task.overdue ? "border-danger-text bg-danger-bg text-danger-text" : task.status === "DONE" ? "opacity-70" : "",
      )}
      style={!task.overdue && color ? { background: color.bg, borderColor: color.border, color: color.text } : undefined}
      title={`${taskTypeLabels[task.type]} · ${task.leadName ?? ""}${task.advisor ? ` · ${task.advisor.name}` : ""}`}
    >
      <span className="block truncate font-medium">
        {taskTypeEmoji[task.type]} {showDay ? `${formatDayLabel(dayOf(task))} ` : ""}
        {timeOf(task)} {task.status === "DONE" ? "✓ " : ""}
        {task.leadName}
      </span>
      {!compact ? <span className="block truncate opacity-80">{task.title ?? taskTypeLabels[task.type]}</span> : null}
    </button>
  );
}

function DayColumns({ days, byDay, onSelect, onCreate }: { days: string[]; byDay: Map<string, LeadTask[]>; onSelect: (t: LeadTask) => void; onCreate?: (day: string) => void }) {
  const today = todayMx();
  return (
    <div className="-mx-4 overflow-x-auto px-4 pb-2 sm:mx-0 sm:px-0">
      <div className={cn("grid gap-3", days.length === 1 ? "grid-cols-1" : "min-w-[980px] grid-cols-7")}>
        {days.map((d) => (
          <section key={d} aria-label={formatDayLabel(d)} className={cn("flex min-h-48 flex-col rounded-xl border bg-card", d === today ? "border-gold" : "border-border")}>
            <header className="flex items-center justify-between border-b border-border px-3 py-2">
              <span className={cn("text-sm font-semibold capitalize", d === today ? "text-dark-gold" : "text-obsessed")}>{formatDayLabel(d)}</span>
              {onCreate ? (
                <button type="button" onClick={() => onCreate(d)} aria-label={`Nueva actividad el ${formatDayLabel(d)}`} className="rounded-md p-1 text-muted hover:bg-app-bg hover:text-obsessed">
                  <Plus className="h-4 w-4" aria-hidden />
                </button>
              ) : null}
            </header>
            <ul className="flex flex-1 flex-col gap-1.5 p-2">
              {(byDay.get(d) ?? []).map((t) => (
                <li key={t.id}>
                  <TaskChip task={t} onClick={() => onSelect(t)} />
                </li>
              ))}
              {(byDay.get(d) ?? []).length === 0 ? <li className="py-4 text-center text-xs text-muted">Sin actividades</li> : null}
            </ul>
          </section>
        ))}
      </div>
    </div>
  );
}

function MonthGrid({
  range,
  anchor,
  byDay,
  onSelect,
  onCreate,
}: {
  range: { start: string; end: string };
  anchor: string;
  byDay: Map<string, LeadTask[]>;
  onSelect: (t: LeadTask) => void;
  onCreate?: (day: string) => void;
}) {
  const days: string[] = [];
  for (let d = range.start; d < range.end; d = addDays(d, 1)) days.push(d);
  const month = anchor.slice(0, 7);
  const today = todayMx();
  return (
    <div className="-mx-4 overflow-x-auto px-4 pb-2 sm:mx-0 sm:px-0">
      <div className="grid min-w-[840px] grid-cols-7 gap-px overflow-hidden rounded-xl border border-border bg-border">
        {["Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"].map((n) => (
          <div key={n} className="bg-app-bg px-2 py-1.5 text-xs font-medium uppercase tracking-wide text-muted">
            {n}
          </div>
        ))}
        {days.map((d) => {
          const items = byDay.get(d) ?? [];
          return (
            <div key={d} className={cn("min-h-28 bg-card p-1.5", d.slice(0, 7) !== month && "bg-app-bg/60")}>
              <div className="mb-1 flex items-center justify-between">
                <span className={cn("text-xs tabular-nums", d === today ? "rounded-full bg-gold px-1.5 font-semibold text-brand-ink" : d.slice(0, 7) === month ? "text-obsessed" : "text-muted")}>
                  {Number(d.slice(8, 10))}
                </span>
                {onCreate ? (
                  <button type="button" onClick={() => onCreate(d)} aria-label={`Nueva actividad el ${formatDayLabel(d)}`} className="rounded p-0.5 text-muted hover:text-obsessed">
                    <Plus className="h-3.5 w-3.5" aria-hidden />
                  </button>
                ) : null}
              </div>
              <ul className="space-y-1">
                {items.slice(0, 3).map((t) => (
                  <li key={t.id}>
                    <TaskChip task={t} compact onClick={() => onSelect(t)} />
                  </li>
                ))}
                {items.length > 3 ? <li className="px-1 text-[0.7rem] text-muted">+{items.length - 3} más</li> : null}
              </ul>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function AgendaList({ byDay, onSelect }: { byDay: Map<string, LeadTask[]>; onSelect: (t: LeadTask) => void }) {
  const days = [...byDay.keys()].sort();
  if (days.length === 0) return <StateMessage kind="empty" title="Sin actividades" description="No hay actividades programadas en este periodo." />;
  return (
    <div className="space-y-4">
      {days.map((d) => (
        <section key={d}>
          <h3 className="mb-2 text-sm font-semibold capitalize text-obsessed">{formatDayLabel(d)}</h3>
          <ul className="space-y-1.5">
            {(byDay.get(d) ?? []).map((t) => (
              <li key={t.id}>
                <button
                  type="button"
                  onClick={() => onSelect(t)}
                  className={cn(
                    "flex w-full flex-wrap items-center gap-x-3 gap-y-1 rounded-xl border px-4 py-2.5 text-left text-sm hover:bg-app-bg focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30",
                    t.overdue ? "border-danger-text/40 bg-danger-bg" : "border-border bg-card",
                  )}
                >
                  <span className="w-12 tabular-nums text-muted">{timeOf(t)}</span>
                  <span aria-hidden>{taskTypeEmoji[t.type]}</span>
                  <span className="font-medium text-obsessed">{t.leadName}</span>
                  <span className="text-muted">{t.title ?? taskTypeLabels[t.type]}</span>
                  {t.propertyTitle || t.propertyKey ? <span className="text-xs text-muted">· {t.propertyTitle ?? t.propertyKey}</span> : null}
                  <span className="ml-auto text-xs text-muted">{t.advisor?.name}</span>
                  {t.status === "DONE" ? <Badge tone="success">{t.result ? taskResultLabels[t.result] : "Realizada"}</Badge> : t.overdue ? <Badge tone="danger">Vencida</Badge> : null}
                </button>
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}
