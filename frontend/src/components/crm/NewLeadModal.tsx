"use client";

import { errorText } from "@/components/crm/CrmUi";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { Select } from "@/components/ui/Select";
import { createLead, type AdvisorRouting, type LeadInterest, type LeadSource } from "@/lib/api/crm";
import { interestLabels, sourceLabels } from "@/lib/crm/labels";
import { useCan } from "@/lib/permissions";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";

/** Alta manual de un lead (llamada, oficina, referido). Los del bot llegan solos. */
export function NewLeadModal({ open, onClose, advisors }: { open: boolean; onClose: () => void; advisors: AdvisorRouting[] }) {
  const router = useRouter();
  const can = useCan();
  const canAssign = can("LEAD_ASSIGN");
  const [form, setForm] = useState({
    name: "",
    phone: "",
    email: "",
    source: "PHONE" as LeadSource,
    sourceDetail: "",
    interest: "BUY" as LeadInterest,
    propertyReference: "",
    summary: "",
    assign: "auto",
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const set = (key: keyof typeof form) => (value: string) => setForm((f) => ({ ...f, [key]: value }));

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    if (!form.name.trim()) return;
    setSaving(true);
    setError(null);
    try {
      const created = await createLead({
        name: form.name.trim(),
        phone: form.phone.trim() || undefined,
        email: form.email.trim() || undefined,
        source: form.source,
        sourceDetail: form.sourceDetail.trim() || undefined,
        interest: form.interest,
        propertyReference: form.propertyReference.trim() || undefined,
        summary: form.summary.trim() || undefined,
        advisorId: canAssign && form.assign !== "auto" && form.assign !== "none" ? form.assign : undefined,
        autoAssign: canAssign && form.assign === "auto",
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
      size="lg"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} type="button">
            Cancelar
          </Button>
          <Button type="submit" form="new-lead-form" disabled={saving || !form.name.trim()}>
            {saving ? "Guardando…" : "Registrar"}
          </Button>
        </>
      }
    >
      <form id="new-lead-form" onSubmit={submit} className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Input label="Nombre" required value={form.name} onChange={(e) => set("name")(e.target.value)} containerClassName="sm:col-span-2" />
        <Input label="Teléfono" type="tel" value={form.phone} onChange={(e) => set("phone")(e.target.value)} />
        <Input label="Correo" type="email" value={form.email} onChange={(e) => set("email")(e.target.value)} />
        <Select
          label="¿Cómo llegó?"
          value={form.source}
          onChange={(e) => set("source")(e.target.value)}
          options={(Object.keys(sourceLabels) as LeadSource[]).filter((s) => s !== "WHATSAPP_BOT").map((s) => ({ value: s, label: sourceLabels[s] }))}
        />
        <Input label="Detalle de la fuente" placeholder="Ej. Inmuebles24, campaña de Facebook" value={form.sourceDetail} onChange={(e) => set("sourceDetail")(e.target.value)} />
        <Select
          label="¿Qué busca?"
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
        {canAssign ? (
          <Select
            label="Asesor"
            value={form.assign}
            onChange={(e) => set("assign")(e.target.value)}
            containerClassName="sm:col-span-2"
            options={[
              { value: "auto", label: "Automático (asesor de la propiedad o ruleta)" },
              { value: "none", label: "Dejar sin asignar" },
              ...advisors.map((a) => ({ value: a.userId, label: a.name })),
            ]}
          />
        ) : (
          <p className="text-sm text-muted sm:col-span-2">Quedará asignado a ti.</p>
        )}
        {error ? (
          <p role="alert" className="rounded-xl bg-danger-bg px-4 py-3 text-sm text-danger-text sm:col-span-2">
            {error}
          </p>
        ) : null}
      </form>
    </Modal>
  );
}
