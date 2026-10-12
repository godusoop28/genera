"use client";

import { errorText } from "@/components/crm/CrmUi";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import {
  copyShifts,
  createAbsence,
  createShift,
  deleteAbsence,
  deleteShift,
  updateAbsence,
  updateShift,
  type AbsenceKind,
  type CalendarAbsence,
  type CalendarAdvisor,
  type EditScope,
  type ShiftKind,
  type ShiftOccurrence,
} from "@/lib/api/crm";
import { addDays, dayNames, formatDayLabel, hhmm, isoDay } from "@/lib/crm/labels";
import { cn } from "@/lib/utils";
import { ChevronDown, Moon, Shield, Sun } from "lucide-react";
import { useState, type ReactNode } from "react";

// Formularios del calendario pensados para usarse fácil: botones grandes,
// pocas preguntas y en palabras simples. Las opciones poco comunes quedan en
// "Más opciones". Todas las horas son de Ciudad de México.

const field = "w-full rounded-xl border-2 border-border bg-card px-4 py-3 text-base text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20";

function Question({ title, help, children }: { title: string; help?: string; children: ReactNode }) {
  return (
    <section className="space-y-2">
      <h3 className="text-base font-semibold text-obsessed">{title}</h3>
      {help ? <p className="text-sm text-muted">{help}</p> : null}
      {children}
    </section>
  );
}

function BigChoice({ selected, onClick, children, disabled }: { selected: boolean; onClick: () => void; children: ReactNode; disabled?: boolean }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-pressed={selected}
      className={cn(
        "flex min-h-14 w-full items-center gap-3 rounded-xl border-2 px-4 py-3 text-left text-base transition-colors focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30 disabled:opacity-60",
        selected ? "border-gold bg-gold/15 font-semibold text-obsessed" : "border-border bg-card text-obsessed hover:border-gold/60",
      )}
    >
      <span className={cn("flex h-5 w-5 shrink-0 items-center justify-center rounded-full border-2", selected ? "border-dark-gold" : "border-border")} aria-hidden>
        {selected ? <span className="h-2.5 w-2.5 rounded-full bg-dark-gold" /> : null}
      </span>
      <span className="min-w-0">{children}</span>
    </button>
  );
}

function ErrorBox({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <p role="alert" className="rounded-xl bg-danger-bg px-4 py-3 text-base text-danger-text">
      {message}
    </p>
  );
}

function MoreOptions({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="rounded-xl border border-border">
      <button type="button" aria-expanded={open} onClick={() => setOpen(!open)} className="flex w-full items-center justify-between px-4 py-3 text-left text-base font-medium text-obsessed">
        Más opciones
        <ChevronDown className={cn("h-5 w-5 text-muted transition-transform", open && "rotate-180")} aria-hidden />
      </button>
      {open ? <div className="space-y-4 border-t border-border px-4 py-4">{children}</div> : null}
    </div>
  );
}

/** Horas cada 30 minutos con texto fácil de leer ("9:00 de la mañana"). */
const TIME_OPTIONS = Array.from({ length: 48 }, (_, i) => {
  const h = Math.floor(i / 2);
  const m = i % 2 === 0 ? "00" : "30";
  const value = `${String(h).padStart(2, "0")}:${m}`;
  const h12 = h % 12 === 0 ? 12 : h % 12;
  const part = h < 12 ? (h === 0 ? "de la noche" : "de la mañana") : h < 19 ? "de la tarde" : "de la noche";
  return { value, label: `${h12}:${m} ${part}` };
});

function timeLabel(value: string) {
  return TIME_OPTIONS.find((t) => t.value === value)?.label ?? value;
}

function TimeSelect({ label, value, onChange, id }: { label: string; value: string; onChange: (v: string) => void; id: string }) {
  const options = TIME_OPTIONS.some((t) => t.value === value) ? TIME_OPTIONS : [{ value, label: value }, ...TIME_OPTIONS];
  return (
    <div className="flex-1 space-y-1.5">
      <label htmlFor={id} className="block text-sm font-medium text-muted">
        {label}
      </label>
      <select id={id} value={value} onChange={(e) => onChange(e.target.value)} className={field}>
        {options.map((t) => (
          <option key={t.value} value={t.value}>
            {t.label}
          </option>
        ))}
      </select>
    </div>
  );
}

