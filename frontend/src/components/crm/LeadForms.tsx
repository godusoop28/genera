"use client";

import { errorText } from "@/components/crm/CrmUi";
import { usePropertyCatalog } from "@/components/property/PropertyTypeSelect";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { Select } from "@/components/ui/Select";
import {
  completeLeadTask,
  createLeadTask,
  listLeadSources,
  rescheduleLeadTask,
  updateLead,
  type AdvisorRouting,
  type LeadDetail,
  type LeadInterest,
  type LeadItem,
  type LeadProfileInput,
  type LeadSourceOption,
  type LeadTask,
  type LeadTemperature,
  type TaskResult,
  type TaskType,
} from "@/lib/api/crm";
import {
  interestLabels,
  isoToMxLocal,
  mxLocalToIso,
  reminderOptions,
  sourceCategoryLabels,
  sourceCategoryOrder,
  taskResultLabels,
  taskTypeEmoji,
  taskTypeLabels,
  temperatureLabels,
} from "@/lib/crm/labels";
import { useCan } from "@/lib/permissions";
import { useEffect, useState, type FormEvent } from "react";

// --- Catálogo de fuentes (CC#29) ---------------------------------------------

let sourcesRequest: Promise<LeadSourceOption[]> | null = null;

/** Catálogo de fuentes, pedido una vez por carga de página. */
export function useLeadSources(): LeadSourceOption[] | null {
  const [sources, setSources] = useState<LeadSourceOption[] | null>(null);
  useEffect(() => {
    let alive = true;
    const request = sourcesRequest ?? (sourcesRequest = listLeadSources());
    request
      .then((s) => alive && setSources(s))
      .catch(() => {
        sourcesRequest = null;
      });
    return () => {
      alive = false;
    };
  }, []);
  return sources;
}

/** Tras editar el catálogo en Configuración, los selectores vuelven a pedirlo. */
export function invalidateLeadSources() {
  sourcesRequest = null;
}

/** Grupos para el selector: solo activas y no del sistema (más la actual, aunque ya no esté activa). */
export function sourceGroups(sources: LeadSourceOption[], keepCode?: string | null, includeSystem = false) {
  return sourceCategoryOrder
    .map((category) => ({
      label: sourceCategoryLabels[category],
      options: sources
        .filter((s) => s.category === category && ((s.active && (includeSystem || !s.system)) || s.code === keepCode))
        .map((s) => ({ value: s.code, label: s.active ? s.label : `${s.label} (inactiva)` })),
    }))
    .filter((g) => g.options.length > 0);
}

/** "¿De dónde viene el lead?": catálogo por categoría; "Otra — especificar" exige el detalle. */
export function SourcePicker({
  code,
  detail,
  onChange,
  keepCode,
  className,
}: {
  code: string;
  detail: string;
  onChange: (code: string, detail: string) => void;
  keepCode?: string | null;
  className?: string;
}) {
  const sources = useLeadSources();
  const selected = sources?.find((s) => s.code === code);
  return (
    <div className={className}>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Select
          label="¿De dónde viene el lead?"
          value={code}
          onChange={(e) => onChange(e.target.value, detail)}
          options={[{ value: "", label: sources ? "Elige la fuente" : "Cargando fuentes…" }]}
          groups={sources ? sourceGroups(sources, keepCode) : []}
        />
        <Input
          label={selected?.requiresDetail ? "Describe el origen (obligatorio)" : "Detalle (opcional)"}
          required={selected?.requiresDetail}
          placeholder={selected?.requiresDetail ? "¿De dónde llegó?" : "Ej. anuncio pagado, campaña de octubre"}
          value={detail}
          onChange={(e) => onChange(code, e.target.value)}
        />
      </div>
      {selected?.description ? <p className="mt-1.5 text-xs text-muted">{selected.description}</p> : null}
    </div>
  );
}

export function sourceIncomplete(sources: LeadSourceOption[] | null, code: string, detail: string): boolean {
  if (!code) return true;
  const selected = sources?.find((s) => s.code === code);
  return Boolean(selected?.requiresDetail && !detail.trim());
}

// --- Ficha: interés y estatus (CC#30) ----------------------------------------

