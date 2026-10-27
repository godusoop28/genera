"use client";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { useToast } from "@/components/ui/Toast";
import { approveReceiptTexts, getReceiptSettings, updateReceiptSettings, type ReceiptSettings } from "@/lib/api/receipts";
import { errorText } from "@/lib/errors";
import { useCan } from "@/lib/permissions";
import { useEffect, useState } from "react";

/**
 * Control de Cambios #31: entidad responsable, representante legal que firma
 * y textos del acuse. Cambiar un texto retira la aprobación legal hasta que se
 * vuelva a aprobar; sin aprobación, el acuse no se firma ni se envía.
 */
export function ReceiptSettingsCard() {
  const can = useCan();
  const allowed = can("RECEIPT_MANAGE");
  const { showToast } = useToast();
  const [settings, setSettings] = useState<ReceiptSettings | null>(null);
  const [form, setForm] = useState({ responsibleEntity: "", representativeName: "", representativeTitle: "", responsibilityText: "", privacyText: "" });
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!allowed) return;
    getReceiptSettings()
      .then((s) => {
        setSettings(s);
        setForm({
          responsibleEntity: s.responsibleEntity,
          representativeName: s.representativeName ?? "",
          representativeTitle: s.representativeTitle ?? "",
          responsibilityText: s.responsibilityText,
          privacyText: s.privacyText,
        });
      })
      .catch(() => undefined);
  }, [allowed]);

  if (!allowed || !settings) return null;
  const dirty =
    form.responsibleEntity !== settings.responsibleEntity ||
    form.representativeName !== (settings.representativeName ?? "") ||
    form.representativeTitle !== (settings.representativeTitle ?? "") ||
    form.responsibilityText !== settings.responsibilityText ||
    form.privacyText !== settings.privacyText;

  const area = (id: string, label: string, key: "responsibilityText" | "privacyText") => (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-obsessed">
        {label}
      </label>
      <textarea
        id={id}
        rows={5}
        value={form[key]}
        onChange={(e) => setForm({ ...form, [key]: e.target.value })}
        className="w-full resize-y rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm text-obsessed focus:border-gold focus:outline-none focus:ring-4 focus:ring-gold/20"
      />
    </div>
  );

  return (
    <Card>
      <CardHeader
        title="Acuse de recepción"
        description="Quién firma y la redacción aprobada por el área legal."
        action={<Badge tone={settings.approved ? "success" : "warning"}>{settings.approved ? "Textos aprobados" : "Pendiente de aprobación legal"}</Badge>}
      />
      <div className="space-y-4">
        <Input label="Entidad responsable (razón social)" value={form.responsibleEntity} onChange={(e) => setForm({ ...form, responsibleEntity: e.target.value })} />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Input label="Representante legal que firma" value={form.representativeName} onChange={(e) => setForm({ ...form, representativeName: e.target.value })} />
          <Input label="Cargo" value={form.representativeTitle} onChange={(e) => setForm({ ...form, representativeTitle: e.target.value })} />
        </div>
        {area("receipt-responsibility", "Constancia de recepción y responsabilidad", "responsibilityText")}
        {area("receipt-privacy", "Texto sobre el aviso de privacidad", "privacyText")}
        {settings.approved ? <p className="text-xs text-muted">Aprobado por {settings.approvedByName}. Si cambias un texto, la aprobación se retira.</p> : null}
        <div className="flex flex-wrap gap-2">
          <Button
            disabled={saving || !dirty || !form.responsibleEntity.trim() || !form.responsibilityText.trim() || !form.privacyText.trim()}
            onClick={async () => {
              setSaving(true);
              try {
                setSettings(
                  await updateReceiptSettings({
                    responsibleEntity: form.responsibleEntity.trim(),
                    representativeName: form.representativeName.trim() || undefined,
                    representativeTitle: form.representativeTitle.trim() || undefined,
                    responsibilityText: form.responsibilityText.trim(),
                    privacyText: form.privacyText.trim(),
                  }),
                );
                showToast("Guardado");
              } catch (err) {
                showToast(errorText(err));
              } finally {
                setSaving(false);
              }
            }}
          >
            Guardar
          </Button>
          <Button
            variant={settings.approved ? "ghost" : "secondary"}
            disabled={saving || dirty}
            onClick={async () => {
              setSaving(true);
              try {
                setSettings(await approveReceiptTexts(!settings.approved));
                showToast(settings.approved ? "Aprobación retirada" : "Textos aprobados");
              } catch (err) {
                showToast(errorText(err));
              } finally {
                setSaving(false);
              }
            }}
          >
            {settings.approved ? "Retirar aprobación" : "Aprobar textos (área legal)"}
          </Button>
        </div>
      </div>
    </Card>
  );
}
