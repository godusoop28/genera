"use client";

import { LoadError, errorText, useApi } from "@/components/crm/CrmUi";
import { QrImage, channelLabels, channelShort, delayOptions, invalidateSurveyConfig } from "@/components/surveys/SurveyUi";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { Select } from "@/components/ui/Select";
import { StateMessage } from "@/components/ui/StateMessage";
import { Toggle } from "@/components/ui/Toggle";
import { useToast } from "@/components/ui/Toast";
import { getSurveyConfig, updateSurveySettings, updateTouchpointConfig, type SurveyChannel, type SurveySettings, type TouchpointConfig } from "@/lib/api/surveys";
import { Pencil, QrCode } from "lucide-react";
import { useState } from "react";

const editableChannels: SurveyChannel[] = ["WHATSAPP", "EMAIL", "SYSTEM", "ADVISOR_LINK"];

function delayText(minutes: number): string {
  const known = delayOptions.find((o) => Number(o.value) === minutes);
  if (known) return known.label;
  if (minutes % 1440 === 0) return `${minutes / 1440} días después`;
  if (minutes % 60 === 0) return `${minutes / 60} h después`;
  return `${minutes} min después`;
}

/** Control de Cambios #28, parte 4: cada punto de contacto se configura por separado; abajo las reglas generales. */
export function NpsConfigPanel() {
  const config = useApi(() => getSurveyConfig(), []);
  const [editing, setEditing] = useState<TouchpointConfig | null>(null);
  const [qr, setQr] = useState<TouchpointConfig | null>(null);

  if (config.error && !config.data) return <LoadError message={config.error} onRetry={config.reload} />;
  if (!config.data) return <StateMessage kind="loading" title="Cargando configuración…" />;

  const groups = [
    { title: "Vía sistema (automáticas)", items: config.data.touchpoints.filter((t) => t.systemTriggered) },
    { title: "Vía asesor (activación manual o presencial)", items: config.data.touchpoints.filter((t) => !t.systemTriggered) },
  ];

  return (
    <div className="space-y-5">
      {groups.map((g) => (
        <Card key={g.title}>
          <CardHeader title={g.title} />
          <ul className="divide-y divide-border">
            {g.items.map((t) => (
              <li key={t.touchpoint} className="flex flex-wrap items-center gap-x-3 gap-y-1.5 py-3 text-sm">
                <span className="min-w-0 flex-1">
                  <span className="block font-medium text-obsessed">
                    {t.number}. {t.label}
                  </span>
                  <span className="text-xs text-muted">
                    {channelShort[t.channel]} · {delayText(t.delayMinutes)}
                    {t.askComment ? " · con pregunta abierta" : " · solo calificación"}
                    {t.introMessage ? " · mensaje personalizado" : ""}
                  </span>
                </span>
                <Badge tone={t.active ? "success" : "neutral"}>{t.active ? "Activo" : "Inactivo"}</Badge>
                <Button size="sm" variant="ghost" onClick={() => setQr(t)} aria-label={`QR de ${t.label}`}>
                  <QrCode className="h-4 w-4" aria-hidden />
                </Button>
                <Button size="sm" variant="secondary" onClick={() => setEditing(t)}>
                  <Pencil className="h-4 w-4" aria-hidden /> Editar
                </Button>
              </li>
            ))}
          </ul>
        </Card>
      ))}

      <SettingsCard settings={config.data.settings} onSaved={config.reload} />

      {editing ? (
        <TouchpointModal
          touchpoint={editing}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            invalidateSurveyConfig();
            config.reload();
          }}
        />
      ) : null}
      <Modal open={qr !== null} onClose={() => setQr(null)} title={qr ? `QR fijo · ${qr.number}. ${qr.label}` : ""}>
        {qr ? (
          <div className="flex flex-col items-center gap-3 text-center">
            <QrImage value={qr.staticLink} size={240} downloadName={`qr-encuesta-${qr.number}-${qr.touchpoint.toLowerCase()}`} />
            <p className="break-all text-xs text-muted">{qr.staticLink}</p>
            <p className="text-sm text-muted">Para lonas, fichas técnicas o folletos: cualquiera que lo escanee responde la encuesta de este punto de contacto (sin expediente asociado).</p>
            {!qr.active ? <p className="text-sm text-warning-text">El punto de contacto está inactivo: el QR mostrará que la encuesta no está disponible.</p> : null}
          </div>
        ) : null}
      </Modal>
    </div>
  );
}