export interface ProfileForm {
  firstContactOn: string;
  propertySubtypeId: string;
  zone: string;
  budgetMin: string;
  budgetMax: string;
  desiredFeatures: string;
  temperature: LeadTemperature | "";
  closeProbability: string;
  expectedCloseOn: string;
}

export const emptyProfile: ProfileForm = {
  firstContactOn: "",
  propertySubtypeId: "",
  zone: "",
  budgetMin: "",
  budgetMax: "",
  desiredFeatures: "",
  temperature: "",
  closeProbability: "",
  expectedCloseOn: "",
};

export function profileOf(lead: LeadItem): ProfileForm {
  return {
    firstContactOn: lead.firstContactOn ?? "",
    propertySubtypeId: lead.propertySubtypeId ?? "",
    zone: lead.zone ?? "",
    budgetMin: lead.budgetMin != null ? String(lead.budgetMin) : "",
    budgetMax: lead.budget != null ? String(lead.budget) : "",
    desiredFeatures: lead.desiredFeatures ?? "",
    temperature: lead.temperature ?? "",
    closeProbability: lead.closeProbability != null ? String(lead.closeProbability) : "",
    expectedCloseOn: lead.expectedCloseOn ?? "",
  };
}

const num = (s: string) => (s.trim() === "" ? undefined : Number(s.replace(/[^\d.]/g, "")));

export function profileInput(p: ProfileForm): LeadProfileInput {
  return {
    firstContactOn: p.firstContactOn || undefined,
    propertySubtypeId: p.propertySubtypeId || undefined,
    zone: p.zone.trim() || undefined,
    budgetMin: num(p.budgetMin),
    budgetMax: num(p.budgetMax),
    desiredFeatures: p.desiredFeatures.trim() || undefined,
    temperature: p.temperature || undefined,
    closeProbability: num(p.closeProbability),
    expectedCloseOn: p.expectedCloseOn || undefined,
  };
}

export function profileError(p: ProfileForm): string | null {
  const min = num(p.budgetMin);
  const max = num(p.budgetMax);
  if (min !== undefined && max !== undefined && min > max) return "El presupuesto mínimo no puede ser mayor que el máximo.";
  const prob = num(p.closeProbability);
  if (prob !== undefined && (prob < 0 || prob > 100)) return "La probabilidad de cierre va de 0 a 100 %.";
  return null;
}

export function ProfileFields({ value, onChange, keepSubtypeId }: { value: ProfileForm; onChange: (next: ProfileForm) => void; keepSubtypeId?: string | null }) {
  const { catalog } = usePropertyCatalog(true);
  const set = (key: keyof ProfileForm) => (v: string) => onChange({ ...value, [key]: v });
  const typeGroups = (catalog ?? [])
    .map((c) => ({
      label: c.name,
      options: c.subtypes.filter((s) => (s.active && c.active) || s.id === keepSubtypeId).map((s) => ({ value: s.id, label: s.active ? s.name : `${s.name} (inactivo)` })),
    }))
    .filter((g) => g.options.length > 0);
  return (
    <>
      <fieldset className="grid grid-cols-1 gap-4 sm:col-span-2 sm:grid-cols-2">
        <legend className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted">Lo que busca</legend>
        <Select label="Tipo de inmueble" value={value.propertySubtypeId} onChange={(e) => set("propertySubtypeId")(e.target.value)} options={[{ value: "", label: "Sin especificar" }]} groups={typeGroups} />
        <Input label="Zona o colonia de interés" value={value.zone} onChange={(e) => set("zone")(e.target.value)} placeholder="Ej. Cuernavaca centro, Jiutepec" />
        <Input label="Presupuesto mínimo" inputMode="numeric" value={value.budgetMin} onChange={(e) => set("budgetMin")(e.target.value)} suffix="MXN" />
        <Input label="Presupuesto máximo" inputMode="numeric" value={value.budgetMax} onChange={(e) => set("budgetMax")(e.target.value)} suffix="MXN" />
        <div className="flex flex-col gap-1.5 sm:col-span-2">
          <label htmlFor="lead-features" className="text-sm font-medium text-obsessed">
            Características deseadas
          </label>
          <textarea
            id="lead-features"
            rows={2}
            value={value.desiredFeatures}
            onChange={(e) => set("desiredFeatures")(e.target.value)}
            placeholder="Recámaras, baños, estacionamiento, jardín…"
            className="w-full resize-y rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20"
          />
        </div>
      </fieldset>
      <fieldset className="grid grid-cols-1 gap-4 sm:col-span-2 sm:grid-cols-2">
        <legend className="mb-2 text-xs font-semibold uppercase tracking-wide text-muted">Estatus</legend>
        <Select
          label="Temperatura"
          value={value.temperature}
          onChange={(e) => set("temperature")(e.target.value)}
          options={[{ value: "", label: "Sin definir" }, ...(Object.keys(temperatureLabels) as LeadTemperature[]).map((t) => ({ value: t, label: temperatureLabels[t] }))]}
        />
        <Input label="Probabilidad de cierre" inputMode="numeric" value={value.closeProbability} onChange={(e) => set("closeProbability")(e.target.value)} suffix="%" />
        <Input label="Fecha estimada de cierre" type="date" value={value.expectedCloseOn} onChange={(e) => set("expectedCloseOn")(e.target.value)} />
        <Input label="Fecha de primer contacto" type="date" value={value.firstContactOn} onChange={(e) => set("firstContactOn")(e.target.value)} hint="Si se deja vacía, es la fecha de registro." />
      </fieldset>
    </>
  );
}

