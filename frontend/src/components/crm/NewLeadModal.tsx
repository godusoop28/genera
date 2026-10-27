"use client";

import { errorText } from "@/components/crm/CrmUi";
import { ProfileFields, SourcePicker, emptyProfile, profileError, profileInput, sourceIncomplete, useLeadSources, type ProfileForm } from "@/components/crm/LeadForms";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { Select } from "@/components/ui/Select";
import { createLead, type AdvisorRouting, type LeadInterest } from "@/lib/api/crm";
import { interestLabels } from "@/lib/crm/labels";
import { useCan } from "@/lib/permissions";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

/** Alta manual de un lead (llamada, oficina, referido…). Los del bot llegan solos. */
export function NewLeadModal({ open, onClose, advisors }: { open: boolean; onClose: () => void; advisors: AdvisorRouting[] }) {
  const router = useRouter();
  const can = useCan();
  const canAssign = can("LEAD_ASSIGN");
  const sources = useLeadSources();
  const [form, setForm] = useState({
    name: "",
    phone: "",
    email: "",
    sourceCode: "",
    sourceDetail: "",
    interest: "BUY" as LeadInterest,
    propertyReference: "",
    summary: "",
    assign: "auto",
    exceptionReason: "",
  });
  const [profile, setProfile] = useState<ProfileForm>(emptyProfile);
  const [showProfile, setShowProfile] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const set = (key: keyof typeof form) => (value: string) => setForm((f) => ({ ...f, [key]: value }));
  const chosen = advisors.find((a) => a.userId === form.assign);
  const invalid = !form.name.trim() || sourceIncomplete(sources, form.sourceCode, form.sourceDetail) || (!!chosen && chosen.status !== "AVAILABLE" && !form.exceptionReason.trim());

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (invalid) return;
    const problem = profileError(profile);
    if (problem) {
      setError(problem);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const p = profileInput(profile);
      const created = await createLead({
        name: form.name.trim(),
        phone: form.phone.trim() || undefined,
        email: form.email.trim() || undefined,
        sourceCode: form.sourceCode,
        sourceDetail: form.sourceDetail.trim() || undefined,
        interest: form.interest,
        propertyReference: form.propertyReference.trim() || undefined,
        summary: form.summary.trim() || undefined,
        budget: p.budgetMax,
        advisorId: canAssign && form.assign !== "auto" && form.assign !== "none" ? form.assign : undefined,
        exceptionReason: form.exceptionReason.trim() || undefined,
        autoAssign: canAssign && form.assign === "auto",
        profile: p,
      });
      onClose();
      router.push(`/leads/${created.lead.id}`);
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
      title="Registrar lead"
      size="xl"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} type="button">
            Cancelar
          </Button>
          <Button type="submit" form="new-lead-form" disabled={saving || invalid}>
            {saving ? "Guardando…" : "Registrar"}
          </Button>
        </>
      }
    >
      <form id="new-lead-form" onSubmit={submit} className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Input label="Nombre completo" required value={form.name} onChange={(e) => set("name")(e.target.value)} containerClassName="sm:col-span-2" />
        <Input label="Teléfono" type="tel" value={form.phone} onChange={(e) => set("phone")(e.target.value)} />
        <Input label="Correo" type="email" value={form.email} onChange={(e) => set("email")(e.target.value)} />
        <SourcePicker code={form.sourceCode} detail={form.sourceDetail} onChange={(code, detail) => setForm((f) => ({ ...f, sourceCode: code, sourceDetail: detail }))} className="sm:col-span-2" />
        <Select
          label="Tipo de operación"
          value={form.interest}
          onChange={(e) => set("interest")(e.target.value)}
          options={(Object.keys(interestLabels) as LeadInterest[]).map((i) => ({ value: i, label: interestLabels[i] }))}
        />
        <Input
          label="Propiedad de 21Online (opcional)"
          placeholder="Número (634294) o clave (GN 14)"
          value={form.propertyReference}
          onChange={(e) => set("propertyReference")(e.target.value)}
        />
        <div className="flex flex-col gap-1.5 sm:col-span-2">
          <label htmlFor="new-lead-summary" className="text-sm font-medium text-obsessed">
            Lo que nos dijo
          </label>
          <textarea
            id="new-lead-summary"
            rows={3}
            value={form.summary}
            onChange={(e) => set("summary")(e.target.value)}
            className="w-full resize-y rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20"
          />
        </div>
        {showProfile ? (
          <ProfileFields value={profile} onChange={setProfile} />
        ) : (
          <button type="button" onClick={() => setShowProfile(true)} className="text-left text-sm font-medium text-dark-gold hover:text-obsessed sm:col-span-2">
            + Agregar lo que busca (tipo de inmueble, zona, presupuesto) y temperatura
          </button>
        )}
        {canAssign ? (
          <Select
            label="Asesor"
            value={form.assign}
            onChange={(e) => set("assign")(e.target.value)}
            containerClassName="sm:col-span-2"
            options={[
              { value: "auto", label: "Automático (asesor de la propiedad o ruleta)" },
              { value: "none", label: "Dejar sin asignar" },
              ...advisors.map((a) => ({ value: a.userId, label: `${a.name} · ${a.status === "AVAILABLE" ? "disponible" : a.statusDetail.toLowerCase()}` })),
            ]}
          />
        ) : (
          <p className="text-sm text-muted sm:col-span-2">Quedará asignado a ti.</p>
        )}
        {chosen && chosen.status !== "AVAILABLE" ? (
          <Input
            label={`${chosen.name} no está disponible (${chosen.statusDetail.toLowerCase()}): motivo de la excepción`}
            value={form.exceptionReason}
            onChange={(e) => set("exceptionReason")(e.target.value)}
            containerClassName="sm:col-span-2"
          />
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