function TouchpointModal({ touchpoint, onClose, onSaved }: { touchpoint: TouchpointConfig; onClose: () => void; onSaved: () => void }) {
  const { showToast } = useToast();
  const [form, setForm] = useState({
    active: touchpoint.active,
    channel: touchpoint.channel === "QR" ? ("WHATSAPP" as SurveyChannel) : touchpoint.channel,
    delay: String(touchpoint.delayMinutes),
    askComment: touchpoint.askComment,
    intro: touchpoint.introMessage ?? "",
  });
  const [saving, setSaving] = useState(false);
  const delayChoices = delayOptions.some((o) => o.value === form.delay) ? delayOptions : [...delayOptions, { value: form.delay, label: delayText(Number(form.delay)) }];

  const save = async () => {
    setSaving(true);
    try {
      await updateTouchpointConfig(touchpoint.touchpoint, {
        active: form.active,
        channel: form.channel,
        delayMinutes: Number(form.delay),
        askComment: form.askComment,
        introMessage: form.intro.trim() || undefined,
      });
      showToast("Punto de contacto actualizado");
      onSaved();
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={`${touchpoint.number}. ${touchpoint.label}`}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancelar
          </Button>
          <Button onClick={() => void save()} disabled={saving}>
            {saving ? "Guardando…" : "Guardar"}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <Toggle id="tp-active" checked={form.active} onChange={(v) => setForm({ ...form, active: v })} label="Activo" description={touchpoint.systemTriggered ? "Se envía sola cuando ocurre el evento." : "El asesor la puede generar; inactiva no se ofrece por QR."} />
        <Select label="Canal" value={form.channel} onChange={(e) => setForm({ ...form, channel: e.target.value as SurveyChannel })} options={editableChannels.map((c) => ({ value: c, label: channelLabels[c] }))} />
        <Select label="Retraso de envío" value={form.delay} onChange={(e) => setForm({ ...form, delay: e.target.value })} options={delayChoices} />
        <Toggle id="tp-comment" checked={form.askComment} onChange={(v) => setForm({ ...form, askComment: v })} label="Pregunta abierta de seguimiento" description="Se adapta a la calificación (qué mejorar o qué le gustó)." />
        <div className="flex flex-col gap-1.5">
          <label htmlFor="tp-intro" className="text-sm font-medium text-obsessed">
            Mensaje de introducción (opcional)
          </label>
          <textarea
            id="tp-intro"
            rows={2}
            maxLength={500}
            value={form.intro}
            onChange={(e) => setForm({ ...form, intro: e.target.value })}
            placeholder="Ej. ¡Felicidades por tu nuevo hogar!"
            className="w-full resize-y rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20"
          />
        </div>
        {touchpoint.updatedByName ? <p className="text-xs text-muted">Última modificación: {touchpoint.updatedByName}</p> : null}
      </div>
    </Modal>
  );
}

function SettingsCard({ settings, onSaved }: { settings: SurveySettings; onSaved: () => void }) {
  const { showToast } = useToast();
  const [form, setForm] = useState({
    minDaysBetween: String(settings.minDaysBetween),
    maxPerPeriod: String(settings.maxPerPeriod),
    periodDays: String(settings.periodDays),
    reminderEnabled: settings.reminderEnabled,
    reminderAfterHours: String(settings.reminderAfterHours),
    maxReminders: String(settings.maxReminders),
    expireDays: String(settings.expireDays),
    advisorsSeeOwn: settings.advisorsSeeOwn,
  });
  const [saving, setSaving] = useState(false);
  const num = (s: string) => Number(s);
  const invalid = [form.minDaysBetween, form.maxPerPeriod, form.periodDays, form.reminderAfterHours, form.maxReminders, form.expireDays].some((v) => !/^\d+$/.test(v));

  return (
    <Card>
      <CardHeader title="Reglas generales" description="Aplican a las encuestas automáticas (las que genera el asesor siempre se crean, con aviso si el cliente recibió una hace poco)." />
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <Input label="Días mínimos entre encuestas" inputMode="numeric" value={form.minDaysBetween} onChange={(e) => setForm({ ...form, minDaysBetween: e.target.value })} />
        <Input label="Máximo de encuestas por cliente" inputMode="numeric" value={form.maxPerPeriod} onChange={(e) => setForm({ ...form, maxPerPeriod: e.target.value })} />
        <Input label="…en un periodo de (días)" inputMode="numeric" value={form.periodDays} onChange={(e) => setForm({ ...form, periodDays: e.target.value })} />
        <div className="sm:col-span-3">
          <Toggle id="nps-reminder" checked={form.reminderEnabled} onChange={(v) => setForm({ ...form, reminderEnabled: v })} label="Recordatorio si no responde" description="Solo por WhatsApp o correo." />
        </div>
        <Input label="Recordar después de (horas)" inputMode="numeric" value={form.reminderAfterHours} onChange={(e) => setForm({ ...form, reminderAfterHours: e.target.value })} />
        <Input label="Número de recordatorios" inputMode="numeric" value={form.maxReminders} onChange={(e) => setForm({ ...form, maxReminders: e.target.value })} hint="1 = una sola vez" />
        <Input label="La encuesta vence a los (días)" inputMode="numeric" value={form.expireDays} onChange={(e) => setForm({ ...form, expireDays: e.target.value })} />
        <div className="sm:col-span-3">
          <Toggle
            id="nps-advisors"
            checked={form.advisorsSeeOwn}
            onChange={(v) => setForm({ ...form, advisorsSeeOwn: v })}
            label="Cada asesor ve el NPS de sus clientes"
            description="Si se desactiva, las calificaciones y comentarios solo los ve dirección."
          />
        </div>
      </div>
      <Button
        className="mt-4"
        disabled={saving || invalid}
        onClick={async () => {
          setSaving(true);
          try {
            await updateSurveySettings({
              minDaysBetween: num(form.minDaysBetween),
              maxPerPeriod: num(form.maxPerPeriod),
              periodDays: num(form.periodDays),
              reminderEnabled: form.reminderEnabled,
              reminderAfterHours: num(form.reminderAfterHours),
              maxReminders: num(form.maxReminders),
              expireDays: num(form.expireDays),
              advisorsSeeOwn: form.advisorsSeeOwn,
            });
            showToast("Reglas guardadas");
            onSaved();
          } catch (err) {
            showToast(errorText(err));
          } finally {
            setSaving(false);
          }
        }}
      >
        Guardar reglas
      </Button>
    </Card>
  );
}
