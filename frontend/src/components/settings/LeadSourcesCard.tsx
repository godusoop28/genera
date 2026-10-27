"use client";

import { invalidateLeadSources } from "@/components/crm/LeadForms";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { Select } from "@/components/ui/Select";
import { Toggle } from "@/components/ui/Toggle";
import { useToast } from "@/components/ui/Toast";
import {
  createLeadSource,
  getAssignmentSettings,
  listLeadSources,
  updateFollowUpSettings,
  updateLeadSource,
  type AssignmentSettings,
  type LeadSource,
  type LeadSourceOption,
  type SourceCategory,
} from "@/lib/api/crm";
import { sourceCategoryLabels, sourceCategoryOrder, sourceLabels } from "@/lib/crm/labels";
import { errorText } from "@/lib/errors";
import { useCan } from "@/lib/permissions";
import { ChevronDown, ChevronRight, Pencil, Plus } from "lucide-react";
import { useCallback, useEffect, useState } from "react";

type SourceForm = { code: string | null; category: SourceCategory; label: string; description: string; channel: LeadSource; active: boolean; requiresDetail: boolean };

/**
 * Control de Cambios #29: catálogo "¿De dónde viene el lead?". El
 * administrador activa o desactiva fuentes y agrega nuevas (p. ej.
 * subcategorías de un portal). Desactivar no modifica los leads que ya la usan.
 */
