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
import {
  getAssignmentSettings,
  listAdvisors,
  updateAdvisor,
  updateAssignmentSettings,
  type AdvisorRouting,
  type AdvisorRoutingInput,
  type AssignmentSettings,
  type OwnerOffShiftPolicy,
  type RoundMode,
} from "@/lib/api/crm";
import { BUSINESS_TIMEZONE_LABEL, advisorStatusLabels, advisorStatusTones, formatLocal, hhmm } from "@/lib/crm/labels";
import { roleLabels } from "@/lib/labels";
import { useCan } from "@/lib/permissions";
import { cn } from "@/lib/utils";
import { AlertTriangle, CalendarDays, Home, Pencil, Shuffle } from "lucide-react";
import Link from "next/link";
import { useState } from "react";

export default function AsesoresPage() {
  const can = useCan();
  const advisors = useApi(() => listAdvisors(), []);
  const settings = useApi(() => getAssignmentSettings(), []);
  const [editing, setEditing] = useState<AdvisorRouting | null>(null);
  const { showToast } = useToast();
  const canManage = can("ADVISOR_ROUTING_MANAGE");

  const save = async (row: AdvisorRouting, patch: Partial<AdvisorRoutingInput>) => {
    const input: AdvisorRoutingInput = {
      receivesLeads: row.receivesLeads,
      weight: row.weight,
      dailyLimit: row.dailyLimit,
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
  const equalMode = settings.data?.roundMode !== "WEIGHTED";

  return (
    <PageContainer
      title="Asesores"
      subtitle="Quién recibe leads, su estado según el calendario, su cupo y su participación en la ronda."
      action={
        <Link
          href="/calendario"
          className="inline-flex min-h-10 items-center gap-2 rounded-xl border border-border bg-card px-4 py-2.5 text-sm font-medium text-obsessed hover:bg-app-bg focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-obsessed/15"
        >
          <CalendarDays className="h-4 w-4" aria-hidden /> Calendario
        </Link>
      }
    >
      <div className="space-y-5">
        {settings.data ? <SettingsCard settings={settings.data} canManage={canManage} onSaved={(s) => settings.setData(s)} /> : null}

        {advisors.error && !advisors.data ? (
          <LoadError message={advisors.error} onRetry={advisors.reload} />
        ) : !advisors.data ? (
          <StateMessage kind="loading" title="Cargando asesores…" />
        ) : advisors.data.length === 0 ? (
          <StateMessage
            kind="empty"
            title="No hay asesores"
            description="Da de alta a los asesores en Usuarios y permisos; después configura aquí quién recibe leads y en el Calendario sus turnos."
            action={
              <Link href="/usuarios" className="text-sm font-medium text-dark-gold hover:text-obsessed">
                Ir a usuarios
              </Link>
            }
          />
        ) : (
          <div className="overflow-hidden rounded-xl border border-border bg-card shadow-[0_1px_2px_rgba(36,39,35,0.04)]">
            <div className="hidden grid-cols-[minmax(0,1.8fr)_minmax(0,1.5fr)_minmax(0,0.9fr)_minmax(0,1fr)_minmax(0,1.1fr)_auto_auto] gap-4 border-b border-border bg-app-bg px-5 py-3 text-xs font-medium uppercase tracking-wide text-muted xl:grid">
              <span>Asesor</span>
              <span>Estado</span>
              <span>Hoy</span>
              <span>Ronda</span>
              <span>Leads</span>
              <span>Recibe leads</span>
              <span className="sr-only">Acciones</span>
            </div>
            <ul className="divide-y divide-border">
              {advisors.data.map((a) => {
                const share = a.receivesLeads && activeWeight ? Math.round((a.weight / activeWeight) * 100) : 0;
                return (
                  <li
                    key={a.userId}
                    className={cn(
                      "grid grid-cols-1 gap-3 px-4 py-4 sm:grid-cols-2 sm:px-5 xl:grid-cols-[minmax(0,1.8fr)_minmax(0,1.5fr)_minmax(0,0.9fr)_minmax(0,1fr)_minmax(0,1.1fr)_auto_auto] xl:items-center xl:gap-4",
                      !a.receivesLeads && "text-muted",
                    )}
                  >
                    <div className="flex min-w-0 items-center gap-3 sm:col-span-2 xl:col-span-1">
                      <Avatar name={a.name} />
                      <div className="min-w-0">
                        <p className="truncate font-semibold text-obsessed">{a.name}</p>
                        <p className="flex flex-wrap items-center gap-x-2 text-xs text-muted">
                          <span>{roleLabels[a.roleCode] ?? a.roleCode}</span>
                          {a.online21UserId ? <span>21Online #{a.online21UserId}</span> : <span className="text-warning-text">Sin usuario de 21Online</span>}
                        </p>
                      </div>
                    </div>
                    <div className="min-w-0 space-y-1">
                      <div className="flex flex-wrap gap-1.5">
                        <Badge tone={advisorStatusTones[a.status]}>{advisorStatusLabels[a.status]}</Badge>
                        {a.currentShiftKind === "GUARD" ? <Badge tone="info">Guardia</Badge> : null}
                        {a.needsCalendar ? <Badge tone="danger">Completar calendario</Badge> : null}
                      </div>
                      <p className="text-xs text-muted">
                        {a.currentShiftEnd ? `En turno hasta ${hhmm(a.currentShiftEnd)}` : a.nextShift ? `Próximo turno: ${formatLocal(a.nextShift)}` : a.statusDetail}
                        {a.needsCalendar && a.legacyShift ? ` · horario anterior ${a.legacyShift} (sin días definidos)` : ""}
                      </p>
                    </div>
                    <div className="text-sm text-obsessed">
                      <span className="xl:sr-only">Hoy: </span>
                      {a.dailyLimit ? `${a.assignedToday} de ${a.dailyLimit}` : `${a.assignedToday} · sin límite`}
                    </div>
                    <div className="text-sm">
                      {equalMode ? (
                        <span className="text-muted">{a.receivesLeads ? "Participa (equitativa)" : "No participa"}</span>
                      ) : (
                        <>
                          <span className="font-semibold tabular-nums text-obsessed">Peso {a.weight}</span>
                          <span className="ml-2 text-xs text-muted">{a.receivesLeads ? `≈${share}%` : "—"}</span>
                        </>
                      )}
                    </div>
                    <div className="flex flex-wrap gap-1.5">
                      <Link href={`/leads?asesor=${a.userId}`} className="inline-flex">
                        <Badge tone="info">{a.openLeads} en seguimiento</Badge>
                      </Link>
                      {a.wonLeads ? <Badge tone="success">{a.wonLeads} ganados</Badge> : null}
                    </div>
                    <div className="xl:w-32">
                      <Toggle
                        id={`receives-${a.userId}`}
                        checked={a.receivesLeads}
                        onChange={(receivesLeads) => (canManage ? void save(a, { receivesLeads }) : undefined)}
                        label={a.receivesLeads ? "Recibe" : "En pausa"}
                      />
                    </div>
                    <div className="flex gap-1">
                      <Link
                        href={`/calendario?asesor=${a.userId}`}
                        className="inline-flex h-9 w-9 items-center justify-center rounded-xl text-obsessed hover:bg-app-bg focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30"
                        aria-label={`Calendario de ${a.name}`}
                        title="Ver su calendario"
                      >
                        <CalendarDays className="h-4 w-4" aria-hidden />
                      </Link>
                      {canManage ? (
                        <Button variant="ghost" size="sm" onClick={() => setEditing(a)} aria-label={`Configurar a ${a.name}`}>
                          <Pencil className="h-4 w-4" aria-hidden />
                        </Button>
                      ) : null}
                    </div>
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

function SettingsCard({ settings, canManage, onSaved }: { settings: AssignmentSettings; canManage: boolean; onSaved: (s: AssignmentSettings) => void }) {
  const { showToast } = useToast();
  const [saving, setSaving] = useState(false);
  const save = async (roundMode: RoundMode, policy: OwnerOffShiftPolicy) => {
    setSaving(true);
    try {
      onSaved(await updateAssignmentSettings(roundMode, policy));
      showToast("Política de asignación guardada");
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setSaving(false);
    }
  };
  const option = (checked: boolean, onClick: () => void, title: string, text: string) => (
    <label className={cn("flex cursor-pointer items-start gap-2 rounded-xl border p-3 text-sm", checked ? "border-gold bg-gold/10" : "border-border", !canManage && "cursor-default opacity-80")}>
      <input type="radio" checked={checked} onChange={onClick} disabled={!canManage || saving} className="mt-1 accent-[var(--color-c21-gold)]" />
      <span>
        <strong className="block font-semibold text-obsessed">{title}</strong>
        <span className="text-muted">{text}</span>
      </span>
    </label>
  );
  return (
    <Card>
      <CardHeader title="¿Cómo se asigna un lead?" description={BUSINESS_TIMEZONE_LABEL} />
      {settings.noUpcomingCoverage ? (
        <p className="mb-4 flex items-start gap-2 rounded-xl border border-danger-text/30 bg-danger-bg px-4 py-3 text-sm text-danger-text">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
          No hay ningún asesor disponible ahora ni turnos próximos en el calendario: los leads nuevos quedarán pendientes sin fecha. Configura turnos.
        </p>
      ) : settings.pendingLeads > 0 ? (
        <p className="mb-4 flex items-start gap-2 rounded-xl border border-warning-text/25 bg-warning-bg px-4 py-3 text-sm text-warning-text">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
          {settings.pendingLeads} lead{settings.pendingLeads === 1 ? "" : "s"} en espera de turno
          {settings.nextCoverage ? `; el próximo turno disponible empieza ${formatLocal(settings.nextCoverage)}.` : "."}
          <Link href="/leads?grupo=PENDING" className="ml-1 font-medium underline">
            Ver
          </Link>
        </p>
      ) : null}
      <ol className="mb-4 grid grid-cols-1 gap-3 text-sm md:grid-cols-2">
        <li className="flex gap-3">
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gold/20 text-dark-gold">
            <Home className="h-4 w-4" aria-hidden />
          </span>
          <span className="text-muted">
            <strong className="block font-semibold text-obsessed">Lead de una propiedad con asesor vinculado</strong>
            Tiene prioridad el asesor de la propiedad en 21Online. Un cliente que ya tiene asesor lo conserva.
          </span>
        </li>
        <li className="flex gap-3">
          <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gold/20 text-dark-gold">
            <Shuffle className="h-4 w-4" aria-hidden />
          </span>
          <span className="text-muted">
            <strong className="block font-semibold text-obsessed">Sin propiedad o sin asesor vinculado</strong>
            Ronda entre quienes están en un turno habilitado, sin ausencia, sin pausa y bajo su límite. Sin nadie disponible: “Pendiente por horario”.
          </span>
        </li>
      </ol>
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <fieldset className="space-y-2">
          <legend className="mb-1 text-sm font-medium text-obsessed">Ronda</legend>
          {option(settings.roundMode === "EQUAL", () => void save("EQUAL", settings.ownerOffShiftPolicy), "Equitativa", "Le toca a quien recibió hace más tiempo.")}
          {option(settings.roundMode === "WEIGHTED", () => void save("WEIGHTED", settings.ownerOffShiftPolicy), "Ponderada", "Reparte en proporción al peso de cada asesor.")}
        </fieldset>
        <fieldset className="space-y-2">
          <legend className="mb-1 text-sm font-medium text-obsessed">Si el asesor de la propiedad está fuera de horario</legend>
          {option(
            settings.ownerOffShiftPolicy === "RESPECT_OWNER",
            () => void save(settings.roundMode, "RESPECT_OWNER"),
            "Respetar al asesor de la propiedad (predeterminado)",
            "Queda reservado para él y pendiente de su próximo turno.",
          )}
          {option(
            settings.ownerOffShiftPolicy === "ROUTE_TO_GUARD",
            () => void save(settings.roundMode, "ROUTE_TO_GUARD"),
            "Canalizar a un asesor de guardia",
            "Lo atiende alguien disponible (prefiere guardias); se conserva la referencia del asesor de la propiedad.",
          )}
        </fieldset>
      </div>
      {settings.updatedByName ? <p className="mt-3 text-xs text-muted">Último cambio por {settings.updatedByName}.</p> : null}
    </Card>
  );
}

function EditRoutingModal({ advisor, onClose, onSave }: { advisor: AdvisorRouting; onClose: () => void; onSave: (patch: Partial<AdvisorRoutingInput>) => Promise<void> }) {
  const [weight, setWeight] = useState(String(advisor.weight));
  const [dailyLimit, setDailyLimit] = useState(advisor.dailyLimit ? String(advisor.dailyLimit) : "");
  const [online21UserId, setOnline21UserId] = useState(advisor.online21UserId ? String(advisor.online21UserId) : "");
  const [whatsappPhone, setWhatsappPhone] = useState(advisor.whatsappPhone ?? "");
  const [saving, setSaving] = useState(false);
  const weightValue = Number(weight);
  const valid = Number.isInteger(weightValue) && weightValue >= 0 && weightValue <= 100;

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
        <Input label="Peso en la ronda ponderada (0–100)" type="number" min={0} max={100} value={weight} onChange={(e) => setWeight(e.target.value)} hint="Con peso 10 recibe el doble que con peso 5." />
        <Input label="Límite diario" type="number" min={1} value={dailyLimit} onChange={(e) => setDailyLimit(e.target.value)} hint="Por día de Ciudad de México. Vacío = sin límite." />
        <Input
          label="Usuario en 21Online"
          inputMode="numeric"
          value={online21UserId}
          onChange={(e) => setOnline21UserId(e.target.value.replace(/\D/g, ""))}
          hint="Id numérico del asesor en 21Online: así se le priorizan los leads de sus propiedades."
        />
        <Input label="WhatsApp para avisos" type="tel" value={whatsappPhone} onChange={(e) => setWhatsappPhone(e.target.value)} placeholder="+52 777 123 4567" hint="Recibe aquí el aviso de cada lead asignado." />
        <p className="text-sm text-muted sm:col-span-2">
          Los horarios se configuran en el{" "}
          <Link href={`/calendario?asesor=${advisor.userId}`} className="font-medium text-dark-gold hover:text-obsessed">
            calendario
          </Link>
          .
        </p>
        {!valid ? <p className="text-sm text-danger-text sm:col-span-2">El peso debe ser un número entero de 0 a 100.</p> : null}
      </div>
    </Modal>
  );
}
