"use client";

import { errorText } from "@/components/crm/CrmUi";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { Select } from "@/components/ui/Select";
import { Toggle } from "@/components/ui/Toggle";
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
  type ShiftRecurrence,
} from "@/lib/api/crm";
import { BUSINESS_TIMEZONE_LABEL, absenceKindLabels, dayShort, formatDayLabel, hhmm, isoDay } from "@/lib/crm/labels";
import { cn } from "@/lib/utils";
import { useState } from "react";

function ErrorBox({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <p role="alert" className="rounded-xl bg-danger-bg px-4 py-3 text-sm text-danger-text sm:col-span-2">
      {message}
    </p>
  );
}

function ScopeChoice({ value, onChange, date }: { value: EditScope; onChange: (v: EditScope) => void; date: string }) {
  return (
    <fieldset className="rounded-xl border border-border p-3 sm:col-span-2">
      <legend className="px-1 text-sm font-medium text-obsessed">Este turno se repite cada semana. ¿Qué quieres cambiar?</legend>
      {(
        [
          ["OCCURRENCE", `Solo el turno del ${formatDayLabel(date)}`],
          ["SERIES", `Toda la serie desde el ${formatDayLabel(date)} (los turnos pasados no cambian)`],
        ] as const
      ).map(([id, label]) => (
        <label key={id} className="mt-1 flex cursor-pointer items-start gap-2 text-sm text-obsessed">
          <input type="radio" name="scope" checked={value === id} onChange={() => onChange(id)} className="mt-1 accent-[var(--color-c21-gold)]" />
          {label}
        </label>
      ))}
    </fieldset>
  );
}

export interface ShiftDraft {
  advisorId: string;
  date: string;
  startTime?: string;
  endTime?: string;
}