// --- Editar la ficha ---------------------------------------------------------

type EditLeadProps = { open: boolean; onClose: () => void; lead: LeadItem; onSaved: (d: LeadDetail) => void };

/** Se monta al abrir: el formulario siempre arranca con los datos vigentes del lead. */
export function EditLeadModal(props: EditLeadProps) {
  return props.open ? <EditLeadModalBody {...props} /> : null;
}

function EditLeadModalBody({ open, onClose, lead, onSaved }: EditLeadProps) {
  const sources = useLeadSources();
  const [form, setForm] = useState(() => initialEdit(lead));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const set = (key: keyof typeof form) => (v: string) => setForm((f) => ({ ...f, [key]: v }));
  const invalid = !form.name.trim() || sourceIncomplete(sources, form.sourceCode, form.sourceDetail);

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const problem = profileError(form.profile);
    if (problem) {
      setError(problem);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      onSaved(
        await updateLead(lead.id, {
          name: form.name.trim(),
          phone: form.phone.trim() || undefined,
          email: form.email.trim() || undefined,
          interest: form.interest,
          sourceCode: form.sourceCode || undefined,
          sourceDetail: form.sourceDetail.trim() || undefined,
          summary: form.summary.trim() || undefined,
          propertyReference: form.propertyReference.trim(),
          profile: profileInput(form.profile),
        }),
      );
      onClose();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Editar ficha del lead"
      size="xl"
      footer={
        <>
          <Button variant="secondary" type="button" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" form="edit-lead-form" disabled={saving || invalid}>
            {saving ? "Guardando…" : "Guardar"}
          </Button>
        </>
      }
    >
      <form id="edit-lead-form" onSubmit={submit} className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Input label="Nombre completo" required value={form.name} onChange={(e) => set("name")(e.target.value)} containerClassName="sm:col-span-2" />
        <Input label="Teléfono" type="tel" value={form.phone} onChange={(e) => set("phone")(e.target.value)} />
        <Input label="Correo" type="email" value={form.email} onChange={(e) => set("email")(e.target.value)} />
        <Select
          label="Tipo de operación"
          value={form.interest}
          onChange={(e) => set("interest")(e.target.value)}
          options={(Object.keys(interestLabels) as LeadInterest[]).map((i) => ({ value: i, label: interestLabels[i] }))}
        />
        <Input label="Propiedad de 21Online" placeholder="Número o clave (vacío = ninguna)" value={form.propertyReference} onChange={(e) => set("propertyReference")(e.target.value)} />
        <SourcePicker
          code={form.sourceCode}
          detail={form.sourceDetail}
          keepCode={lead.sourceCode}
          onChange={(code, detail) => setForm((f) => ({ ...f, sourceCode: code, sourceDetail: detail }))}
          className="sm:col-span-2"
        />
        <ProfileFields value={form.profile} onChange={(profile) => setForm((f) => ({ ...f, profile }))} keepSubtypeId={lead.propertySubtypeId} />
        <div className="flex flex-col gap-1.5 sm:col-span-2">
          <label htmlFor="edit-lead-summary" className="text-sm font-medium text-obsessed">
            Lo que nos dijo
          </label>
          <textarea
            id="edit-lead-summary"
            rows={3}
            value={form.summary}
            onChange={(e) => set("summary")(e.target.value)}
            className="w-full resize-y rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20"
          />
        </div>
        <p className="text-xs text-muted sm:col-span-2">Cada cambio queda en el historial del lead.</p>
        {error ? (
          <p role="alert" className="rounded-xl bg-danger-bg px-4 py-3 text-sm text-danger-text sm:col-span-2">
            {error}
          </p>
        ) : null}
      </form>
    </Modal>
  );
}

