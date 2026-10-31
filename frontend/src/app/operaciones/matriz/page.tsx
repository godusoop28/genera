"use client";

import { LoadError, useApi } from "@/components/crm/CrmUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { formatMoment } from "@/components/operations/OperationUi";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { Select } from "@/components/ui/Select";
import { StateMessage } from "@/components/ui/StateMessage";
import { useToast } from "@/components/ui/Toast";
import { errorText } from "@/lib/errors";
import {
  addMatrixItem,
  approveMatrix,
  getMatrix,
  operationTypeLabels,
  originLabels,
  sectionLabels,
  updateMatrixItem,
  type MatrixInput,
  type MatrixItem,
  type RequirementSection,
} from "@/lib/api/operations";
import { useCan } from "@/lib/permissions";
import { ArrowLeft, Plus, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { useState } from "react";

const sideLabel = (s: string | null) => (s === "SELLER" ? "Vendedor / arrendador" : s === "BUYER" ? "Comprador / arrendatario" : "De la operación");

/**
 * CC#32: matriz documental de cierre. La configura y aprueba el área legal o responsable de
 * cumplimiento; mientras esté en borrador ningún expediente se puede declarar completo y validado.
 */
export default function MatrixPage() {
  const can = useCan();
  const canEdit = can("OPERATION_MATRIX_MANAGE");
  const { showToast } = useToast();
  const result = useApi(() => getMatrix(), []);
  const [editing, setEditing] = useState<MatrixItem | "new" | null>(null);
  const m = result.data;

  if (result.error && !m) return <PageContainer title="Matriz documental de cierre"><LoadError message={result.error} onRetry={result.reload} /></PageContainer>;
  if (!m) return <PageContainer title="Matriz documental de cierre"><StateMessage kind="loading" title="Cargando matriz…" /></PageContainer>;

  return (
    <PageContainer
      title="Matriz documental de cierre"
      subtitle={
        <Link href="/operaciones" className="inline-flex items-center gap-1 text-dark-gold hover:underline">
          <ArrowLeft className="h-4 w-4" aria-hidden /> Operaciones
        </Link>
      }
      action={
        canEdit ? (
          <>
            <Button variant="secondary" onClick={() => setEditing("new")}>
              <Plus className="h-4 w-4" aria-hidden /> Agregar requisito
            </Button>
            <Button
              variant={m.approved ? "secondary" : "primary"}
              onClick={async () => {
                try {
                  result.setData(await approveMatrix(!m.approved));
                  showToast(m.approved ? "Matriz regresada a borrador." : "Matriz aprobada.");
                } catch (err) {
                  showToast(errorText(err));
                }
              }}
            >
              <ShieldCheck className="h-4 w-4" aria-hidden /> {m.approved ? "Retirar aprobación" : "Aprobar matriz"}
            </Button>
          </>
        ) : null
      }
    >
      <Card className="mb-6">
        <div className="flex flex-wrap items-center gap-3 text-sm">
          <Badge tone={m.approved ? "success" : "warning"}>{m.approved ? `Aprobada · revisión ${m.revision}` : `Borrador · revisión ${m.revision}`}</Badge>
          {m.approved ? (
            <span className="text-muted">
              Aprobó {m.approvedByName} · {formatMoment(m.approvedAt)}
            </span>
          ) : (
            <span className="text-warning-text">
              Pendiente de aprobación del área legal o responsable de cumplimiento. Los renglones iniciales son un borrador de trabajo y no acreditan cumplimiento.
            </span>
          )}
        </div>
        <p className="mt-2 text-xs text-muted">
          Los requisitos marcados como obligatorios no admiten &quot;No aplica&quot;. Los cambios aplican a operaciones nuevas; las existentes los toman con &quot;Agregar requisitos nuevos de
          la matriz&quot;. Cualquier cambio deja la matriz en borrador hasta que se vuelva a aprobar.
        </p>
      </Card>

      {(["OPERATION", "PLD", "PROFECO"] as RequirementSection[]).map((section) => (
        <Card key={section} className="mb-6">
          <CardHeader title={sectionLabels[section]} />
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-sm">
              <thead className="text-left text-xs uppercase tracking-wide text-muted">
                <tr>
                  <th className="py-2 pr-3">Requisito</th>
                  <th className="py-2 pr-3">Parte</th>
                  <th className="py-2 pr-3">Aplica a</th>
                  <th className="py-2 pr-3">Vigencia</th>
                  <th className="py-2 pr-3">Obligatorio</th>
                  <th className="py-2" />
                </tr>
              </thead>
              <tbody>
                {m.items
                  .filter((i) => i.section === section)
                  .map((i) => (
                    <tr key={i.id} className={`border-t border-border ${i.active ? "" : "opacity-50"}`}>
                      <td className="py-2 pr-3">
                        <p className="font-medium">{i.label}</p>
                        {i.description ? <p className="text-xs text-muted">{i.description}</p> : null}
                      </td>
                      <td className="py-2 pr-3 text-xs">{sideLabel(i.partySide)}</td>
                      <td className="py-2 pr-3 text-xs">
                        {i.operationType ? operationTypeLabels[i.operationType] : "Compraventa y arrendamiento"}
                        <br />
                        {i.origin ? originLabels[i.origin] : "Propias y externas"}
                      </td>
                      <td className="py-2 pr-3 text-xs">{i.validityDays ? `${i.validityDays} días` : "—"}</td>
                      <td className="py-2 pr-3 text-xs">{i.mandatory ? "Sí" : "No"}</td>
                      <td className="py-2 text-right">
                        {canEdit ? (
                          <Button size="sm" variant="ghost" onClick={() => setEditing(i)}>
                            Editar
                          </Button>
                        ) : null}
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
          </div>
        </Card>
      ))}

      {editing ? (
        <ItemModal
          item={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSave={async (input) => {
            const next = editing === "new" ? await addMatrixItem(input) : await updateMatrixItem(editing.id, input);
            result.setData(next);
            setEditing(null);
            showToast("Matriz actualizada; queda en borrador hasta su aprobación.");
          }}
        />
      ) : null}
    </PageContainer>
  );
}

function ItemModal({ item, onClose, onSave }: { item: MatrixItem | null; onClose: () => void; onSave: (input: MatrixInput) => Promise<void> }) {
  const [form, setForm] = useState<MatrixInput>(
    item ?? { section: "OPERATION", partySide: null, operationType: null, origin: null, label: "", description: "", mandatory: false, validityDays: null, documentTypeHint: null, sortOrder: 500, active: true },
  );
  const [validity, setValidity] = useState(item?.validityDays ? String(item.validityDays) : "");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const set = <K extends keyof MatrixInput>(key: K, value: MatrixInput[K]) => setForm((f) => ({ ...f, [key]: value }));
  const validityError = validity && !(Number(validity) > 0 && Number.isInteger(Number(validity))) ? "Escribe un número entero de días." : undefined;

  return (
    <Modal
      open
      onClose={onClose}
      size="lg"
      title={item ? "Editar requisito" : "Agregar requisito"}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            disabled={saving || !form.label.trim() || Boolean(validityError)}
            onClick={async () => {
              setSaving(true);
              setError(null);
              try {
                await onSave({ ...form, validityDays: validity ? Number(validity) : null });
              } catch (err) {
                setError(errorText(err));
              } finally {
                setSaving(false);
              }
            }}
          >
            Guardar
          </Button>
        </>
      }
    >
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <Input label="Requisito" value={form.label} onChange={(e) => set("label", e.target.value)} containerClassName="sm:col-span-2" />
        <Input label="Descripción" value={form.description ?? ""} onChange={(e) => set("description", e.target.value)} containerClassName="sm:col-span-2" />
        <Select
          label="Sección"
          value={form.section}
          onChange={(e) => set("section", e.target.value as RequirementSection)}
          options={(["OPERATION", "PLD", "PROFECO"] as RequirementSection[]).map((s) => ({ value: s, label: sectionLabels[s] }))}
        />
        <Select
          label="Parte"
          value={form.partySide ?? ""}
          onChange={(e) => set("partySide", (e.target.value || null) as MatrixInput["partySide"])}
          options={[
            { value: "", label: "De la operación" },
            { value: "SELLER", label: "Vendedor / arrendador" },
            { value: "BUYER", label: "Comprador / arrendatario" },
          ]}
        />
        <Select
          label="Tipo de operación"
          value={form.operationType ?? ""}
          onChange={(e) => set("operationType", (e.target.value || null) as MatrixInput["operationType"])}
          options={[
            { value: "", label: "Compraventa y arrendamiento" },
            { value: "SALE", label: "Solo compraventa" },
            { value: "RENT", label: "Solo arrendamiento" },
          ]}
        />
        <Select
          label="Origen"
          value={form.origin ?? ""}
          onChange={(e) => set("origin", (e.target.value || null) as MatrixInput["origin"])}
          options={[
            { value: "", label: "Propias y externas" },
            { value: "OWN", label: "Solo cartera propia" },
            { value: "EXTERNAL", label: "Solo propiedades externas" },
          ]}
        />
        <Input label="Vigencia" inputMode="numeric" suffix="días" placeholder="Vacío = no vence" value={validity} onChange={(e) => setValidity(e.target.value.replace(/\D/g, ""))} error={validityError} />
        <Input label="Orden" inputMode="numeric" value={String(form.sortOrder)} onChange={(e) => set("sortOrder", Number(e.target.value.replace(/\D/g, "")) || 0)} />
        <label className="inline-flex items-center gap-2 text-sm">
          <input type="checkbox" checked={form.mandatory} onChange={(e) => set("mandatory", e.target.checked)} /> Obligación legal (no admite &quot;No aplica&quot;)
        </label>
        <label className="inline-flex items-center gap-2 text-sm">
          <input type="checkbox" checked={form.active} onChange={(e) => set("active", e.target.checked)} /> Activo
        </label>
        {error ? (
          <p role="alert" className="rounded-xl bg-danger-bg px-4 py-3 text-sm text-danger-text sm:col-span-2">
            {error}
          </p>
        ) : null}
      </div>
    </Modal>
  );
}
