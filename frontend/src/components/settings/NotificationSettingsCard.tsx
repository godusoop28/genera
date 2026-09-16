"use client";

import { Card, CardHeader } from "@/components/ui/Card";
import { Toggle } from "@/components/ui/Toggle";
import { useSettings } from "@/context/SettingsProvider";

export function NotificationSettingsCard() {
  const { settings, setNotification } = useSettings();

  return (
    <Card>
      <CardHeader title="Notificaciones" description="Simuladas para este prototipo; no se envían correos reales." />
      <div className="flex flex-col divide-y divide-border">
        <div className="py-2 first:pt-0 last:pb-0">
          <Toggle
            label="Avisarme cuando un cliente envíe documentos"
            checked={settings.notifications.documentsReceived}
            onChange={(v) => setNotification("documentsReceived", v)}
          />
        </div>
        <div className="py-2 first:pt-0 last:pb-0">
          <Toggle
            label="Avisarme sobre correcciones solicitadas"
            checked={settings.notifications.correctionsRequested}
            onChange={(v) => setNotification("correctionsRequested", v)}
          />
        </div>
        <div className="py-2 first:pt-0 last:pb-0">
          <Toggle
            label="Resumen semanal por correo"
            checked={settings.notifications.weeklySummary}
            onChange={(v) => setNotification("weeklySummary", v)}
          />
        </div>
      </div>
    </Card>
  );
}