function initialEdit(lead: LeadItem) {
  return {
    name: lead.name,
    phone: lead.phone ?? "",
    email: lead.email ?? "",
    interest: lead.interest,
    sourceCode: lead.sourceCode ?? "",
    sourceDetail: lead.sourceDetail ?? "",
    summary: lead.summary ?? "",
    propertyReference: lead.property ? (lead.property.online21Id != null ? String(lead.property.online21Id) : (lead.property.key ?? "")) : "",
    profile: profileOf(lead),
  };
}

// --- Actividades (CC#30) -----------------------------------------------------

const resultOptions = (Object.keys(taskResultLabels) as TaskResult[]).map((r) => ({ value: r, label: taskResultLabels[r] }));
const typeOptions = (Object.keys(taskTypeLabels) as TaskType[]).map((t) => ({ value: t, label: `${taskTypeEmoji[t]} ${taskTypeLabels[t]}` }));

function textArea(id: string, label: string, value: string, onChange: (v: string) => void, placeholder?: string) {
  return (
    <div className="flex flex-col gap-1.5 sm:col-span-2">
      <label htmlFor={id} className="text-sm font-medium text-obsessed">
        {label}
      </label>
      <textarea
        id={id}
        rows={2}
        value={value}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
        className="w-full resize-y rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20"
      />
    </div>
  );
}

/**
 * Programar una actividad, registrar una ya realizada o reprogramar una
 * existente (task). leads: si no viene leadId, se elige el lead (alta desde la agenda).
 */
type TaskModalProps = {
  open: boolean;
  onClose: () => void;
  leadId?: string;
  leads?: { id: string; name: string }[];
  task?: LeadTask | null;
  advisors?: AdvisorRouting[];
  defaultStart?: string;
  defaultProperty?: { key: string | null; title: string | null } | null;
  onSaved: (t: LeadTask) => void;
};

export function TaskModal(props: TaskModalProps) {
  return props.open ? <TaskModalBody {...props} /> : null;
}

