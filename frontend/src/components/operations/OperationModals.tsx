"use client";

import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { MoneyInput } from "@/components/ui/MoneyInput";
import { Select } from "@/components/ui/Select";
import { errorText } from "@/lib/errors";
import { moneyToRaw } from "@/lib/money";
import {
  closeOperation,
  createOperation,
  partyLabel,
  representedLabel,
  updateOperation,
  type OperationDetail,
  type OperationType,
  type PartyInput,
  type RepresentedSide,
} from "@/lib/api/operations";
import { useState } from "react";

const emptyParty: PartyInput = { fullName: "", personType: "INDIVIDUAL", representativeName: "", representativeRole: "", email: "", phone: "", counterpartContact: "", notes: "" };

const textareaClass =
  "w-full resize-y rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20";

export function PartyFields({ title, value, onChange, counterpart }: { title: string; value: PartyInput; onChange: (p: PartyInput) => void; counterpart: boolean }) {
  const set = (key: keyof PartyInput) => (v: string) => onChange({ ...value, [key]: v });
  return (
    <fieldset className="grid grid-cols-1 gap-3 rounded-xl border border-border p-3 sm:grid-cols-2">
      <legend className="px-1 text-xs font-semibold uppercase tracking-wide text-muted">{title}</legend>
      <Input label="Nombre o razón social" value={value.fullName} onChange={(e) => set("fullName")(e.target.value)} />
      <Select
        label="Tipo de persona"
        value={value.personType ?? ""}
        onChange={(e) => set("personType")(e.target.value)}
        options={[
          { value: "INDIVIDUAL", label: "Persona física" },
          { value: "LEGAL_ENTITY", label: "Persona moral" },
        ]}
      />
      <Input label="Representante (si aplica)" value={value.representativeName ?? ""} onChange={(e) => set("representativeName")(e.target.value)} />
      <Input label="Carácter del representante" placeholder="Apoderado, representante legal…" value={value.representativeRole ?? ""} onChange={(e) => set("representativeRole")(e.target.value)} />
      <Input label="Correo" type="email" inputMode="email" value={value.email ?? ""} onChange={(e) => set("email")(e.target.value)} />
      <Input label="Teléfono" type="tel" inputMode="tel" value={value.phone ?? ""} onChange={(e) => set("phone")(e.target.value)} />
      {counterpart ? (
        <Input
          label="Asesor, oficina o tercero por el que se piden sus papeles"
          containerClassName="sm:col-span-2"
          value={value.counterpartContact ?? ""}
          onChange={(e) => set("counterpartContact")(e.target.value)}
        />
      ) : null}
    </fieldset>
  );
}