function AdvisorPicker({ advisors, value, onChange, fixed }: { advisors: CalendarAdvisor[]; value: string; onChange: (id: string) => void; fixed: boolean }) {
  if (fixed || advisors.length === 1) {
    return <p className="rounded-xl bg-app-bg px-4 py-3 text-base font-semibold text-obsessed">{advisors.find((a) => a.userId === value)?.name ?? advisors[0]?.name}</p>;
  }
  return (
    <select aria-label="Asesor" value={value} onChange={(e) => onChange(e.target.value)} className={field}>
      {advisors.map((a) => (
        <option key={a.userId} value={a.userId}>
          {a.name}
        </option>
      ))}
    </select>
  );
}

const WEEKDAYS = [1, 2, 3, 4, 5];
const WEEKEND = [6, 7];
const ALL_DAYS = [1, 2, 3, 4, 5, 6, 7];
const same = (a: number[], b: number[]) => a.length === b.length && a.every((x) => b.includes(x));

export interface ShiftDraft {
  advisorId: string;
  date: string;
  startTime?: string;
  endTime?: string;
}

/** Agregar o cambiar el horario de un asesor. */
export function ShiftModal({
  advisors,
  occurrence,
  draft,
  fixedAdvisorId,
  today,
  onClose,
  onSaved,
}: {
  advisors: CalendarAdvisor[];
  occurrence: ShiftOccurrence | null;
  draft: ShiftDraft | null;
  fixedAdvisorId: string | null;
  today: string;
  onClose: () => void;
  onSaved: (message: string) => void;
}) {
  const editing = occurrence !== null;
  const isSeries = occurrence?.recurrence === "WEEKLY";
  const baseDate = occurrence?.date ?? draft?.date ?? today;
  const [advisorId, setAdvisorId] = useState(occurrence?.advisorId ?? fixedAdvisorId ?? (draft?.advisorId || advisors[0]?.userId) ?? "");
  const [kind, setKind] = useState<ShiftKind>(occurrence?.kind ?? "REGULAR");
  const [repeat, setRepeat] = useState(occurrence ? occurrence.recurrence === "WEEKLY" : true);
  const [days, setDays] = useState<number[]>(occurrence?.days.length ? occurrence.days : editing ? [isoDay(baseDate)] : WEEKDAYS);
  const [date, setDate] = useState(occurrence?.date ?? (baseDate < today ? today : baseDate));
  const [until, setUntil] = useState(occurrence?.seriesEnd ?? "");
  const [startTime, setStartTime] = useState(occurrence ? hhmm(occurrence.start) : (draft?.startTime ?? "09:00"));
  const [endTime, setEndTime] = useState(occurrence ? hhmm(occurrence.end) : (draft?.endTime ?? "14:00"));
  const [assignable, setAssignable] = useState(occurrence?.assignable ?? true);
  const [notes, setNotes] = useState(occurrence?.notes ?? "");
  const [scope, setScope] = useState<EditScope | null>(isSeries ? null : "SERIES");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const onlyThisDay = editing && isSeries && scope === "OCCURRENCE";
  const weekly = !onlyThisDay && repeat;
  const nextDay = endTime <= startTime;
  const valid = !!advisorId && startTime !== endTime && (!weekly || days.length > 0) && (!until || until >= date) && scope !== null;
  const isPast = editing && !isSeries && occurrence.date < today;

  const save = async () => {
    setSaving(true);
    setError(null);
    const input = {
      advisorId,
      kind,
      recurrence: weekly ? ("WEEKLY" as const) : ("ONCE" as const),
      days: weekly ? days : [],
      startDate: onlyThisDay ? occurrence.date : date,
      endDate: weekly && until ? until : null,
      startTime,
      endTime,
      assignable,
      notes: notes.trim() || null,
    };
    try {
      if (editing) {
        await updateShift(occurrence.shiftId, input, scope ?? "SERIES", occurrence.date);
        onSaved("Horario guardado");
      } else {
        await createShift(input);
        onSaved("Horario agregado");
      }
    } catch (err) {
      setError(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    setSaving(true);
    setError(null);
    try {
      await deleteShift(occurrence!.shiftId, scope ?? "SERIES", occurrence!.date);
      onSaved("Horario quitado");
    } catch (err) {
      setError(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  const dayName = dayNames[isoDay(baseDate)].toLowerCase();

  return (
    <Modal
      open
      onClose={onClose}
      title={editing ? "Cambiar horario" : "Agregar horario"}
      size="lg"
      footer={
        <>
          {editing && scope ? (
            <Button variant="danger" size="lg" onClick={() => void remove()} disabled={saving || isPast} className="mr-auto">
              {onlyThisDay ? "Quitar solo este día" : "Quitar este horario"}
            </Button>
          ) : null}
          <Button variant="secondary" size="lg" onClick={onClose}>
            Cancelar
          </Button>
          <Button size="lg" onClick={() => void save()} disabled={!valid || saving || isPast}>
            {saving ? "Guardando…" : "Guardar"}
          </Button>
        </>
      }
    >
      <div className="space-y-6">
        {isPast ? <p className="rounded-xl bg-app-bg px-4 py-3 text-base text-muted">Este horario ya pasó. Se guarda como historial y no se puede cambiar.</p> : null}

        {editing && isSeries ? (
          <Question title="¿Qué quieres cambiar?">
            <div className="grid gap-2 sm:grid-cols-2">
              <BigChoice selected={scope === "OCCURRENCE"} onClick={() => setScope("OCCURRENCE")}>
                Solo el {formatDayLabel(occurrence.date)}
              </BigChoice>
              <BigChoice selected={scope === "SERIES"} onClick={() => setScope("SERIES")}>
                Todos los {dayName === "sábado" || dayName === "domingo" ? `${dayName}s` : dayName.endsWith("s") ? dayName : `${dayName}s`} de aquí en adelante
              </BigChoice>
            </div>
          </Question>
        ) : null}

        {scope !== null ? (
          <>
            <Question title="¿Quién?">
              <AdvisorPicker advisors={advisors} value={advisorId} onChange={setAdvisorId} fixed={!!fixedAdvisorId || onlyThisDay} />
            </Question>

            {!onlyThisDay ? (
              <Question title={repeat ? "¿Qué días trabaja?" : "¿Qué día?"}>
                {repeat ? (
                  <>
                    <div className="flex flex-wrap gap-2">
                      {[
                        ["Lunes a viernes", WEEKDAYS],
                        ["Sábado y domingo", WEEKEND],
                        ["Toda la semana", ALL_DAYS],
                      ].map(([label, preset]) => (
                        <button
                          key={label as string}
                          type="button"
                          onClick={() => setDays(preset as number[])}
                          className={cn(
                            "min-h-12 rounded-xl border-2 px-4 text-base font-medium",
                            same(days, preset as number[]) ? "border-gold bg-gold/15 text-obsessed" : "border-border bg-card text-obsessed hover:border-gold/60",
                          )}
                        >
                          {label as string}
                        </button>
                      ))}
                    </div>
                    <div className="grid grid-cols-4 gap-2 sm:grid-cols-7">
                      {ALL_DAYS.map((d) => {
                        const on = days.includes(d);
                        return (
                          <button
                            key={d}
                            type="button"
                            aria-pressed={on}
                            onClick={() => setDays(on ? days.filter((x) => x !== d) : [...days, d].sort())}
                            className={cn(
                              "min-h-14 rounded-xl border-2 text-base font-semibold transition-colors focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30",
                              on ? "border-gold bg-gold text-brand-ink" : "border-border bg-card text-muted hover:border-gold/60",
                            )}
                          >
                            {dayNames[d].slice(0, 3)}
                          </button>
                        );
                      })}
                    </div>
                  </>
                ) : (
                  <input type="date" aria-label="Fecha" min={today} value={date} onChange={(e) => setDate(e.target.value)} className={field} />
                )}
              </Question>
            ) : null}

            <Question title="¿A qué hora?">
              <div className="flex flex-col gap-3 sm:flex-row">
                <TimeSelect id="shift-start" label="Entra" value={startTime} onChange={setStartTime} />
                <TimeSelect id="shift-end" label="Sale" value={endTime} onChange={setEndTime} />
              </div>
              {startTime === endTime ? (
                <p className="text-base text-danger-text">La hora de entrada y de salida no pueden ser iguales.</p>
              ) : nextDay ? (
                <p className="flex items-center gap-2 text-base text-info-text">
                  <Moon className="h-5 w-5" aria-hidden /> Sale al día siguiente a las {timeLabel(endTime)}.
                </p>
              ) : (
                <p className="flex items-center gap-2 text-base text-muted">
                  <Sun className="h-5 w-5" aria-hidden /> De {timeLabel(startTime)} a {timeLabel(endTime)}.
                </p>
              )}
              <p className="text-sm text-muted">¿Trabaja en dos partes (por ejemplo, mañana y tarde)? Guarda este horario y luego agrega el segundo.</p>
            </Question>

            <Question title="¿Es una guardia?" help="Una guardia es el turno de quien atiende a los clientes cuando el asesor de la propiedad no está.">
              <div className="grid gap-2 sm:grid-cols-2">
                <BigChoice selected={kind === "REGULAR"} onClick={() => setKind("REGULAR")}>
                  No, es su horario normal
                </BigChoice>
                <BigChoice selected={kind === "GUARD"} onClick={() => setKind("GUARD")}>
                  <span className="inline-flex items-center gap-2">
                    <Shield className="h-5 w-5 text-info-text" aria-hidden /> Sí, es guardia
                  </span>
                </BigChoice>
              </div>
            </Question>

            <MoreOptions>
              {!editing ? (
                <Question title="¿Se repite cada semana?">
                  <div className="grid gap-2 sm:grid-cols-2">
                    <BigChoice selected={repeat} onClick={() => setRepeat(true)}>
                      Sí, cada semana
                    </BigChoice>
                    <BigChoice selected={!repeat} onClick={() => setRepeat(false)}>
                      No, solo un día
                    </BigChoice>
                  </div>
                </Question>
              ) : null}
              {weekly ? (
                <>
                  <Question title="¿Desde cuándo?">
                    <input type="date" aria-label="Desde" min={today} value={date} onChange={(e) => setDate(e.target.value)} className={field} />
                  </Question>
                  <Question title="¿Hasta cuándo?" help="Déjalo vacío si no tiene fecha de fin.">
                    <input type="date" aria-label="Hasta" min={date} value={until} onChange={(e) => setUntil(e.target.value)} className={field} />
                  </Question>
                </>
              ) : null}
              <Question title="¿Recibe clientes nuevos en este horario?" help="Si eliges “No”, el horario se ve en el calendario pero no le llegan clientes del reparto automático.">
                <div className="grid gap-2 sm:grid-cols-2">
                  <BigChoice selected={assignable} onClick={() => setAssignable(true)}>
                    Sí
                  </BigChoice>
                  <BigChoice selected={!assignable} onClick={() => setAssignable(false)}>
                    No
                  </BigChoice>
                </div>
              </Question>
              <Question title="Nota (opcional)">
                <input value={notes} onChange={(e) => setNotes(e.target.value)} className={field} placeholder="Ej. cubre a Eva" />
              </Question>
            </MoreOptions>
          </>
        ) : null}
        <ErrorBox message={error} />
      </div>
    </Modal>
  );
}

const ABSENCE_ORDER: AbsenceKind[] = ["VACATION", "REST", "PERMISSION", "UNAVAILABLE"];
const ABSENCE_TEXT: Record<AbsenceKind, string> = { VACATION: "Vacaciones", REST: "Día de descanso", PERMISSION: "Permiso", UNAVAILABLE: "No puede atender" };

/** Vacaciones o días libres: mientras dure, no le llegan clientes aunque tenga horario. */
export function AbsenceModal({
  advisors,
  absence,
  fixedAdvisorId,
  defaultDate,
  onClose,
  onSaved,
}: {
  advisors: CalendarAdvisor[];
  absence: CalendarAbsence | null;
  fixedAdvisorId: string | null;
  defaultDate: string;
  onClose: () => void;
  onSaved: (message: string) => void;
}) {
  const fullDays = !absence || (absence.start.slice(11, 16) === "00:00" && (absence.end.slice(11, 16) === "00:00" || absence.end.slice(11, 16) === "23:59"));
  const [advisorId, setAdvisorId] = useState(absence?.advisorId ?? fixedAdvisorId ?? advisors[0]?.userId ?? "");
  const [kind, setKind] = useState<AbsenceKind>(absence?.kind ?? "VACATION");
  const [fromDay, setFromDay] = useState(absence?.start.slice(0, 10) ?? defaultDate);
  const [toDay, setToDay] = useState(absence ? (absence.end.slice(11, 16) === "00:00" ? addDays(absence.end.slice(0, 10), -1) : absence.end.slice(0, 10)) : defaultDate);
  const [hours, setHours] = useState(!fullDays);
  const [fromTime, setFromTime] = useState(absence && !fullDays ? hhmm(absence.start) : "09:00");
  const [toTime, setToTime] = useState(absence && !fullDays ? hhmm(absence.end) : "14:00");
  const [notes, setNotes] = useState(absence?.notes ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const start = hours ? `${fromDay}T${fromTime}:00` : `${fromDay}T00:00:00`;
  const end = hours ? `${fromDay}T${toTime}:00` : `${addDays(toDay, 1)}T00:00:00`;
  const valid = !!advisorId && !!fromDay && end > start && (hours || toDay >= fromDay);

  const run = async (action: () => Promise<unknown>, message: string) => {
    setSaving(true);
    setError(null);
    try {
      await action();
      onSaved(message);
    } catch (err) {
      setError(errorText(err));
    } finally {
      setSaving(false);
    }
  };
  const input = () => ({ advisorId, kind, start, end, notes: notes.trim() || null });

  return (
    <Modal
      open
      onClose={onClose}
      title={absence ? "Cambiar vacaciones o día libre" : "Vacaciones o día libre"}
      size="lg"
      footer={
        <>
          {absence ? (
            <Button variant="danger" size="lg" className="mr-auto" disabled={saving} onClick={() => void run(() => deleteAbsence(absence.id), "Se quitó")}>
              Quitar
            </Button>
          ) : null}
          <Button variant="secondary" size="lg" onClick={onClose}>
            Cancelar
          </Button>
          <Button size="lg" disabled={!valid || saving} onClick={() => void run(() => (absence ? updateAbsence(absence.id, input()) : createAbsence(input())), "Guardado")}>
            {saving ? "Guardando…" : "Guardar"}
          </Button>
        </>
      }
    >
      <div className="space-y-6">
        <p className="text-base text-muted">Mientras no esté, no le llegan clientes nuevos aunque tenga horario.</p>
        <Question title="¿Quién?">
          <AdvisorPicker advisors={advisors} value={advisorId} onChange={setAdvisorId} fixed={!!fixedAdvisorId} />
        </Question>
        <Question title="¿Por qué?">
          <div className="grid gap-2 sm:grid-cols-2">
            {ABSENCE_ORDER.map((k) => (
              <BigChoice key={k} selected={kind === k} onClick={() => setKind(k)}>
                {ABSENCE_TEXT[k]}
              </BigChoice>
            ))}
          </div>
        </Question>
        {hours ? (
          <Question title="¿Qué día y a qué hora?">
            <input type="date" aria-label="Día" value={fromDay} onChange={(e) => setFromDay(e.target.value)} className={field} />
            <div className="flex flex-col gap-3 sm:flex-row">
              <TimeSelect id="abs-from" label="Desde" value={fromTime} onChange={setFromTime} />
              <TimeSelect id="abs-to" label="Hasta" value={toTime} onChange={setToTime} />
            </div>
          </Question>
        ) : (
          <Question title="¿Qué días?" help="Incluye el primero y el último día.">
            <div className="flex flex-col gap-3 sm:flex-row">
              <div className="flex-1 space-y-1.5">
                <label htmlFor="abs-from-day" className="block text-sm font-medium text-muted">
                  Del día
                </label>
                <input id="abs-from-day" type="date" value={fromDay} onChange={(e) => setFromDay(e.target.value)} className={field} />
              </div>
              <div className="flex-1 space-y-1.5">
                <label htmlFor="abs-to-day" className="block text-sm font-medium text-muted">
                  Al día
                </label>
                <input id="abs-to-day" type="date" min={fromDay} value={toDay} onChange={(e) => setToDay(e.target.value)} className={field} />
              </div>
            </div>
          </Question>
        )}
        <MoreOptions>
          <Question title="¿Es solo unas horas?">
            <div className="grid gap-2 sm:grid-cols-2">
              <BigChoice selected={!hours} onClick={() => setHours(false)}>
                No, días completos
              </BigChoice>
              <BigChoice selected={hours} onClick={() => setHours(true)}>
                Sí, solo unas horas
              </BigChoice>
            </div>
          </Question>
          <Question title="Nota (opcional)">
            <input value={notes} onChange={(e) => setNotes(e.target.value)} className={field} />
          </Question>
        </MoreOptions>
        <ErrorBox message={error} />
      </div>
    </Modal>
  );
}

/** Repetir los horarios de una semana (o un día) en otra fecha. */
export function CopyModal({
  advisors,
  selectedDay,
  weekFrom,
  fixedAdvisorId,
  today,
  onClose,
  onSaved,
}: {
  advisors: CalendarAdvisor[];
  selectedDay: string;
  weekFrom: string;
  fixedAdvisorId: string | null;
  today: string;
  onClose: () => void;
  onSaved: (message: string) => void;
}) {
  const [what, setWhat] = useState<"day" | "week">("week");
  const [target, setTarget] = useState(addDays(weekFrom, 7) < today ? today : addDays(weekFrom, 7));
  const [advisorId, setAdvisorId] = useState(fixedAdvisorId ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const source = what === "day" ? selectedDay : weekFrom;

  return (
    <Modal
      open
      onClose={onClose}
      title="Repetir horarios en otra fecha"
      size="lg"
      footer={
        <>
          <Button variant="secondary" size="lg" onClick={onClose}>
            Cerrar
          </Button>
          <Button
            size="lg"
            disabled={!target || saving}
            onClick={async () => {
              setSaving(true);
              setError(null);
              try {
                const res = await copyShifts(source, what === "day" ? 1 : 7, target, advisorId || undefined);
                if (res.skipped === 0) onSaved(`Se repitieron ${res.created} horario(s)`);
                else if (res.created > 0) setError(`Se repitieron ${res.created}. ${res.skipped} no, porque ya había otro horario a esa hora.`);
                else setError("No se repitió ninguno: ya había horarios a esas horas.");
              } catch (err) {
                setError(errorText(err));
              } finally {
                setSaving(false);
              }
            }}
          >
            {saving ? "Repitiendo…" : "Repetir"}
          </Button>
        </>
      }
    >
      <div className="space-y-6">
        <Question title="¿Qué quieres repetir?">
          <div className="grid gap-2 sm:grid-cols-2">
            <BigChoice selected={what === "week"} onClick={() => setWhat("week")}>
              La semana del {formatDayLabel(weekFrom)}
            </BigChoice>
            <BigChoice selected={what === "day"} onClick={() => setWhat("day")}>
              Solo el {formatDayLabel(selectedDay)}
            </BigChoice>
          </div>
        </Question>
        <Question title={what === "week" ? "¿A partir de qué lunes?" : "¿En qué día?"}>
          <input type="date" aria-label="Fecha destino" min={today} value={target} onChange={(e) => setTarget(e.target.value)} className={field} />
        </Question>
        {!fixedAdvisorId && advisors.length > 1 ? (
          <Question title="¿De quién?">
            <select aria-label="Asesor" value={advisorId} onChange={(e) => setAdvisorId(e.target.value)} className={field}>
              <option value="">De todos</option>
              {advisors.map((a) => (
                <option key={a.userId} value={a.userId}>
                  {a.name}
                </option>
              ))}
            </select>
          </Question>
        ) : null}
        <ErrorBox message={error} />
      </div>
    </Modal>
  );
}