function TaskModalBody({ open, onClose, leadId, leads, task, advisors, defaultStart, defaultProperty, onSaved }: TaskModalProps) {
  const can = useCan();
  const [form, setForm] = useState(() => initialTask(task, defaultStart, defaultProperty));
  const [chosenLead, setChosenLead] = useState(leadId ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const set = (key: keyof typeof form) => (v: string | boolean) => setForm((f) => ({ ...f, [key]: v }));
  const editing = Boolean(task);
  const done = !editing && form.done;
  const invalid = !chosenLead || !form.when || (form.type === "OTHER" && !form.title.trim()) || (done && !form.result && form.type !== "NOTE");

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const input = {
        type: form.type,
        title: form.title.trim() || undefined,
        scheduledAt: mxLocalToIso(form.when),
        durationMinutes: form.duration ? Number(form.duration) : undefined,
        advisorId: form.advisorId || undefined,
        propertyKey: form.propertyKey.trim() || undefined,
        propertyTitle: form.propertyTitle.trim() || undefined,
        notesBefore: form.notesBefore.trim() || undefined,
        reminderMinutes: form.reminder ? Number(form.reminder) : undefined,
        done,
        result: done ? (form.result as TaskResult) || undefined : undefined,
        actualMinutes: done && form.actualMinutes ? Number(form.actualMinutes) : undefined,
        outcomeNotes: done ? form.outcomeNotes.trim() || undefined : undefined,
        nextAction: done ? form.nextAction.trim() || undefined : undefined,
        nextContactAt: done && form.nextContactAt ? mxLocalToIso(form.nextContactAt) : undefined,
      };
      onSaved(task ? await rescheduleLeadTask(task.id, input) : await createLeadTask(chosenLead, input));
      onClose();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title={editing ? "Reprogramar actividad" : "Nueva actividad"}
      size="lg"
      footer={
        <>
          <Button variant="secondary" type="button" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" form="task-form" disabled={saving || invalid}>
            {saving ? "Guardando…" : editing ? "Guardar" : done ? "Registrar" : "Programar"}
          </Button>
        </>
      }
    >
      <form id="task-form" onSubmit={submit} className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        {!leadId && leads ? (
          <Select
            label="Lead"
            value={chosenLead}
            onChange={(e) => setChosenLead(e.target.value)}
            options={[{ value: "", label: "Elige el prospecto" }, ...leads.map((l) => ({ value: l.id, label: l.name }))]}
            containerClassName="sm:col-span-2"
          />
        ) : null}
        {!editing ? (
          <div className="flex gap-2 sm:col-span-2" role="radiogroup" aria-label="Estado de la actividad">
            {[
              { v: false, label: "Programar (futura)" },
              { v: true, label: "Ya se realizó" },
            ].map((o) => (
              <button
                key={String(o.v)}
                type="button"
                role="radio"
                aria-checked={form.done === o.v}
                onClick={() => set("done")(o.v)}
                className={`flex-1 rounded-xl border px-3 py-2 text-sm font-medium ${form.done === o.v ? "border-gold bg-gold/15 text-obsessed" : "border-border text-muted hover:text-obsessed"}`}
              >
                {o.label}
              </button>
            ))}
          </div>
        ) : null}
        <Select label="Tipo de actividad" value={form.type} onChange={(e) => set("type")(e.target.value)} options={typeOptions} disabled={editing} />
        <Input label={form.type === "OTHER" ? "Descripción (obligatoria)" : "Título (opcional)"} value={form.title} onChange={(e) => set("title")(e.target.value)} />
        <Input label={done ? "Fecha y hora en que se realizó" : "Fecha y hora"} type="datetime-local" required value={form.when} onChange={(e) => set("when")(e.target.value)} hint="Hora de Ciudad de México" />
        <Input label="Duración estimada (min)" inputMode="numeric" value={form.duration} onChange={(e) => set("duration")(e.target.value)} />
        {advisors && advisors.length && can("LEAD_ASSIGN") ? (
          <Select
            label="Asesor responsable"
            value={form.advisorId}
            onChange={(e) => set("advisorId")(e.target.value)}
            options={[{ value: "", label: "El asesor del lead" }, ...advisors.map((a) => ({ value: a.userId, label: a.name }))]}
          />
        ) : null}
        {!done ? <Select label="Recordatorio" value={form.reminder} onChange={(e) => set("reminder")(e.target.value)} options={reminderOptions} /> : null}
        <Input label="Inmueble relacionado (clave)" value={form.propertyKey} onChange={(e) => set("propertyKey")(e.target.value)} placeholder="Ej. GN 14" />
        <Input label="Inmueble (descripción)" value={form.propertyTitle} onChange={(e) => set("propertyTitle")(e.target.value)} placeholder="Ej. Casa en Vista Hermosa" />
        {textArea("task-notes", "Notas previas", form.notesBefore, set("notesBefore"), "Instrucciones o contexto")}
        {done ? (
          <>
            <Select label="Resultado" value={form.result} onChange={(e) => set("result")(e.target.value)} options={[{ value: "", label: "Elige el resultado" }, ...resultOptions]} />
            <Input label="Duración real (min)" inputMode="numeric" value={form.actualMinutes} onChange={(e) => set("actualMinutes")(e.target.value)} />
            {textArea("task-outcome", "Resumen / notas", form.outcomeNotes, set("outcomeNotes"), "Qué se habló o acordó")}
            <Input label="Siguiente acción" value={form.nextAction} onChange={(e) => set("nextAction")(e.target.value)} placeholder="Ej. enviar ficha técnica" />
            <Input label="Fecha de siguiente contacto" type="datetime-local" value={form.nextContactAt} onChange={(e) => set("nextContactAt")(e.target.value)} hint="Crea sola la próxima actividad" />
          </>
        ) : null}
        {error ? (
          <p role="alert" className="rounded-xl bg-danger-bg px-4 py-3 text-sm text-danger-text sm:col-span-2">
            {error}
          </p>
        ) : null}
      </form>
    </Modal>
  );
}