export function LeadSourcesCard() {
  const can = useCan();
  const allowed = can("LEAD_SOURCE_MANAGE");
  const { showToast } = useToast();
  const [sources, setSources] = useState<LeadSourceOption[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [open, setOpen] = useState<Set<SourceCategory>>(() => new Set());
  const [form, setForm] = useState<SourceForm | null>(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const load = useCallback(() => {
    listLeadSources()
      .then((s) => {
        setSources(s);
        setError(null);
      })
      .catch((err) => setError(errorText(err)));
  }, []);

  useEffect(() => {
    if (allowed) load();
  }, [allowed, load]);

  if (!allowed) return null;

  const toggleActive = async (s: LeadSourceOption) => {
    try {
      await updateLeadSource(s.code, { label: s.label, description: s.description ?? undefined, active: !s.active, requiresDetail: s.requiresDetail });
      invalidateLeadSources();
      showToast(`${s.label}: ${s.active ? "desactivada" : "activada"}`);
      load();
    } catch (err) {
      showToast(errorText(err));
    }
  };

  const save = async () => {
    if (!form || !form.label.trim()) return;
    setSaving(true);
    setFormError(null);
    try {
      if (form.code) {
        await updateLeadSource(form.code, { label: form.label.trim(), description: form.description.trim() || undefined, active: form.active, requiresDetail: form.requiresDetail });
      } else {
        await createLeadSource({ category: form.category, label: form.label.trim(), description: form.description.trim() || undefined, channel: form.channel, requiresDetail: form.requiresDetail });
      }
      invalidateLeadSources();
      showToast(form.code ? "Fuente actualizada" : "Fuente agregada");
      setForm(null);
      load();
    } catch (err) {
      setFormError(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card>
      <CardHeader
        title="Fuentes de lead"
        description="Opciones de “¿De dónde viene el lead?”. Solo las activas aparecen al registrar; desactivar una no cambia los leads que ya la tienen."
        action={
          <Button size="sm" variant="secondary" onClick={() => setForm({ code: null, category: "PORTALS", label: "", description: "", channel: "PORTAL", active: true, requiresDetail: false })}>
            <Plus className="h-4 w-4" aria-hidden /> Agregar
          </Button>
        }
      />
      {error ? <p className="text-sm text-danger-text">{error}</p> : null}
      {!sources ? (
        <p className="text-sm text-muted">Cargando…</p>
      ) : (
        <ul className="divide-y divide-border">
          {sourceCategoryOrder.map((category) => {
            const items = sources.filter((s) => s.category === category);
            if (items.length === 0) return null;
            const expanded = open.has(category);
            const active = items.filter((s) => s.active).length;
            return (
              <li key={category} className="py-2">
                <button
                  type="button"
                  onClick={() =>
                    setOpen((prev) => {
                      const next = new Set(prev);
                      if (next.has(category)) next.delete(category);
                      else next.add(category);
                      return next;
                    })
                  }
                  aria-expanded={expanded}
                  className="flex w-full items-center gap-2 text-left text-sm font-medium text-obsessed"
                >
                  {expanded ? <ChevronDown className="h-4 w-4" aria-hidden /> : <ChevronRight className="h-4 w-4" aria-hidden />}
                  {sourceCategoryLabels[category]}
                  <span className="ml-auto text-xs font-normal text-muted">
                    {active} de {items.length} activas
                  </span>
                </button>
                {expanded ? (
                  <ul className="mt-2 space-y-1 pl-6">
                    {items.map((s) => (
                      <li key={s.code} className="flex items-center gap-2 text-sm">
                        <span className={s.active ? "min-w-0 flex-1 truncate text-obsessed" : "min-w-0 flex-1 truncate text-muted line-through"}>{s.label}</span>
                        {s.requiresDetail ? <Badge tone="info">Pide detalle</Badge> : null}
                        {s.system ? <Badge tone="neutral">Asistente</Badge> : null}
                        <button
                          type="button"
                          aria-label={`Editar ${s.label}`}
                          onClick={() => setForm({ code: s.code, category: s.category, label: s.label, description: s.description ?? "", channel: s.channel, active: s.active, requiresDetail: s.requiresDetail })}
                          className="rounded p-1 text-muted hover:text-obsessed"
                        >
                          <Pencil className="h-3.5 w-3.5" aria-hidden />
                        </button>
                        {!s.system ? (
                          <button type="button" role="switch" aria-checked={s.active} onClick={() => void toggleActive(s)} className="text-xs font-medium text-dark-gold hover:text-obsessed">
                            {s.active ? "Desactivar" : "Activar"}
                          </button>
                        ) : null}
                      </li>
                    ))}
                  </ul>
                ) : null}
              </li>
            );
          })}
        </ul>
      )}
      <Modal
        open={form !== null}
        onClose={() => setForm(null)}
        title={form?.code ? "Editar fuente" : "Agregar fuente"}
        footer={
          <>
            <Button variant="secondary" onClick={() => setForm(null)}>
              Cancelar
            </Button>
            <Button onClick={() => void save()} disabled={saving || !form?.label.trim()}>
              {saving ? "Guardando…" : "Guardar"}
            </Button>
          </>
        }
      >
        {form ? (
          <div className="space-y-4">
            {!form.code ? (
              <>
                <Select
                  label="Categoría"
                  value={form.category}
                  onChange={(e) => setForm({ ...form, category: e.target.value as SourceCategory })}
                  options={sourceCategoryOrder.map((c) => ({ value: c, label: sourceCategoryLabels[c] }))}
                />
                <Select
                  label="Canal (para los reportes generales)"
                  value={form.channel}
                  onChange={(e) => setForm({ ...form, channel: e.target.value as LeadSource })}
                  options={(Object.keys(sourceLabels) as LeadSource[]).filter((c) => c !== "WHATSAPP_BOT").map((c) => ({ value: c, label: sourceLabels[c] }))}
                />
              </>
            ) : null}
            <Input label="Nombre" value={form.label} onChange={(e) => setForm({ ...form, label: e.target.value })} placeholder="Ej. Inmuebles24 — anuncio pagado" />
            <Input label="Descripción (opcional)" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
            <Toggle id="source-detail" checked={form.requiresDetail} onChange={(v) => setForm({ ...form, requiresDetail: v })} label="Pedir que se describa el origen" description="Como “Otra — especificar”: el detalle es obligatorio." />
            {form.code ? <Toggle id="source-active" checked={form.active} onChange={(v) => setForm({ ...form, active: v })} label="Activa" description="Aparece al registrar un lead." /> : null}
            {formError ? <p className="text-sm text-danger-text">{formError}</p> : null}
          </div>
        ) : null}
      </Modal>
    </Card>
  );
}

/** Control de Cambios #30: cuándo un lead se considera "por enfriarse" y si los recordatorios también van por correo. */
export function FollowUpSettingsCard() {
  const can = useCan();
  const allowed = can("ADVISOR_ROUTING_MANAGE");
  const { showToast } = useToast();
  const [settings, setSettings] = useState<AssignmentSettings | null>(null);
  const [days, setDays] = useState("7");
  const [email, setEmail] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!allowed) return;
    getAssignmentSettings()
      .then((s) => {
        setSettings(s);
        setDays(String(s.staleDays));
        setEmail(s.reminderEmail);
      })
      .catch(() => undefined);
  }, [allowed]);

  if (!allowed || !settings) return null;
  const n = Number(days);
  const invalid = !Number.isInteger(n) || n < 1 || n > 90;

  return (
    <Card>
      <CardHeader title="Seguimiento de leads" description="Alertas de leads sin actividad y recordatorios de actividades a los asesores." />
      <div className="space-y-4">
        <Input
          label="Días sin actividad para alertar"
          inputMode="numeric"
          value={days}
          onChange={(e) => setDays(e.target.value)}
          error={invalid ? "Entre 1 y 90 días." : undefined}
          hint="Dirección recibe cada mañana la lista de leads abiertos sin contacto en ese lapso."
        />
        <Toggle id="reminder-email" checked={email} onChange={setEmail} label="Mandar recordatorios también por correo" description="Siempre llegan a la campana del sistema; el correo requiere el servidor SMTP configurado." />
        <Button
          disabled={saving || invalid || (n === settings.staleDays && email === settings.reminderEmail)}
          onClick={async () => {
            setSaving(true);
            try {
              setSettings(await updateFollowUpSettings(n, email));
              showToast("Configuración guardada");
            } catch (err) {
              showToast(errorText(err));
            } finally {
              setSaving(false);
            }
          }}
        >
          Guardar
        </Button>
      </div>
    </Card>
  );
}
