"use client";

import { Avatar, LoadError, errorText, useApi } from "@/components/crm/CrmUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { Select } from "@/components/ui/Select";
import { StateMessage } from "@/components/ui/StateMessage";
import { Toggle } from "@/components/ui/Toggle";
import { useToast } from "@/components/ui/Toast";
import {
  getAssignmentSettings,
  listAdvisors,
  listOnline21Users,
  syncAdvisorsWithOnline21,
  updateAdvisor,
  updateAssignmentSettings,
  type AdvisorRouting,
  type AdvisorRoutingInput,
  type AssignmentSettings,
  type Online21SyncResult,
  type Online21User,
  type OwnerOffShiftPolicy,
  type RoundMode,
} from "@/lib/api/crm";
import { BUSINESS_TIMEZONE_LABEL, advisorStatusLabels, advisorStatusTones, formatDateTime, formatLocal, hhmm } from "@/lib/crm/labels";
import { roleLabels } from "@/lib/labels";
import { useCan } from "@/lib/permissions";
import { cn } from "@/lib/utils";
import { AlertTriangle, CalendarDays, Home, Link2, Pencil, RefreshCw, Shuffle } from "lucide-react";
import Link from "next/link";
import { useState } from "react";

export default function AsesoresPage() {
  const can = useCan();
  const advisors = useApi(() => listAdvisors(), []);
  const settings = useApi(() => getAssignmentSettings(), []);
  const [editing, setEditing] = useState<AdvisorRouting | null>(null);
  const { showToast } = useToast();
  const canManage = can("ADVISOR_ROUTING_MANAGE");
  const [syncing, setSyncing] = useState(false);
  const [syncResult, setSyncResult] = useState<Online21SyncResult | null>(null);
  const [online21Users, setOnline21Users] = useState<Online21User[] | null>(null);

  const syncOnline21 = async () => {
    setSyncing(true);
    try {
      const result = await syncAdvisorsWithOnline21();
      setSyncResult(result);
      setOnline21Users(result.users);
      if (!result.configured) {
        showToast("La integración con 21Online no está configurada.");
      } else {
        showToast(result.linked.length ? `${result.linked.length} asesor(es) vinculados con 21Online` : "Asesores al día con 21Online");
        advisors.reload();
      }
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setSyncing(false);
    }
  };

  const openEditor = (a: AdvisorRouting) => {
    setEditing(a);
    if (!online21Users) {
      listOnline21Users()
        .then(setOnline21Users)
        .catch(() => setOnline21Users([]));
    }
  };

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
        <div className="flex flex-wrap gap-2">
          {canManage ? (
            <Button variant="secondary" onClick={syncOnline21} disabled={syncing} title="Vincula por correo a los asesores con su usuario de 21Online">
              <RefreshCw className={cn("h-4 w-4", syncing && "animate-spin")} aria-hidden /> {syncing ? "Sincronizando…" : "Sincronizar con 21Online"}
            </Button>
          ) : null}
          <Link
            href="/calendario"
            className="inline-flex min-h-10 items-center gap-2 rounded-xl border border-border bg-card px-4 py-2.5 text-sm font-medium text-obsessed hover:bg-app-bg focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-obsessed/15"
          >
            <CalendarDays className="h-4 w-4" aria-hidden /> Calendario
          </Link>
        </div>
      }
    >
      <div className="space-y-5">
        {syncResult?.configured ? <SyncResultCard result={syncResult} onClose={() => setSyncResult(null)} /> : null}
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
                      <Avatar name={a.name} photoUrl={a.online21?.photoUrl} />
                      <div className="min-w-0">
                        <p className="truncate font-semibold text-obsessed">{a.name}</p>
                        <p className="flex flex-wrap items-center gap-x-2 text-xs text-muted">
                          <span>{roleLabels[a.roleCode] ?? a.roleCode}</span>
                          {a.online21UserId ? (
                            a.online21?.profileUrl ? (
                              <a href={a.online21.profileUrl} target="_blank" rel="noreferrer" className="hover:text-obsessed">
                                21Online #{a.online21UserId}
                              </a>
                            ) : (
                              <span>21Online #{a.online21UserId}</span>
                            )
                          ) : (
                            <span className="text-warning-text">Sin usuario de 21Online</span>
                          )}
                        </p>
                        {a.online21 ? (
                          <p className="truncate text-xs text-muted" title={`Datos de 21Online, actualizados ${formatDateTime(a.online21.syncedAt)}`}>
                            {[
                              a.online21.name && a.online21.name !== a.name ? a.online21.name : null,
                              a.online21.position,
                              a.online21.mobilePhone ?? a.online21.officePhone,
                              a.online21.email && a.online21.email.toLowerCase() !== a.email.toLowerCase() ? a.online21.email : null,
                            ]
                              .filter(Boolean)
                              .join(" · ")}
                          </p>
                        ) : null}
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
                        <Button variant="ghost" size="sm" onClick={() => openEditor(a)} aria-label={`Configurar a ${a.name}`}>
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
          online21Users={online21Users}
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

