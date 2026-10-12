"use client";

import { AbsenceModal, CopyModal, ShiftModal, type ShiftDraft } from "@/components/crm/CalendarModals";
import { LoadError, Segmented, errorText } from "@/components/crm/CrmUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Select } from "@/components/ui/Select";
import { StateMessage } from "@/components/ui/StateMessage";
import { useToast } from "@/components/ui/Toast";
import { useAuth } from "@/context/AuthProvider";
import {
  getCalendar,
  getCalendarAudit,
  type CalendarAbsence,
  type CalendarAuditEntry,
  type CalendarView,
  type LocalRange,
  type ShiftKind,
  type ShiftOccurrence,
} from "@/lib/api/crm";
import {
  BUSINESS_TIMEZONE_LABEL,
  absenceKindLabels,
  addDays,
  advisorColor,
  advisorStatusLabels,
  advisorStatusTones,
  formatDayLabel,
  formatLocal,
  hhmm,
  minutesOf,
  shiftKindLabels,
  weekStart,
} from "@/lib/crm/labels";
import { useCan } from "@/lib/permissions";
import { cn } from "@/lib/utils";
import { AlertTriangle, CalendarPlus, ChevronLeft, ChevronRight, Clock, Copy, History, Plane, Shield, UserRound } from "lucide-react";
import { useSearchParams } from "next/navigation";
import { Suspense, useCallback, useEffect, useMemo, useState } from "react";

type View = "people" | "week" | "day" | "agenda";

export default function CalendarioPage() {
  return (
    <Suspense fallback={null}>
      <CalendarScreen />
    </Suspense>
  );
}

