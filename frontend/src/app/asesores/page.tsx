"use client";

import { Avatar, LoadError, errorText, useApi } from "@/components/crm/CrmUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { StateMessage } from "@/components/ui/StateMessage";
import { Toggle } from "@/components/ui/Toggle";
import { useToast } from "@/components/ui/Toast";
import { listAdvisors, updateAdvisor, type AdvisorRouting, type AdvisorRoutingInput } from "@/lib/api/crm";
import { roleLabels } from "@/lib/labels";
import { cn } from "@/lib/utils";
import { Clock, Home, Pencil, Shuffle } from "lucide-react";
import Link from "next/link";
import { useState } from "react";

export default function AsesoresPage() {
  const advisors = useApi(() => listAdvisors(), []);
  const [editing, setEditing] = useState<AdvisorRouting | null>(null);
  const { showToast } = useToast();

  const save = async (row: AdvisorRouting, patch: Partial<AdvisorRoutingInput>) => {
    const input: AdvisorRoutingInput = {
      receivesLeads: row.receivesLeads,
      weight: row.weight,
      dailyLimit: row.dailyLimit,
      shiftStart: row.shiftStart,
      shiftEnd: row.shiftEnd,
      online21UserId: row.online21UserId,
      whatsappPhone: row.whatsappPhone,
      ...patch,
    };
    try {
      const updated = await updateAdvisor(row.userId, input);
      advisors.setData((list) => (list ?? []).map((a) => (a.userId === row.userId ? { ...a, ...updated, openLeads: a.openLeads, wonLeads: a.wonLeads } : a)));
      return true;
    } catch (err) {
      showToast(errorText(err));
      return false;
    }
  };

  const activeWeight = (advisors.data ?? []).filter((a) => a.receivesLeads && a.weight > 0).reduce((s, a) => s + a.weight, 0);

  return (
    <PageContainer title="Asesores" subtitle="Quién recibe leads, con qué peso, límite y horario.">
      <div className="space-y-5">
        <Card>
          <CardHeader title="¿Cómo se asigna un lead?" />
          <ol className="grid grid-cols-1 gap-4 text-sm md:grid-cols-2">
            <li className="flex gap-3">
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gold/20 text-dark-gold">
                <Home className="h-4 w-4" aria-hidden />
              </span>
              <span>
                <strong className="font-semibold text-obsessed">Si pregunta por una propiedad</strong>
                <span className="block text-muted">va directo al asesor que la tiene en 21Online (para eso captura su usuario de 21Online).</span>
              </span>
            </li>
            <li className="flex gap-3">
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gold/20 text-dark-gold">
                <Shuffle className="h-4 w-4" aria-hidden />
              </span>
              <span>
                <strong className="font-semibold text-obsessed">Si solo explora o la propiedad no tiene asesor en Genera</strong>
                <span className="block text-muted">entra a la ruleta: se reparte entre quienes reciben leads, en su horario y sin rebasar su límite diario, en proporción a su peso.</span>
              </span>
            </li>
          </ol>
        </Card>

        {advisors.error && !advisors.data ? (
          <LoadError message={advisors.error} onRetry={advisors.reload} />
        ) : !advisors.data ? (
          <StateMessage kind="loading" title="Cargando asesores…" />
        ) : advisors.data.length === 0 ? (
          <StateMessage
            kind="empty"
            title="No hay asesores"
            description="Da de alta a los asesores en Usuarios y permisos; después configura aquí quién recibe leads."
            action={
              <Link href="/usuarios" className="text-sm font-medium text-dark-gold hover:text-obsessed">
                Ir a usuarios
              </Link>
            }
          />
        ) : (
          <div className="overflow-hidden rounded-xl border border-border bg-card shadow-[0_1px_2px_rgba(36,39,35,0.04)]">
            <div className="hidden grid-cols-[minmax(0,2fr)_minmax(0,1.1fr)_minmax(0,1fr)_minmax(0,1.3fr)_auto_auto] gap-4 border-b border-border bg-app-bg px-5 py-3 text-xs font-medium uppercase tracking-wide text-muted lg:grid">
              <span>Asesor</span>
              <span>Peso en la ruleta</span>
              <span>Hoy</span>
              <span>Leads</span>
              <span>Recibe leads</span>
              <span className="sr-only">Editar</span>
            </div>
            <ul className="divide-y divide-border">
              {advisors.data.map((a) => {
                const share = a.receivesLeads && activeWeight ? Math.round((a.weight / activeWeight) * 100) : 0;
                return (
                  <li
                    key={a.userId}
                    className={cn(
                      "grid grid-cols-1 gap-3 px-4 py-4 sm:px-5 lg:grid-cols-[minmax(0,2fr)_minmax(0,1.1fr)_minmax(0,1fr)_minmax(0,1.3fr)_auto_auto] lg:items-center lg:gap-4",
                      !a.receivesLeads && "text-muted",
                    )}
                  >
                    <div className="flex min-w-0 items-center gap-3">
                      <Avatar name={a.name} />
                      <div className="min-w-0">
                        <p className="truncate font-semibold text-obsessed">{a.name}</p>
                        <p className="flex flex-wrap items-center gap-x-2 text-xs text-muted">
                          <span>{roleLabels[a.roleCode] ?? a.roleCode}</span>
                          <span className="inline-flex items-center gap-1">
                            <Clock className="h-3 w-3" aria-hidden />
                            {a.shiftStart && a.shiftEnd ? `${a.shiftStart.slice(0, 5)}–${a.shiftEnd.slice(0, 5)}` : "Todo el día"}
                          </span>
                          {a.online21UserId ? <span>21Online #{a.online21UserId}</span> : <span className="text-warning-text">Sin usuario de 21Online</span>}
                        </p>
                      </div>
                    </div>
                    <div className="text-sm">
                      <span className="font-semibold tabular-nums text-obsessed">{a.weight}</span>
                      <span className="ml-2 text-xs text-muted">{a.receivesLeads ? `≈${share}% de la ruleta` : "—"}</span>
                    </div>
                    <div className="text-sm text-obsessed">
                      <span className="lg:sr-only">Hoy: </span>
                      {a.dailyLimit ? `${a.assignedToday} de ${a.dailyLimit}` : `${a.assignedToday} · sin límite`}
                    </div>
                    <div className="flex flex-wrap gap-1.5">
                      <Link href={`/leads?asesor=${a.userId}`} className="inline-flex">
                        <Badge tone="info">{a.openLeads} en seguimiento</Badge>
                      </Link>
                      {a.wonLeads ? <Badge tone="success">{a.wonLeads} ganados</Badge> : null}
                    </div>
                    <div className="lg:w-32">
                      <Toggle
                        id={`receives-${a.userId}`}
                        checked={a.receivesLeads}
                        onChange={(receivesLeads) => void save(a, { receivesLeads })}
                        label={a.receivesLeads ? "Recibe" : "En pausa"}
                      />
                    </div>
                    <Button variant="ghost" size="sm" onClick={() => setEditing(a)} aria-label={`Configurar a ${a.name}`}>
                      <Pencil className="h-4 w-4" aria-hidden />
                    </Button>
                  </li>
                );
              })}
            </ul>
          </div>
        )}
      </div>

      {editing ? (
        <EditRoutingModal
          advisor={editing}
          onClose={() => setEditing(null)}
          onSave={async (patch) => {
            if (await save(editing, patch)) {
              setEditing(null);
              showToast("Configuración guardada");
            }
          }}
        />
      ) : null}
    </PageContainer>
  );
}

function EditRoutingModal({ advisor, onClose, onSave }: { advisor: AdvisorRouting; onClose: () => void; onSave: (patch: Partial<AdvisorRoutingInput>) => Promise<void> }) {
  const [weight, setWeight] = useState(String(advisor.weight));
  const [dailyLimit, setDailyLimit] = useState(advisor.dailyLimit ? String(advisor.dailyLimit) : "");
  const [shiftStart, setShiftStart] = useState(advisor.shiftStart?.slice(0, 5) ?? "");
  const [shiftEnd, setShiftEnd] = useState(advisor.shiftEnd?.slice(0, 5) ?? "");
  const [online21UserId, setOnline21UserId] = useState(advisor.online21UserId ? String(advisor.online21UserId) : "");
  const [whatsappPhone, setWhatsappPhone] = useState(advisor.whatsappPhone ?? "");
  const [saving, setSaving] = useState(false);
  const weightValue = Number(weight);
  const valid = Number.isInteger(weightValue) && weightValue >= 0 && weightValue <= 100 && (!shiftStart === !shiftEnd);

  return (
    <Modal
      open
      onClose={onClose}
      title={`Configurar a ${advisor.name}`}
      size="lg"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button
            disabled={!valid || saving}
            onClick={async () => {
              setSaving(true);
              await onSave({
                weight: weightValue,
                dailyLimit: dailyLimit ? Number(dailyLimit) : null,
                shiftStart: shiftStart || null,
                shiftEnd: shiftEnd || null,
                online21UserId: online21UserId ? Number(online21UserId) : null,
                whatsappPhone: whatsappPhone.trim() || null,
              });
              setSaving(false);
            }}
          >
            {saving ? "Guardando…" : "Guardar"}
          </Button>
        </>
      }
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Input label="Peso en la ruleta (0–100)" type="number" min={0} max={100} value={weight} onChange={(e) => setWeight(e.target.value)} hint="Con peso 10 recibe el doble que con peso 5." />
        <Input label="Límite diario" type="number" min={1} value={dailyLimit} onChange={(e) => setDailyLimit(e.target.value)} hint="Vacío = sin límite." />
        <Input label="Entra a la ruleta desde" type="time" value={shiftStart} onChange={(e) => setShiftStart(e.target.value)} />
        <Input label="Hasta" type="time" value={shiftEnd} onChange={(e) => setShiftEnd(e.target.value)} hint="Hora de Ciudad de México. Vacío = todo el día." />
        <Input
          label="Usuario en 21Online"
          inputMode="numeric"
          value={online21UserId}
          onChange={(e) => setOnline21UserId(e.target.value.replace(/\D/g, ""))}
          hint="Id numérico del asesor en 21Online: así le llegan los leads de sus propiedades."
        />
        <Input
          label="WhatsApp para avisos"
          type="tel"
          value={whatsappPhone}
          onChange={(e) => setWhatsappPhone(e.target.value)}
          placeholder="+52 777 123 4567"
          hint="Recibe aquí el aviso de cada lead nuevo."
        />
        {!valid ? <p className="text-sm text-danger-text sm:col-span-2">Revisa el peso (0–100) y que el horario tenga inicio y fin.</p> : null}
      </div>
    </Modal>
  );
}