function initialTask(task: LeadTask | null | undefined, defaultStart?: string, property?: { key: string | null; title: string | null } | null) {
  return {
    done: false,
    type: (task?.type ?? "CALL") as TaskType,
    title: task?.title ?? "",
    when: task?.scheduledAt ? isoToMxLocal(task.scheduledAt) : (defaultStart ?? ""),
    duration: task?.durationMinutes != null ? String(task.durationMinutes) : "30",
    advisorId: task?.advisor?.id ?? "",
    reminder: task ? (task.reminderMinutes != null ? String(task.reminderMinutes) : "") : "60",
    propertyKey: task?.propertyKey ?? property?.key ?? "",
    propertyTitle: task?.propertyTitle ?? property?.title ?? "",
    notesBefore: task?.notesBefore ?? "",
    result: "",
    actualMinutes: "",
    outcomeNotes: "",
    nextAction: "",
    nextContactAt: "",
  };
}

/** Registrar el resultado de una actividad programada. */
export function CompleteTaskModal({ task, onClose, onSaved }: { task: LeadTask | null; onClose: () => void; onSaved: (t: LeadTask) => void }) {
  return task ? <CompleteTaskModalBody key={task.id} task={task} onClose={onClose} onSaved={onSaved} /> : null;
}

function CompleteTaskModalBody({ task, onClose, onSaved }: { task: LeadTask; onClose: () => void; onSaved: (t: LeadTask) => void }) {
  const [form, setForm] = useState({ result: "", actualMinutes: task.durationMinutes != null ? String(task.durationMinutes) : "", outcomeNotes: "", nextAction: "", nextContactAt: "", nextType: "" });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const set = (key: keyof typeof form) => (v: string) => setForm((f) => ({ ...f, [key]: v }));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!form.result) return;
    setSaving(true);
    setError(null);
    try {
      onSaved(
        await completeLeadTask(task.id, {
          result: form.result as TaskResult,
          actualMinutes: form.actualMinutes ? Number(form.actualMinutes) : undefined,
          outcomeNotes: form.outcomeNotes.trim() || undefined,
          nextAction: form.nextAction.trim() || undefined,
          nextContactAt: form.nextContactAt ? mxLocalToIso(form.nextContactAt) : undefined,
          nextType: (form.nextType as TaskType) || undefined,
        }),
      );
      onClose();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={`Resultado: ${taskTypeLabels[task.type]}${task.leadName ? ` con ${task.leadName}` : ""}`}
      size="lg"
      footer={
        <>
          <Button variant="secondary" type="button" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" form="complete-task-form" disabled={saving || !form.result}>
            {saving ? "Guardando…" : "Registrar resultado"}
          </Button>
        </>
      }
    >
      <form id="complete-task-form" onSubmit={submit} className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Select label="Resultado" value={form.result} onChange={(e) => set("result")(e.target.value)} options={[{ value: "", label: "Elige el resultado" }, ...resultOptions]} />
        <Input label="Duración real (min)" inputMode="numeric" value={form.actualMinutes} onChange={(e) => set("actualMinutes")(e.target.value)} />
        {textArea("complete-notes", "Resumen / notas", form.outcomeNotes, set("outcomeNotes"), "Qué se habló o acordó")}
        <Input label="Siguiente acción" value={form.nextAction} onChange={(e) => set("nextAction")(e.target.value)} placeholder="Ej. agendar segunda visita" />
        <Select label="Tipo de la siguiente actividad" value={form.nextType} onChange={(e) => set("nextType")(e.target.value)} options={[{ value: "", label: "Igual que esta" }, ...typeOptions]} />
        <Input
          label="Fecha de siguiente contacto"
          type="datetime-local"
          value={form.nextContactAt}
          onChange={(e) => set("nextContactAt")(e.target.value)}
          hint="Si la indicas, se programa sola la próxima actividad"
          containerClassName="sm:col-span-2"
        />
        {error ? (
          <p role="alert" className="rounded-xl bg-danger-bg px-4 py-3 text-sm text-danger-text sm:col-span-2">
            {error}
          </p>
        ) : null}
      </form>
    </Modal>
  );
}