/** CC#32: "Registrar operación de propiedad externa": sin publicarla ni darla de alta como captación propia. */
export function ExternalOperationModal({ open, onClose, onCreated }: { open: boolean; onClose: () => void; onCreated: (d: OperationDetail) => void }) {
  const [type, setType] = useState<OperationType>("SALE");
  const [side, setSide] = useState<RepresentedSide>("BUYER");
  const [form, setForm] = useState({ propertyDescription: "", propertyAddress: "", externalSource: "", externalOffice: "", externalContact: "", generaParticipation: "" });
  const [seller, setSeller] = useState<PartyInput>(emptyParty);
  const [buyer, setBuyer] = useState<PartyInput>(emptyParty);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const set = (key: keyof typeof form) => (v: string) => setForm((f) => ({ ...f, [key]: v }));

  const submit = async () => {
    setSaving(true);
    setError(null);
    try {
      const created = await createOperation({
        origin: "EXTERNAL",
        operationType: type,
        representedSide: side,
        ...form,
        seller: seller.fullName.trim() ? seller : null,
        buyer: buyer.fullName.trim() ? buyer : null,
      });
      onCreated(created);
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
      size="xl"
      title="Registrar operación de propiedad externa"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={submit} disabled={saving || !form.propertyDescription.trim() || (!form.externalSource.trim() && !form.externalOffice.trim())}>
            {saving ? "Registrando…" : "Registrar e iniciar cierre"}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <p className="text-sm text-muted">
          Para inmuebles que no están en la cartera de C21 Genera pero en cuyo cierre participa. No se publica ni se da de alta como captación. Al registrarla se
          activa la solicitud de papeles de ambas partes según la matriz documental.
        </p>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Select
            label="Tipo de operación"
            value={type}
            onChange={(e) => setType(e.target.value as OperationType)}
            options={[
              { value: "SALE", label: "Compraventa" },
              { value: "RENT", label: "Arrendamiento" },
            ]}
          />
          <Select
            label="C21 Genera representa a"
            value={side}
            onChange={(e) => setSide(e.target.value as RepresentedSide)}
            options={(["SELLER", "BUYER", "BOTH"] as RepresentedSide[]).map((s) => ({ value: s, label: representedLabel(type, s) }))}
          />
          <Input label="Inmueble" placeholder="Ej. Casa en Lomas de Cuernavaca, clave del tercero" value={form.propertyDescription} onChange={(e) => set("propertyDescription")(e.target.value)} />
          <Input label="Domicilio del inmueble" value={form.propertyAddress} onChange={(e) => set("propertyAddress")(e.target.value)} />
          <Input label="Origen de la propiedad" placeholder="Bolsa inmobiliaria, referido, portal…" value={form.externalSource} onChange={(e) => set("externalSource")(e.target.value)} />
          <Input label="Oficina o tercero participante" value={form.externalOffice} onChange={(e) => set("externalOffice")(e.target.value)} />
          <Input label="Contacto del tercero" placeholder="Nombre, teléfono o correo" value={form.externalContact} onChange={(e) => set("externalContact")(e.target.value)} containerClassName="sm:col-span-2" />
          <div className="flex flex-col gap-1.5 sm:col-span-2">
            <label htmlFor="op-participation" className="text-sm font-medium text-obsessed">
              Participación de C21 Genera
            </label>
            <textarea id="op-participation" rows={2} className={textareaClass} value={form.generaParticipation} onChange={(e) => set("generaParticipation")(e.target.value)} placeholder="Alcance de la intervención y reparto acordado" />
          </div>
        </div>
        <PartyFields title={partyLabel(type, "SELLER")} value={seller} onChange={setSeller} counterpart={side === "BUYER"} />
        <PartyFields title={partyLabel(type, "BUYER")} value={buyer} onChange={setBuyer} counterpart={side === "SELLER"} />
        {error ? (
          <p role="alert" className="rounded-xl bg-danger-bg px-4 py-3 text-sm text-danger-text">
            {error}
          </p>
        ) : null}
      </div>
    </Modal>
  );
}

/** Editar datos de la operación y de las partes. */
export function EditOperationModal({ detail, open, onClose, onSaved }: { detail: OperationDetail; open: boolean; onClose: () => void; onSaved: (d: OperationDetail) => void }) {
  const op = detail.operation;
  const partyOf = (side: "SELLER" | "BUYER"): PartyInput => {
    const p = detail.parties.find((x) => x.side === side);
    return p ? { ...emptyParty, ...p } : emptyParty;
  };
  const [side, setSide] = useState<RepresentedSide>(op.representedSide);
  const [form, setForm] = useState({
    propertyDescription: op.propertyDescription,
    propertyAddress: detail.propertyAddress ?? "",
    externalSource: detail.externalSource ?? "",
    externalOffice: detail.externalOffice ?? "",
    externalContact: detail.externalContact ?? "",
    generaParticipation: detail.generaParticipation ?? "",
  });
  const [amount, setAmount] = useState(moneyToRaw(op.amount));
  const [currency, setCurrency] = useState(op.currency ?? "MXN");
  const [seller, setSeller] = useState<PartyInput>(partyOf("SELLER"));
  const [buyer, setBuyer] = useState<PartyInput>(partyOf("BUYER"));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const set = (key: keyof typeof form) => (v: string) => setForm((f) => ({ ...f, [key]: v }));

  const submit = async () => {
    setSaving(true);
    setError(null);
    try {
      onSaved(
        await updateOperation(op.id, {
          representedSide: side,
          ...form,
          amount: amount ? Number(amount) : null,
          currency: amount ? currency : null,
          seller: seller.fullName.trim() ? seller : null,
          buyer: buyer.fullName.trim() ? buyer : null,
        }),
      );
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
      size="xl"
      title={`Editar ${op.folio}`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={submit} disabled={saving}>
            {saving ? "Guardando…" : "Guardar"}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Select
            label="C21 Genera representa a"
            value={side}
            onChange={(e) => setSide(e.target.value as RepresentedSide)}
            options={(["SELLER", "BUYER", "BOTH"] as RepresentedSide[]).map((s) => ({ value: s, label: representedLabel(op.operationType, s) }))}
          />
          <Input label="Inmueble" value={form.propertyDescription} onChange={(e) => set("propertyDescription")(e.target.value)} />
          <Input label="Domicilio del inmueble" value={form.propertyAddress} onChange={(e) => set("propertyAddress")(e.target.value)} />
          <div className="grid grid-cols-[1fr_6rem] gap-2">
            <MoneyInput label="Monto de la operación" value={amount} onChange={setAmount} currency={currency} />
            <Select
              label="Moneda"
              value={currency}
              onChange={(e) => setCurrency(e.target.value)}
              options={[
                { value: "MXN", label: "MXN" },
                { value: "USD", label: "USD" },
              ]}
            />
          </div>
          {op.origin === "EXTERNAL" ? (
            <>
              <Input label="Origen de la propiedad" value={form.externalSource} onChange={(e) => set("externalSource")(e.target.value)} />
              <Input label="Oficina o tercero participante" value={form.externalOffice} onChange={(e) => set("externalOffice")(e.target.value)} />
              <Input label="Contacto del tercero" value={form.externalContact} onChange={(e) => set("externalContact")(e.target.value)} />
            </>
          ) : null}
          <Input label="Participación de C21 Genera" value={form.generaParticipation} onChange={(e) => set("generaParticipation")(e.target.value)} containerClassName="sm:col-span-2" />
        </div>
        <PartyFields title={partyLabel(op.operationType, "SELLER")} value={seller} onChange={setSeller} counterpart={side === "BUYER"} />
        <PartyFields title={partyLabel(op.operationType, "BUYER")} value={buyer} onChange={setBuyer} counterpart={side === "SELLER"} />
        {error ? (
          <p role="alert" className="rounded-xl bg-danger-bg px-4 py-3 text-sm text-danger-text">
            {error}
          </p>
        ) : null}
      </div>
    </Modal>
  );
}

/** Registrar el cierre comercial: fecha, monto y, si falta documentación, compromiso y responsable. */
export function CloseOperationModal({ detail, open, onClose, onSaved }: { detail: OperationDetail; open: boolean; onClose: () => void; onSaved: (d: OperationDetail) => void }) {
  const op = detail.operation;
  const today = new Date().toLocaleDateString("en-CA", { timeZone: "America/Mexico_City" });
  const [closedOn, setClosedOn] = useState(today);
  const [amount, setAmount] = useState(moneyToRaw(op.amount));
  const [currency, setCurrency] = useState(op.currency ?? "MXN");
  const [dueDate, setDueDate] = useState("");
  const [owner, setOwner] = useState(op.advisorName ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const pendingDocs = op.docStatus !== "COMPLETE_VALIDATED";

  const submit = async () => {
    setSaving(true);
    setError(null);
    try {
      onSaved(
        await closeOperation(op.id, {
          closedOn,
          amount: amount ? Number(amount) : null,
          currency,
          docDueDate: pendingDocs ? dueDate || null : null,
          docFollowUpOwner: pendingDocs ? owner || null : null,
        }),
      );
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
      size="lg"
      title="Registrar cierre de la operación"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={submit} disabled={saving || !amount || (pendingDocs && (!dueDate || !owner.trim()))}>
            {saving ? "Registrando…" : "Registrar cierre"}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-3">
        <p className="text-sm text-muted">
          El cierre comercial es independiente de la revisión documental. El responsable del cierre es el asesor de la operación ({op.advisorName ?? "sin asignar"}).
        </p>
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <Input label="Fecha de cierre" type="date" max={today} value={closedOn} onChange={(e) => setClosedOn(e.target.value)} />
          <div className="grid grid-cols-[1fr_6rem] gap-2">
            <MoneyInput label="Monto de la operación" value={amount} onChange={setAmount} currency={currency} />
            <Select
              label="Moneda"
              value={currency}
              onChange={(e) => setCurrency(e.target.value)}
              options={[
                { value: "MXN", label: "MXN" },
                { value: "USD", label: "USD" },
              ]}
            />
          </div>
        </div>
        {pendingDocs ? (
          <div className="rounded-xl border border-warning-text/30 bg-warning-bg p-3 text-sm text-warning-text">
            <p className="font-medium">La documentación no está completa y validada.</p>
            <p className="mt-1">
              La operación quedará como &quot;Cerrada — Documentación pendiente&quot;, sin presentarse como expediente completo. Indica quién subsana los faltantes y para cuándo.
            </p>
            <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-2">
              <Input label="Fecha compromiso" type="date" min={today} value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
              <Input label="Responsable" value={owner} onChange={(e) => setOwner(e.target.value)} />
            </div>
          </div>
        ) : null}
        {error ? (
          <p role="alert" className="rounded-xl bg-danger-bg px-4 py-3 text-sm text-danger-text">
            {error}
          </p>
        ) : null}
      </div>
    </Modal>
  );
}