/** Crear o editar un turno. Las horas son siempre de Ciudad de México. */
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
  const [advisorId, setAdvisorId] = useState(occurrence?.advisorId ?? fixedAdvisorId ?? draft?.advisorId ?? advisors[0]?.userId ?? "");
  const [kind, setKind] = useState<ShiftKind>(occurrence?.kind ?? "REGULAR");
  const [recurrence, setRecurrence] = useState<ShiftRecurrence>(occurrence?.recurrence ?? "WEEKLY");
  const [days, setDays] = useState<number[]>(occurrence?.days.length ? occurrence.days : [isoDay(baseDate)]);
  const [startDate, setStartDate] = useState(isSeries ? baseDate : (occurrence?.date ?? baseDate));
  const [endDate, setEndDate] = useState(occurrence?.seriesEnd ?? "");
  const [startTime, setStartTime] = useState(occurrence ? hhmm(occurrence.start) : (draft?.startTime ?? "09:00"));
  const [endTime, setEndTime] = useState(occurrence ? hhmm(occurrence.end) : (draft?.endTime ?? "14:00"));
  const [assignable, setAssignable] = useState(occurrence?.assignable ?? true);
  const [notes, setNotes] = useState(occurrence?.notes ?? "");
  const [scope, setScope] = useState<EditScope>("OCCURRENCE");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const crossesMidnight = startTime && endTime && endTime <= startTime;
  const effectiveRecurrence: ShiftRecurrence = editing && isSeries && scope === "OCCURRENCE" ? "ONCE" : recurrence;
  const valid = !!advisorId && !!startDate && !!startTime && !!endTime && startTime !== endTime && (effectiveRecurrence === "ONCE" || days.length > 0) && (!endDate || endDate >= startDate);

  const save = async () => {
    setSaving(true);
    setError(null);
    const input = {
      advisorId,
      kind,
      recurrence: effectiveRecurrence,
      days: effectiveRecurrence === "WEEKLY" ? days : [],
      startDate,
      endDate: effectiveRecurrence === "WEEKLY" && endDate ? endDate : null,
      startTime,
      endTime,
      assignable,
      notes: notes.trim() || null,
    };
    try {
      if (editing && occurrence) {
        await updateShift(occurrence.shiftId, input, isSeries ? scope : "SERIES", occurrence.date);
        onSaved("Turno actualizado");
      } else {
        await createShift(input);
        onSaved("Turno creado");
      }
    } catch (err) {
      setError(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!occurrence) return;
    setSaving(true);
    setError(null);
    try {
      await deleteShift(occurrence.shiftId, isSeries ? scope : "SERIES", occurrence.date);
      onSaved("Turno eliminado");
    } catch (err) {
      setError(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  const isPast = occurrence ? occurrence.date < today && !isSeries : false;

  return (
    <Modal
      open
      onClose={onClose}
      title={editing ? "Editar turno" : "Nuevo turno"}
      size="lg"
      footer={
        <>
          {editing ? (
            <Button variant="danger" onClick={() => void remove()} disabled={saving || isPast} className="mr-auto">
              {isSeries && scope === "SERIES" ? "Eliminar la serie desde aquí" : "Eliminar turno"}
            </Button>
          ) : null}
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => void save()} disabled={!valid || saving || isPast}>
            {saving ? "Guardando…" : "Guardar"}
          </Button>
        </>
      }
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <p className="text-xs text-muted sm:col-span-2">{BUSINESS_TIMEZONE_LABEL}</p>
        {isPast ? <p className="rounded-xl bg-app-bg px-4 py-3 text-sm text-muted sm:col-span-2">Este turno ya pasó: se conserva como historial y no se modifica.</p> : null}
        {editing && isSeries ? <ScopeChoice value={scope} onChange={setScope} date={occurrence.date} /> : null}
        <Select
          label="Asesor"
          value={advisorId}
          onChange={(e) => setAdvisorId(e.target.value)}
          disabled={!!fixedAdvisorId}
          options={advisors.map((a) => ({ value: a.userId, label: a.name }))}
        />
        <Select
          label="Tipo de turno"
          value={kind}
          onChange={(e) => setKind(e.target.value as ShiftKind)}
          options={[
            { value: "REGULAR", label: "Jornada normal" },
            { value: "GUARD", label: "Guardia" },
          ]}
        />
        {!(editing && isSeries && scope === "OCCURRENCE") && !(editing && !isSeries) ? (
          <Select
            label="Repetición"
            value={recurrence}
            onChange={(e) => setRecurrence(e.target.value as ShiftRecurrence)}
            containerClassName="sm:col-span-2"
            options={[
              { value: "WEEKLY", label: "Cada semana" },
              { value: "ONCE", label: "Solo una fecha" },
            ]}
          />
        ) : null}
        {effectiveRecurrence === "WEEKLY" ? (
          <fieldset className="sm:col-span-2">
            <legend className="mb-1.5 text-sm font-medium text-obsessed">Días</legend>
            <div className="flex flex-wrap gap-2">
              {[1, 2, 3, 4, 5, 6, 7].map((d) => {
                const on = days.includes(d);
                return (
                  <button
                    key={d}
                    type="button"
                    aria-pressed={on}
                    onClick={() => setDays(on ? days.filter((x) => x !== d) : [...days, d].sort())}
                    className={cn(
                      "min-h-10 min-w-12 rounded-xl border px-3 text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30",
                      on ? "border-gold bg-gold text-brand-ink" : "border-border bg-card text-muted hover:border-gold/60",
                    )}
                  >
                    {dayShort[d]}
                  </button>
                );
              })}
            </div>
          </fieldset>
        ) : null}
        <Input label={effectiveRecurrence === "WEEKLY" ? "Desde" : "Fecha"} type="date" min={today} value={startDate} onChange={(e) => setStartDate(e.target.value)} />
        {effectiveRecurrence === "WEEKLY" ? (
          <Input label="Hasta (opcional)" type="date" min={startDate} value={endDate} onChange={(e) => setEndDate(e.target.value)} hint="Vacío = sin fecha final." />
        ) : (
          <div className="hidden sm:block" />
        )}
        <Input label="Hora de inicio" type="time" value={startTime} onChange={(e) => setStartTime(e.target.value)} />
        <Input
          label="Hora de fin"
          type="time"
          value={endTime}
          onChange={(e) => setEndTime(e.target.value)}
          hint={crossesMidnight ? "Termina al día siguiente (cruza la medianoche)." : undefined}
        />
        <div className="sm:col-span-2">
          <Toggle
            id="shift-assignable"
            checked={assignable}
            onChange={setAssignable}
            label="Participa en la asignación automática"
            description="Si lo apagas, el turno se ve en el calendario pero no recibe leads de la ronda."
          />
        </div>
        <Input label="Observaciones (opcional)" value={notes} onChange={(e) => setNotes(e.target.value)} containerClassName="sm:col-span-2" />
        {occurrence?.createdByName || occurrence?.updatedByName ? (
          <p className="text-xs text-muted sm:col-span-2">
            {occurrence.createdByName ? `Creado por ${occurrence.createdByName}` : ""}
            {occurrence.updatedByName ? ` · Modificado por ${occurrence.updatedByName}` : ""}
          </p>
        ) : null}
        <ErrorBox message={error} />
      </div>
    </Modal>
  );
}

/** Vacaciones, permiso, descanso o indisponibilidad: prevalece sobre los turnos. */
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
  const [advisorId, setAdvisorId] = useState(absence?.advisorId ?? fixedAdvisorId ?? advisors[0]?.userId ?? "");
  const [kind, setKind] = useState<AbsenceKind>(absence?.kind ?? "VACATION");
  const [start, setStart] = useState(absence?.start.slice(0, 16) ?? `${defaultDate}T00:00`);
  const [end, setEnd] = useState(absence?.end.slice(0, 16) ?? `${defaultDate}T23:59`);
  const [notes, setNotes] = useState(absence?.notes ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const valid = !!advisorId && !!start && !!end && end > start;

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
  const input = () => ({ advisorId, kind, start: `${start}:00`, end: `${end}:00`, notes: notes.trim() || null });

  return (
    <Modal
      open
      onClose={onClose}
      title={absence ? "Editar ausencia" : "Registrar ausencia"}
      size="lg"
      footer={
        <>
          {absence ? (
            <Button variant="danger" className="mr-auto" disabled={saving} onClick={() => void run(() => deleteAbsence(absence.id), "Ausencia eliminada")}>
              Eliminar
            </Button>
          ) : null}
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            disabled={!valid || saving}
            onClick={() => void run(() => (absence ? updateAbsence(absence.id, input()) : createAbsence(input())), absence ? "Ausencia actualizada" : "Ausencia registrada")}
          >
            {saving ? "Guardando…" : "Guardar"}
          </Button>
        </>
      }
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <p className="text-sm text-muted sm:col-span-2">Mientras dure, el asesor no participa en la ronda aunque tenga turno. {BUSINESS_TIMEZONE_LABEL}.</p>
        <Select label="Asesor" value={advisorId} disabled={!!fixedAdvisorId} onChange={(e) => setAdvisorId(e.target.value)} options={advisors.map((a) => ({ value: a.userId, label: a.name }))} />
        <Select
          label="Tipo"
          value={kind}
          onChange={(e) => setKind(e.target.value as AbsenceKind)}
          options={(Object.keys(absenceKindLabels) as AbsenceKind[]).map((k) => ({ value: k, label: absenceKindLabels[k] }))}
        />
        <Input label="Desde" type="datetime-local" value={start} onChange={(e) => setStart(e.target.value)} />
        <Input label="Hasta" type="datetime-local" value={end} min={start} onChange={(e) => setEnd(e.target.value)} />
        <Input label="Observaciones (opcional)" value={notes} onChange={(e) => setNotes(e.target.value)} containerClassName="sm:col-span-2" />
        <ErrorBox message={error} />
      </div>
    </Modal>
  );
}

/** Copiar los turnos de un día o de una semana a otra fecha. */
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
  const [target, setTarget] = useState("");
  const [advisorId, setAdvisorId] = useState(fixedAdvisorId ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [skipped, setSkipped] = useState<string[]>([]);
  const source = what === "day" ? selectedDay : weekFrom;

  return (
    <Modal
      open
      onClose={onClose}
      title="Copiar horarios"
      size="lg"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cerrar
          </Button>
          <Button
            disabled={!target || saving}
            onClick={async () => {
              setSaving(true);
              setError(null);
              try {
                const res = await copyShifts(source, what === "day" ? 1 : 7, target, advisorId || undefined);
                setSkipped(res.skippedDetails);
                if (res.skipped === 0) onSaved(`Se copiaron ${res.created} turno(s)`);
                else if (res.created > 0) setError(`Se copiaron ${res.created}; ${res.skipped} se omitieron porque se cruzan con turnos existentes.`);
                else setError("No se copió ningún turno: todos se cruzan con turnos existentes.");
              } catch (err) {
                setError(errorText(err));
              } finally {
                setSaving(false);
              }
            }}
          >
            {saving ? "Copiando…" : "Copiar"}
          </Button>
        </>
      }
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Select
          label="Qué copiar"
          value={what}
          onChange={(e) => setWhat(e.target.value as "day" | "week")}
          options={[
            { value: "week", label: `La semana del ${formatDayLabel(weekFrom)}` },
            { value: "day", label: `El día ${formatDayLabel(selectedDay)}` },
          ]}
        />
        <Input
          label={what === "week" ? "Al lunes de la semana" : "Al día"}
          type="date"
          min={today}
          value={target}
          onChange={(e) => setTarget(e.target.value)}
          hint="Se crean como turnos de una fecha; los que se crucen se omiten."
        />
        <Select
          label="Asesor"
          value={advisorId}
          disabled={!!fixedAdvisorId}
          onChange={(e) => setAdvisorId(e.target.value)}
          containerClassName="sm:col-span-2"
          options={[{ value: "", label: "Todos los asesores" }, ...advisors.map((a) => ({ value: a.userId, label: a.name }))]}
        />
        <ErrorBox message={error} />
        {skipped.length ? (
          <ul className="list-disc pl-5 text-xs text-muted sm:col-span-2">
            {skipped.slice(0, 8).map((s) => (
              <li key={s}>{s}</li>
            ))}
          </ul>
        ) : null}
      </div>
    </Modal>
  );
}