function CalendarScreen() {
  const can = useCan();
  const { user } = useAuth();
  const params = useSearchParams();
  const { showToast } = useToast();
  const canView = can("CALENDAR_VIEW_ALL") || can("CALENDAR_VIEW_OWN");

  // En celular la vista cómoda es la agenda.
  // Vista principal: tabla sencilla de horarios por asesor (fácil de leer también en celular).
  const [view, setView] = useState<View>("people");
  const [anchor, setAnchor] = useState<string | null>(null); // día seleccionado (Ciudad de México)
  const [advisorFilter, setAdvisorFilter] = useState(params.get("asesor") ?? "");
  const [kindFilter, setKindFilter] = useState<"" | ShiftKind>("");
  const [data, setData] = useState<CalendarView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loadedKey, setLoadedKey] = useState<string | null>(null);
  const [token, setToken] = useState(0);
  const [shiftModal, setShiftModal] = useState<{ occurrence: ShiftOccurrence | null; draft: ShiftDraft | null } | null>(null);
  const [absenceModal, setAbsenceModal] = useState<{ absence: CalendarAbsence | null } | null>(null);
  const [copyOpen, setCopyOpen] = useState(false);

  const from = anchor ? (view === "day" ? anchor : weekStart(anchor)) : null;
  const to = from ? (view === "day" ? from : addDays(from, 6)) : null;
  const requestKey = `${from}|${to}|${token}`;
  const loading = loadedKey !== requestKey;

  useEffect(() => {
    if (!canView) return;
    let cancelled = false;
    // Primera carga sin fecha: se pide la semana "de hoy" según el servidor (no según el dispositivo).
    const requestFrom = from ?? new Date().toISOString().slice(0, 10);
    const requestTo = to ?? addDays(requestFrom, 13);
    getCalendar(from ? requestFrom : addDays(requestFrom, -7), requestTo)
      .then((res) => {
        if (cancelled) return;
        if (!anchor) {
          setAnchor(res.today);
          return; // se vuelve a pedir con la semana correcta
        }
        setData(res);
        setError(null);
        setLoadedKey(requestKey);
      })
      .catch((err) => {
        if (!cancelled) {
          setError(errorText(err));
          setLoadedKey(requestKey);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [from, to, token, canView, anchor, requestKey]);

  const reload = useCallback(() => setToken((t) => t + 1), []);
  const saved = (message: string) => {
    setShiftModal(null);
    setAbsenceModal(null);
    setCopyOpen(false);
    showToast(message);
    reload();
  };

  const editable = data ? data.canManage || data.canEditOwn : false;
  const fixedAdvisorId = data && !data.canManage && data.canEditOwn ? (user?.id ?? null) : null;
  const editableAdvisors = data ? data.advisors.filter((a) => data.canManage || a.userId === user?.id) : [];

  const occurrences = useMemo(
    () => (data?.occurrences ?? []).filter((o) => (!advisorFilter || o.advisorId === advisorFilter) && (!kindFilter || o.kind === kindFilter)),
    [data, advisorFilter, kindFilter],
  );
  const absences = useMemo(() => (data?.absences ?? []).filter((a) => !advisorFilter || a.advisorId === advisorFilter), [data, advisorFilter]);
  const days = from && to ? Array.from({ length: view === "day" ? 1 : 7 }, (_, i) => addDays(from, i)) : [];

  if (!canView) {
    return (
      <PageContainer title="Calendario de asesores">
        <StateMessage kind="restricted" title="Acceso restringido" description="Tu usuario no tiene permiso para consultar el calendario." />
      </PageContainer>
    );
  }

  const canOpen = (o: ShiftOccurrence) => editable && (data?.canManage || o.advisorId === user?.id);

  return (
    <PageContainer
      title="Calendario de asesores"
      subtitle={
        <span className="inline-flex items-center gap-1.5">
          <Clock className="h-3.5 w-3.5" aria-hidden /> {BUSINESS_TIMEZONE_LABEL}
        </span>
      }
      action={
        editable && data ? (
          <>
            <Button variant="secondary" size="lg" onClick={() => setCopyOpen(true)}>
              <Copy className="h-5 w-5" aria-hidden /> Repetir semana
            </Button>
            <Button variant="secondary" size="lg" onClick={() => setAbsenceModal({ absence: null })}>
              <Plane className="h-5 w-5" aria-hidden /> Vacaciones o día libre
            </Button>
            <Button size="lg" onClick={() => setShiftModal({ occurrence: null, draft: { advisorId: advisorFilter || fixedAdvisorId || "", date: anchor ?? data.today } })}>
              <CalendarPlus className="h-5 w-5" aria-hidden /> Agregar horario
            </Button>
          </>
        ) : null
      }
    >
      {error && !data ? (
        <LoadError message={error} onRetry={reload} />
      ) : !data || !from || !to ? (
        <StateMessage kind="loading" title="Cargando calendario…" />
      ) : (
        <div className="space-y-4">
          <NowPanel data={data} />

          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div className="flex flex-wrap items-center gap-2">
              <Button variant="secondary" size="sm" aria-label={view === "day" ? "Día anterior" : "Semana anterior"} onClick={() => setAnchor(addDays(anchor!, view === "day" ? -1 : -7))}>
                <ChevronLeft className="h-4 w-4" aria-hidden />
              </Button>
              <Button variant="secondary" size="sm" onClick={() => setAnchor(data.today)}>
                Hoy
              </Button>
              <Button variant="secondary" size="sm" aria-label={view === "day" ? "Día siguiente" : "Semana siguiente"} onClick={() => setAnchor(addDays(anchor!, view === "day" ? 1 : 7))}>
                <ChevronRight className="h-4 w-4" aria-hidden />
              </Button>
              <p className="ml-1 text-sm font-semibold text-obsessed" aria-live="polite">
                {view === "day" ? formatDayLabel(from) : `${formatDayLabel(from)} – ${formatDayLabel(to)}`}
                {loading ? <span className="ml-2 text-xs font-normal text-muted">Actualizando…</span> : null}
              </p>
            </div>
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
              {data.advisors.length > 1 ? (
                <Select
                  aria-label="Filtrar por asesor"
                  value={advisorFilter}
                  onChange={(e) => setAdvisorFilter(e.target.value)}
                  options={[{ value: "", label: "Todos los asesores" }, ...data.advisors.map((a) => ({ value: a.userId, label: a.name }))]}
                  containerClassName="sm:w-56"
                />
              ) : null}
              <Select
                aria-label="Mostrar"
                value={kindFilter}
                onChange={(e) => setKindFilter(e.target.value as "" | ShiftKind)}
                options={[
                  { value: "", label: "Todos los horarios" },
                  { value: "REGULAR", label: "Solo horario normal" },
                  { value: "GUARD", label: "Solo guardias" },
                ]}
                containerClassName="sm:w-52"
              />
              <Segmented
                label="Vista"
                value={view}
                onChange={setView}
                options={[
                  { id: "people", label: "Horarios" },
                  { id: "week", label: "Calendario" },
                  { id: "agenda", label: "Lista por día" },
                ]}
              />
            </div>
          </div>

          {view !== "people" ? <Legend data={data} onPick={(id) => setAdvisorFilter(advisorFilter === id ? "" : id)} selected={advisorFilter} /> : null}

          {view === "people" ? (
            <PeopleWeek
              days={days}
              advisors={data.advisors.filter((a) => !advisorFilter || a.userId === advisorFilter)}
              occurrences={occurrences}
              absences={absences}
              today={data.today}
              canAdd={(id) => editable && (data.canManage || id === user?.id)}
              onOpen={(o) => canOpen(o) && setShiftModal({ occurrence: o, draft: null })}
              onAdd={(advisorId, date) => setShiftModal({ occurrence: null, draft: { advisorId, date } })}
              onOpenAbsence={(a) => editable && setAbsenceModal({ absence: a })}
            />
          ) : data.occurrences.length === 0 && data.absences.length === 0 ? (
            <StateMessage
              kind="empty"
              title="Sin turnos en estas fechas"
              description={editable ? "Crea el primer turno con “Nuevo turno”. Puedes repetirlo cada semana o copiar una semana completa." : "Aún no hay turnos registrados."}
            />
          ) : view === "agenda" ? (
            <Agenda days={days} occurrences={occurrences} absences={absences} gaps={advisorFilter ? [] : data.gaps} today={data.today} onOpen={(o) => canOpen(o) && setShiftModal({ occurrence: o, draft: null })} onOpenAbsence={(a) => editable && setAbsenceModal({ absence: a })} />
          ) : (
            <TimeGrid
              days={days}
              occurrences={occurrences}
              absences={absences}
              gaps={advisorFilter ? [] : data.gaps}
              now={data.now}
              today={data.today}
              onOpen={(o) => canOpen(o) && setShiftModal({ occurrence: o, draft: null })}
              onCreate={editable ? (date, hour) => setShiftModal({ occurrence: null, draft: { advisorId: advisorFilter || fixedAdvisorId || "", date, startTime: `${String(hour).padStart(2, "0")}:00`, endTime: `${String(Math.min(hour + 4, 23)).padStart(2, "0")}:00` } }) : undefined}
              onSelectDay={(d) => {
                setAnchor(d);
                setView("day");
              }}
            />
          )}

          {absences.length > 0 && view === "week" ? <AbsenceList absences={absences} onOpen={(a) => editable && setAbsenceModal({ absence: a })} /> : null}
          <AuditPanel />
        </div>
      )}

      {shiftModal && data ? (
        <ShiftModal
          advisors={editableAdvisors}
          occurrence={shiftModal.occurrence}
          draft={shiftModal.draft}
          fixedAdvisorId={fixedAdvisorId}
          today={data.today}
          onClose={() => setShiftModal(null)}
          onSaved={saved}
        />
      ) : null}
      {absenceModal && data ? (
        <AbsenceModal advisors={editableAdvisors} absence={absenceModal.absence} fixedAdvisorId={fixedAdvisorId} defaultDate={anchor ?? data.today} onClose={() => setAbsenceModal(null)} onSaved={saved} />
      ) : null}
      {copyOpen && data && from ? (
        <CopyModal advisors={editableAdvisors} selectedDay={anchor ?? data.today} weekFrom={weekStart(anchor ?? data.today)} fixedAdvisorId={fixedAdvisorId} today={data.today} onClose={() => setCopyOpen(false)} onSaved={saved} />
      ) : null}
    </PageContainer>
  );
}

// ---------------------------------------------------------------------------

function NowPanel({ data }: { data: CalendarView }) {
  const noCoverageNow = data.onShiftNow.filter((p) => p.assignable).length === 0;
  return (
    <section aria-label="Cobertura" className="grid grid-cols-1 gap-3 lg:grid-cols-3">
      <div className="rounded-xl border border-border bg-card p-4">
        <p className="text-sm font-semibold text-muted">Atendiendo ahora</p>
        {data.onShiftNow.length === 0 ? (
          <p className="mt-2 text-base text-muted">Nadie está atendiendo en este momento.</p>
        ) : (
          <ul className="mt-2 space-y-1.5">
            {data.onShiftNow.map((p) => (
              <li key={`${p.advisorId}-${p.start}`} className="flex items-center gap-2 text-base">
                <Dot id={p.advisorId} />
                <span className="truncate font-semibold text-obsessed">{p.name}</span>
                <span className="shrink-0 text-sm text-muted">hasta las {hhmm(p.end)}</span>
                {p.kind === "GUARD" ? <Badge tone="info">Guardia</Badge> : null}
              </li>
            ))}
          </ul>
        )}
      </div>
      <div className="rounded-xl border border-border bg-card p-4">
        <p className="text-sm font-semibold text-muted">Después entran</p>
        {data.nextUp.length === 0 ? (
          <p className="mt-2 text-base text-muted">Nadie más en los próximos dos días.</p>
        ) : (
          <ul className="mt-2 space-y-1.5">
            {data.nextUp.map((p) => (
              <li key={`${p.advisorId}-${p.start}`} className="flex items-center gap-2 text-base">
                <Dot id={p.advisorId} />
                <span className="truncate font-semibold text-obsessed">{p.name}</span>
                <span className="shrink-0 text-sm text-muted">{formatLocal(p.start)}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
      <div className={cn("rounded-xl border p-4", data.gaps.length || noCoverageNow ? "border-warning-text/25 bg-warning-bg" : "border-border bg-card")}>
        <p className="text-sm font-semibold text-muted">Clientes nuevos</p>
        {noCoverageNow ? (
          <p className="mt-2 flex items-start gap-2 text-base text-warning-text">
            <AlertTriangle className="mt-1 h-5 w-5 shrink-0" aria-hidden />
            <span>
              Ahora nadie los está recibiendo. Quedan guardados y se reparten
              {data.nextCoverage ? ` cuando entre alguien (${formatLocal(data.nextCoverage)}).` : " cuando haya un horario. Agrega horarios."}
            </span>
          </p>
        ) : (
          <p className="mt-2 text-base text-success-text">Sí hay quien los reciba ahora.</p>
        )}
        {data.gaps.length > 0 ? <p className="mt-1 text-sm text-obsessed">Esta semana hay horas sin nadie atendiendo (en rojo en el calendario).</p> : null}
      </div>
    </section>
  );
}

/** Tabla sencilla: una fila por asesor, una columna por día, con los horarios en letra grande. */
function PeopleWeek({
  days,
  advisors,
  occurrences,
  absences,
  today,
  canAdd,
  onOpen,
  onAdd,
  onOpenAbsence,
}: {
  days: string[];
  advisors: CalendarView["advisors"];
  occurrences: ShiftOccurrence[];
  absences: CalendarAbsence[];
  today: string;
  canAdd: (advisorId: string) => boolean;
  onOpen: (o: ShiftOccurrence) => void;
  onAdd: (advisorId: string, date: string) => void;
  onOpenAbsence: (a: CalendarAbsence) => void;
}) {
  if (advisors.length === 0) {
    return <StateMessage kind="empty" title="No hay asesores" description="Da de alta asesores en Usuarios y permisos." />;
  }
  const cell = (advisorId: string, d: string) => {
    const blocks = occurrences.filter((o) => o.advisorId === advisorId && o.date === d).sort((a, b) => a.start.localeCompare(b.start));
    const away = absences.filter((a) => a.advisorId === advisorId && a.start.slice(0, 10) <= d && a.end.slice(0, 10) >= d && !(a.end.slice(0, 10) === d && a.end.slice(11, 16) === "00:00"));
    return (
      <div className="flex flex-col gap-1.5">
        {away.map((a) => (
          <button key={a.id} type="button" onClick={() => onOpenAbsence(a)} className="rounded-lg bg-manual-bg px-2 py-1.5 text-left text-sm font-medium text-manual-text">
            <Plane className="mr-1 inline h-4 w-4" aria-hidden />
            {absenceKindLabels[a.kind]}
          </button>
        ))}
        {blocks.map((o) => {
          const c = advisorColor(o.advisorId);
          return (
            <button
              key={`${o.shiftId}-${o.date}`}
              type="button"
              onClick={() => onOpen(o)}
              className={cn("rounded-lg border-l-4 px-2 py-1.5 text-left text-base font-semibold leading-snug hover:shadow focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/40", o.fullyAbsent && "line-through opacity-60")}
              style={{ background: c.bg, borderLeftColor: c.border, color: c.text }}
            >
              {hhmm(o.start)} a {hhmm(o.end)}
              {o.kind === "GUARD" ? (
                <span className="mt-0.5 flex items-center gap-1 text-xs font-medium">
                  <Shield className="h-3.5 w-3.5" aria-hidden /> Guardia
                </span>
              ) : null}
            </button>
          );
        })}
        {blocks.length === 0 && away.length === 0 ? (
          canAdd(advisorId) && d >= today ? (
            <button
              type="button"
              onClick={() => onAdd(advisorId, d)}
              className="rounded-lg border-2 border-dashed border-border px-2 py-2 text-sm text-muted hover:border-gold hover:text-obsessed"
              aria-label={`Agregar horario el ${formatDayLabel(d)}`}
            >
              + Agregar
            </button>
          ) : (
            <span className="px-2 py-2 text-sm text-muted">Descansa</span>
          )
        ) : null}
      </div>
    );
  };

  return (
    <>
      {/* Computadora: tabla */}
      <div className="hidden overflow-x-auto rounded-xl border border-border bg-card md:block">
        <table className="w-full min-w-[900px] border-collapse">
          <thead>
            <tr className="bg-app-bg">
              <th className="w-48 px-3 py-3 text-left text-sm font-semibold text-muted">Asesor</th>
              {days.map((d) => (
                <th key={d} className={cn("px-2 py-3 text-left text-sm font-semibold capitalize", d === today ? "bg-gold/15 text-dark-gold" : "text-obsessed")}>
                  {formatDayLabel(d)}
                  {d === today ? " · hoy" : ""}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {advisors.map((a) => (
              <tr key={a.userId} className="border-t border-border align-top">
                <th scope="row" className="px-3 py-3 text-left">
                  <span className="flex items-center gap-2">
                    <Dot id={a.userId} />
                    <span className="text-base font-semibold text-obsessed">{a.name}</span>
                  </span>
                  <span className="mt-1 block text-sm font-normal text-muted">{advisorStatusLabels[a.status]}</span>
                </th>
                {days.map((d) => (
                  <td key={d} className={cn("px-2 py-3", d === today && "bg-gold/5")}>
                    {cell(a.userId, d)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {/* Celular: una tarjeta por asesor */}
      <ul className="space-y-3 md:hidden">
        {advisors.map((a) => (
          <li key={a.userId} className="rounded-xl border border-border bg-card">
            <p className="flex items-center gap-2 border-b border-border px-4 py-3">
              <Dot id={a.userId} />
              <span className="text-base font-semibold text-obsessed">{a.name}</span>
              <span className="ml-auto text-sm text-muted">{advisorStatusLabels[a.status]}</span>
            </p>
            <ul className="divide-y divide-border">
              {days.map((d) => (
                <li key={d} className={cn("grid grid-cols-[96px_minmax(0,1fr)] items-start gap-3 px-4 py-3", d === today && "bg-gold/5")}>
                  <span className={cn("pt-1.5 text-sm font-semibold capitalize", d === today ? "text-dark-gold" : "text-muted")}>{formatDayLabel(d)}</span>
                  {cell(a.userId, d)}
                </li>
              ))}
            </ul>
          </li>
        ))}
      </ul>
    </>
  );
}

function Dot({ id }: { id: string }) {
  const c = advisorColor(id);
  return <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ background: c.border }} aria-hidden />;
}

function Legend({ data, onPick, selected }: { data: CalendarView; onPick: (id: string) => void; selected: string }) {
  return (
    <ul className="flex flex-wrap gap-2" aria-label="Asesores">
      {data.advisors.map((a) => (
        <li key={a.userId}>
          <button
            type="button"
            onClick={() => onPick(a.userId)}
            aria-pressed={selected === a.userId}
            title={a.statusDetail + (a.nextShift ? ` · próximo turno ${formatLocal(a.nextShift)}` : "")}
            className={cn(
              "inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs transition-colors focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30",
              selected === a.userId ? "border-gold bg-gold/15" : "border-border bg-card hover:border-gold/60",
            )}
          >
            <Dot id={a.userId} />
            <span className="font-medium text-obsessed">{a.name}</span>
            <Badge tone={advisorStatusTones[a.status]} className="py-0">
              {advisorStatusLabels[a.status]}
            </Badge>
            {a.needsCalendar ? (
              <Badge tone="danger" className="py-0">
                Completar calendario{a.legacyShift ? ` (antes ${a.legacyShift})` : ""}
              </Badge>
            ) : null}
          </button>
        </li>
      ))}
    </ul>
  );
}

interface Piece {
  key: string;
  day: string;
  startMin: number;
  endMin: number;
  occurrence: ShiftOccurrence;
  continued: boolean;
}

/** Parte un turno en piezas por día (los nocturnos ocupan dos columnas). */
function piecesOf(o: ShiftOccurrence): Piece[] {
  const startDay = o.start.slice(0, 10);
  const endDay = o.end.slice(0, 10);
  if (startDay === endDay) return [{ key: `${o.shiftId}-${o.date}`, day: startDay, startMin: minutesOf(o.start), endMin: minutesOf(o.end), occurrence: o, continued: false }];
  const out: Piece[] = [{ key: `${o.shiftId}-${o.date}-a`, day: startDay, startMin: minutesOf(o.start), endMin: 24 * 60, occurrence: o, continued: false }];
  if (minutesOf(o.end) > 0) out.push({ key: `${o.shiftId}-${o.date}-b`, day: endDay, startMin: 0, endMin: minutesOf(o.end), occurrence: o, continued: true });
  return out;
}

function rangePieces(r: LocalRange): { day: string; startMin: number; endMin: number }[] {
  const out: { day: string; startMin: number; endMin: number }[] = [];
  let day = r.start.slice(0, 10);
  const endDay = r.end.slice(0, 10);
  let startMin = minutesOf(r.start);
  while (day < endDay) {
    out.push({ day, startMin, endMin: 24 * 60 });
    day = addDays(day, 1);
    startMin = 0;
  }
  const endMin = minutesOf(r.end);
  if (endMin > startMin) out.push({ day, startMin, endMin });
  return out;
}

const HOUR_PX = 44;

function TimeGrid({
  days,
  occurrences,
  absences,
  gaps,
  now,
  today,
  onOpen,
  onCreate,
  onSelectDay,
}: {
  days: string[];
  occurrences: ShiftOccurrence[];
  absences: CalendarAbsence[];
  gaps: LocalRange[];
  now: string;
  today: string;
  onOpen: (o: ShiftOccurrence) => void;
  onCreate?: (date: string, hour: number) => void;
  onSelectDay: (d: string) => void;
}) {
  const pieces = occurrences.flatMap(piecesOf).filter((p) => days.includes(p.day));
  const gapPieces = gaps.flatMap(rangePieces).filter((g) => days.includes(g.day));
  const minHour = Math.min(7, ...pieces.map((p) => Math.floor(p.startMin / 60)));
  const maxHour = Math.max(21, ...pieces.map((p) => Math.ceil(p.endMin / 60)));
  const hours = Array.from({ length: maxHour - minHour }, (_, i) => minHour + i);
  const height = hours.length * HOUR_PX;
  const y = (min: number) => ((min - minHour * 60) / 60) * HOUR_PX;

  return (
    <div className="overflow-x-auto rounded-xl border border-border bg-card">
      <div className={cn("grid", days.length > 1 ? "min-w-[860px]" : "min-w-0")} style={{ gridTemplateColumns: `56px repeat(${days.length}, minmax(0, 1fr))` }}>
        <div className="border-b border-border bg-app-bg" />
        {days.map((d) => {
          const dayAbsences = absences.filter((a) => a.start.slice(0, 10) <= d && a.end.slice(0, 10) >= d);
          return (
            <button
              key={d}
              type="button"
              onClick={() => onSelectDay(d)}
              className={cn("border-b border-l border-border px-2 py-2 text-left hover:bg-app-bg focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-inset focus-visible:ring-gold/30", d === today ? "bg-gold/10" : "bg-app-bg")}
            >
              <span className={cn("block text-sm font-semibold capitalize", d === today ? "text-dark-gold" : "text-obsessed")}>{formatDayLabel(d)}</span>
              {dayAbsences.length ? (
                <span className="block truncate text-[0.7rem] text-manual-text" title={dayAbsences.map((a) => `${a.advisorName}: ${absenceKindLabels[a.kind]}`).join(", ")}>
                  {dayAbsences.length} ausencia{dayAbsences.length === 1 ? "" : "s"}
                </span>
              ) : (
                <span className="block text-[0.7rem] text-transparent">.</span>
              )}
            </button>
          );
        })}

        <div className="relative" style={{ height }}>
          {hours.map((h) => (
            <span key={h} className="absolute right-2 -translate-y-1/2 text-[0.68rem] tabular-nums text-muted" style={{ top: y(h * 60) }}>
              {h > minHour ? `${String(h).padStart(2, "0")}:00` : ""}
            </span>
          ))}
        </div>
        {days.map((d) => {
          const dayPieces = pieces.filter((p) => p.day === d).sort((a, b) => a.startMin - b.startMin);
          // Carriles para que los turnos simultáneos no se encimen.
          const lanes: number[] = [];
          const laneOf = new Map<string, number>();
          for (const p of dayPieces) {
            let lane = lanes.findIndex((end) => end <= p.startMin);
            if (lane === -1) {
              lane = lanes.length;
              lanes.push(p.endMin);
            } else lanes[lane] = p.endMin;
            laneOf.set(p.key, lane);
          }
          const laneCount = Math.max(1, lanes.length);
          return (
            <div key={d} className="relative border-l border-border" style={{ height }}>
              {hours.map((h) => (
                <button
                  key={h}
                  type="button"
                  tabIndex={-1}
                  aria-hidden
                  disabled={!onCreate}
                  onClick={() => onCreate?.(d, h)}
                  className="absolute inset-x-0 border-t border-border/60 enabled:hover:bg-gold/5"
                  style={{ top: y(h * 60), height: HOUR_PX }}
                />
              ))}
              {gapPieces
                .filter((g) => g.day === d)
                .map((g, i) => (
                  <div
                    key={`gap-${i}`}
                    className="pointer-events-none absolute inset-x-0 border-y border-danger-text/30 bg-danger-bg/70"
                    style={{ top: y(Math.max(g.startMin, minHour * 60)), height: Math.max(2, y(Math.min(g.endMin, maxHour * 60)) - y(Math.max(g.startMin, minHour * 60))) }}
                    title="Sin cobertura para la asignación"
                  >
                    {g.endMin - g.startMin >= 60 ? <span className="absolute left-1 top-0.5 text-[0.65rem] font-medium text-danger-text">Sin cobertura</span> : null}
                  </div>
                ))}
              {dayPieces.map((p) => {
                const o = p.occurrence;
                const c = advisorColor(o.advisorId);
                const lane = laneOf.get(p.key) ?? 0;
                const top = y(Math.max(p.startMin, minHour * 60));
                const h = Math.max(22, y(Math.min(p.endMin, maxHour * 60)) - top);
                return (
                  <button
                    key={p.key}
                    type="button"
                    onClick={() => onOpen(o)}
                    title={`${o.advisorName} · ${shiftKindLabels[o.kind]} · ${hhmm(o.start)}–${hhmm(o.end)}${o.notes ? ` · ${o.notes}` : ""}`}
                    className={cn(
                      "absolute overflow-hidden rounded-lg border-l-4 px-1.5 py-1 text-left text-[0.72rem] leading-tight shadow-sm transition-shadow hover:shadow-md focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/40",
                      o.fullyAbsent && "opacity-50",
                      o.kind === "GUARD" && "border-dashed",
                    )}
                    style={{
                      top,
                      height: h,
                      left: `calc(${(lane / laneCount) * 100}% + 2px)`,
                      width: `calc(${100 / laneCount}% - 4px)`,
                      background: o.fullyAbsent ? `repeating-linear-gradient(135deg, ${c.bg}, ${c.bg} 6px, transparent 6px, transparent 12px)` : c.bg,
                      borderLeftColor: c.border,
                      color: c.text,
                    }}
                  >
                    <span className="block truncate font-semibold">{o.advisorName}</span>
                    <span className="block truncate">
                      {p.continued ? "↳ " : ""}
                      {hhmm(o.start)}–{hhmm(o.end)}
                    </span>
                    <span className="block truncate">
                      {o.kind === "GUARD" ? "Guardia" : "Jornada"}
                      {!o.assignable ? " · sin ronda" : ""}
                      {o.fullyAbsent ? " · ausente" : o.absentParts.length ? " · ausencia parcial" : ""}
                    </span>
                  </button>
                );
              })}
              {d === today ? (
                <div className="pointer-events-none absolute inset-x-0 z-10 border-t-2 border-danger-text" style={{ top: y(minutesOf(now)) }} aria-label={`Ahora, ${hhmm(now)}`}>
                  <span className="absolute -left-1 -top-1.5 h-3 w-3 rounded-full bg-danger-text" />
                </div>
              ) : null}
            </div>
          );
        })}
      </div>
    </div>
  );
}

function Agenda({
  days,
  occurrences,
  absences,
  gaps,
  today,
  onOpen,
  onOpenAbsence,
}: {
  days: string[];
  occurrences: ShiftOccurrence[];
  absences: CalendarAbsence[];
  gaps: LocalRange[];
  today: string;
  onOpen: (o: ShiftOccurrence) => void;
  onOpenAbsence: (a: CalendarAbsence) => void;
}) {
  return (
    <ol className="space-y-3">
      {days.map((d) => {
        const items = occurrences.filter((o) => o.date === d).sort((a, b) => a.start.localeCompare(b.start));
        const dayAbsences = absences.filter((a) => a.start.slice(0, 10) <= d && a.end.slice(0, 10) >= d);
        const dayGaps = gaps.flatMap(rangePieces).filter((g) => g.day === d);
        return (
          <li key={d} className="rounded-xl border border-border bg-card">
            <p className={cn("border-b border-border px-4 py-2.5 text-sm font-semibold capitalize", d === today ? "bg-gold/10 text-dark-gold" : "text-obsessed")}>
              {formatDayLabel(d)}
              {d === today ? " · hoy" : ""}
            </p>
            <ul className="divide-y divide-border">
              {items.map((o) => {
                const c = advisorColor(o.advisorId);
                return (
                  <li key={`${o.shiftId}-${o.date}`}>
                    <button type="button" onClick={() => onOpen(o)} className="flex w-full items-center gap-3 px-4 py-3 text-left hover:bg-app-bg focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-inset focus-visible:ring-gold/30">
                      <span className="h-10 w-1.5 shrink-0 rounded-full" style={{ background: c.border }} aria-hidden />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm font-semibold text-obsessed">{o.advisorName}</span>
                        <span className="block text-xs text-muted">
                          {hhmm(o.start)}–{hhmm(o.end)}
                          {o.end.slice(0, 10) !== o.date ? " (termina al día siguiente)" : ""}
                        </span>
                      </span>
                      <span className="flex shrink-0 flex-col items-end gap-1">
                        <Badge tone={o.kind === "GUARD" ? "info" : "neutral"}>
                          {o.kind === "GUARD" ? <Shield aria-hidden /> : <UserRound aria-hidden />}
                          {shiftKindLabels[o.kind]}
                        </Badge>
                        {o.fullyAbsent ? <Badge tone="manual">Ausente</Badge> : !o.assignable ? <Badge tone="neutral">Sin ronda</Badge> : null}
                      </span>
                    </button>
                  </li>
                );
              })}
              {dayAbsences.map((a) => (
                <li key={a.id}>
                  <button type="button" onClick={() => onOpenAbsence(a)} className="flex w-full items-center gap-3 px-4 py-2.5 text-left text-sm hover:bg-app-bg">
                    <Plane className="h-4 w-4 shrink-0 text-manual-text" aria-hidden />
                    <span className="min-w-0 flex-1 truncate text-obsessed">
                      {a.advisorName}: {absenceKindLabels[a.kind]}
                    </span>
                    <span className="shrink-0 text-xs text-muted">
                      {formatLocal(a.start)} – {formatLocal(a.end)}
                    </span>
                  </button>
                </li>
              ))}
              {dayGaps.map((g, i) => (
                <li key={`g-${i}`} className="flex items-center gap-2 px-4 py-2 text-xs text-danger-text">
                  <AlertTriangle className="h-3.5 w-3.5" aria-hidden /> Sin cobertura de {String(Math.floor(g.startMin / 60)).padStart(2, "0")}:{String(g.startMin % 60).padStart(2, "0")} a{" "}
                  {g.endMin >= 1440 ? "24:00" : `${String(Math.floor(g.endMin / 60)).padStart(2, "0")}:${String(g.endMin % 60).padStart(2, "0")}`}
                </li>
              ))}
              {items.length === 0 && dayAbsences.length === 0 && dayGaps.length === 0 ? <li className="px-4 py-3 text-sm text-muted">Sin turnos.</li> : null}
            </ul>
          </li>
        );
      })}
    </ol>
  );
}

function AbsenceList({ absences, onOpen }: { absences: CalendarAbsence[]; onOpen: (a: CalendarAbsence) => void }) {
  return (
    <section className="rounded-xl border border-border bg-card p-4" aria-label="Ausencias">
      <p className="mb-2 text-xs font-medium uppercase tracking-wide text-muted">Ausencias en estas fechas</p>
      <ul className="space-y-1.5">
        {absences.map((a) => (
          <li key={a.id}>
            <button type="button" onClick={() => onOpen(a)} className="flex w-full flex-wrap items-center gap-2 rounded-lg px-1 py-1 text-left text-sm hover:bg-app-bg">
              <Dot id={a.advisorId} />
              <span className="font-medium text-obsessed">{a.advisorName}</span>
              <Badge tone="manual">{absenceKindLabels[a.kind]}</Badge>
              <span className="text-xs text-muted">
                {formatLocal(a.start)} – {formatLocal(a.end)}
              </span>
              {a.notes ? <span className="text-xs text-muted">· {a.notes}</span> : null}
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}

function AuditPanel() {
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<CalendarAuditEntry[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  return (
    <section className="rounded-xl border border-border bg-card">
      <button
        type="button"
        aria-expanded={open}
        onClick={() => {
          setOpen(!open);
          if (!items) getCalendarAudit().then(setItems).catch((err) => setError(errorText(err)));
        }}
        className="flex w-full items-center gap-2 px-4 py-3 text-left text-sm font-medium text-obsessed hover:bg-app-bg"
      >
        <History className="h-4 w-4 text-muted" aria-hidden /> Historial de cambios del calendario
      </button>
      {open ? (
        <div className="border-t border-border px-4 py-3">
          {error ? (
            <p className="text-sm text-danger-text">{error}</p>
          ) : !items ? (
            <p className="text-sm text-muted">Cargando…</p>
          ) : items.length === 0 ? (
            <p className="text-sm text-muted">Sin cambios registrados.</p>
          ) : (
            <ul className="space-y-2">
              {items.map((a) => (
                <li key={a.id} className="text-sm">
                  <span className="text-obsessed">{a.detail}</span>
                  <span className="block text-xs text-muted">
                    {a.action === "CREATED" ? "Creado" : a.action === "UPDATED" ? "Modificado" : "Eliminado"} por {a.actorName ?? "el sistema"} ·{" "}
                    {new Intl.DateTimeFormat("es-MX", { dateStyle: "medium", timeStyle: "short", timeZone: "America/Mexico_City" }).format(new Date(a.createdAt))}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      ) : null}
    </section>
  );
}