function SyncResultCard({ result, onClose }: { result: Online21SyncResult; onClose: () => void }) {
  return (
    <Card>
      <CardHeader
        title="Sincronización con 21Online"
        description="Se vincula por correo a quien aún no tiene usuario de 21Online; los vínculos puestos a mano no se cambian. También corre sola cada 6 horas."
        action={
          <Button variant="ghost" size="sm" onClick={onClose}>
            Cerrar
          </Button>
        }
      />
      <div className="grid grid-cols-1 gap-4 text-sm md:grid-cols-3">
        <SyncList title="Vinculados ahora" tone="text-success-text" items={result.linked} empty="Nadie nuevo." />
        <SyncList title="Sin usuario de 21Online" tone="text-warning-text" items={result.unmatched} empty="Todos los asesores están vinculados." />
        <SyncList title="Vínculos rotos" tone="text-danger-text" items={result.brokenLinks} empty="Ninguno." hint="El usuario ya no aparece en 21Online: elige otro en su configuración." />
      </div>
    </Card>
  );
}

function SyncList({ title, tone, items, empty, hint }: { title: string; tone: string; items: string[]; empty: string; hint?: string }) {
  return (
    <div>
      <p className={cn("font-semibold", tone)}>
        {title} ({items.length})
      </p>
      {items.length ? (
        <ul className="mt-1 space-y-0.5 text-obsessed">
          {items.map((i) => (
            <li key={i}>{i}</li>
          ))}
        </ul>
      ) : (
        <p className="mt-1 text-muted">{empty}</p>
      )}
      {items.length && hint ? <p className="mt-1 text-xs text-muted">{hint}</p> : null}
    </div>
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

function EditRoutingModal({
  advisor,
  online21Users,
  onClose,
  onSave,
}: {
  advisor: AdvisorRouting;
  /** null = cargando; [] = 21Online no respondió o no está configurado (se captura el número a mano). */
  online21Users: Online21User[] | null;
  onClose: () => void;
  onSave: (patch: Partial<AdvisorRoutingInput>) => Promise<void>;
}) {
  const [weight, setWeight] = useState(String(advisor.weight));
  const [dailyLimit, setDailyLimit] = useState(advisor.dailyLimit ? String(advisor.dailyLimit) : "");
  const [online21UserId, setOnline21UserId] = useState(advisor.online21UserId ? String(advisor.online21UserId) : "");
  const [whatsappPhone, setWhatsappPhone] = useState(advisor.whatsappPhone ?? "");
  const [saving, setSaving] = useState(false);
  const weightValue = Number(weight);
  const valid = Number.isInteger(weightValue) && weightValue >= 0 && weightValue <= 100;
  // WhatsApp o celular registrado en 21Online (solo sugerencia: nunca se cambia solo).
  const selected = online21Users?.find((u) => String(u.id) === online21UserId);
  const suggestedPhone = toE164(selected?.whatsapp ?? selected?.mobilePhone ?? null);

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
        {online21Users && online21Users.length > 0 ? (
          <div>
          <Select
            label="Usuario en 21Online"
            value={online21UserId}
            onChange={(e) => setOnline21UserId(e.target.value)}
            options={[
              { value: "", label: "Sin vincular" },
              ...online21Users.map((u) => ({
                value: String(u.id),
                label: `${u.name}${u.email ? ` · ${u.email}` : ""}${u.linkedUserId && u.linkedUserId !== advisor.userId ? ` (vinculado con ${u.linkedUserName})` : ""}`,
              })),
              ...(online21UserId && !online21Users.some((u) => String(u.id) === online21UserId)
                ? [{ value: online21UserId, label: `#${online21UserId} (ya no aparece en 21Online)` }]
                : []),
            ]}
          />
          <p className="mt-1 text-xs text-muted">Así se le priorizan los leads de sus propiedades y puede editarlas desde Genera.</p>
          </div>
        ) : (
          <Input
            label="Usuario en 21Online"
            inputMode="numeric"
            value={online21UserId}
            onChange={(e) => setOnline21UserId(e.target.value.replace(/\D/g, ""))}
            hint={online21Users === null ? "Consultando usuarios de 21Online…" : "Id numérico del asesor en 21Online: así se le priorizan los leads de sus propiedades."}
          />
        )}
        <div>
          <Input label="WhatsApp para avisos" type="tel" value={whatsappPhone} onChange={(e) => setWhatsappPhone(e.target.value)} placeholder="+52 777 123 4567" hint="Recibe aquí el aviso de cada lead asignado." />
          {suggestedPhone && suggestedPhone !== whatsappPhone ? (
            <button type="button" onClick={() => setWhatsappPhone(suggestedPhone)} className="mt-1 inline-flex items-center gap-1 text-xs font-medium text-dark-gold hover:text-obsessed">
              <Link2 className="h-3 w-3" aria-hidden /> Usar el de 21Online: {suggestedPhone}
            </button>
          ) : null}
        </div>
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

/** 10 dígitos de México → +52…; si ya trae lada internacional se respeta. */
function toE164(phone: string | null): string | null {
  if (!phone) return null;
  const digits = phone.replace(/\D/g, "");
  if (digits.length === 10) return `+52${digits}`;
  if (digits.length === 12 && digits.startsWith("52")) return `+${digits}`;
  return phone.trim().startsWith("+") ? `+${digits}` : null;
}
