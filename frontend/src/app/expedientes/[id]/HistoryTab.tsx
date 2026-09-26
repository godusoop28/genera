"use client";

import { Badge } from "@/components/ui/Badge";
import { Card, CardHeader } from "@/components/ui/Card";
import { getActivity, getNotifications } from "@/lib/api/activity";
import { getChanges } from "@/lib/api/expedientes";
import type { ActivityResponse, ChangeResponse, NotificationResponse } from "@/lib/api/types";
import { formatDateTime, label, notificationStatusLabels, roleLabels } from "@/lib/labels";
import { cn } from "@/lib/utils";
import { useEffect, useState } from "react";
import type { ExpedienteContext } from "./page";

function actorText(type: string | null, name: string | null, role: string | null): string {
  switch (type) {
    case "CLIENT":
      return "Cliente (desde su liga)";
    case "SYSTEM":
      return "Sistema (automático)";
    case "STAFF":
      return `${name ?? "Usuario interno"}${role ? ` · ${label(roleLabels, role)}` : ""}`;
    default:
      return "No registrado (movimiento anterior a la bitácora con usuario)";
  }
}

const views = [
  { id: "actividad", label: "Movimientos" },
  { id: "cambios", label: "Correcciones de datos" },
  { id: "avisos", label: "Avisos enviados" },
] as const;

export function HistoryTab({ expediente }: ExpedienteContext) {
  const [view, setView] = useState<(typeof views)[number]["id"]>("actividad");
  const [activity, setActivity] = useState<ActivityResponse[] | null>(null);
  const [changes, setChanges] = useState<ChangeResponse[] | null>(null);
  const [notifications, setNotifications] = useState<NotificationResponse[] | null>(null);

  useEffect(() => {
    getActivity(expediente.id).then(setActivity).catch(() => setActivity([]));
    getChanges(expediente.id).then(setChanges).catch(() => setChanges([]));
    getNotifications(expediente.id).then(setNotifications).catch(() => setNotifications([]));
  }, [expediente.id]);

  return (
    <Card>
      <CardHeader title="Bitácora del expediente" description="Quién hizo cada acción, con qué rol, sobre qué documento, y cuándo." />
      <div className="mb-4 flex gap-2">
        {views.map((v) => (
          <button
            key={v.id}
            type="button"
            onClick={() => setView(v.id)}
            className={cn("rounded-lg border px-3 py-1.5 text-sm", view === v.id ? "border-gold bg-gold/15" : "border-border")}
          >
            {v.label}
          </button>
        ))}
      </div>

      {view === "actividad" ? (
        <ul className="flex flex-col gap-3">
          {activity?.length === 0 ? <li className="text-sm text-muted">Sin movimientos todavía.</li> : null}
          {activity?.map((a) => (
            <li key={a.id} className="border-l-2 border-gold pl-3">
              <p className="text-sm text-obsessed">{a.message}</p>
              <p className="text-xs text-muted">
                {formatDateTime(a.occurredAt)} · {actorText(a.actorType, a.actorName, a.actorRole)}
                {a.documentLabel ? ` · Documento: ${a.documentLabel}` : ""}
              </p>
            </li>
          ))}
        </ul>
      ) : null}

      {view === "cambios" ? (
        <ul className="flex flex-col gap-2">
          {changes?.length === 0 ? <li className="text-sm text-muted">No se han corregido datos.</li> : null}
          {changes?.map((c) => (
            <li key={c.id} className="rounded-lg border border-border px-3 py-2 text-sm">
              <p className="text-obsessed">
                <span className="font-medium">{c.section}</span> — {c.field}: <span className="text-muted line-through">{c.oldValue ?? "vacío"}</span> →{" "}
                <span>{c.newValue ?? "vacío"}</span>
              </p>
              <p className="text-xs text-muted">
                {formatDateTime(c.changedAt)} · {actorText(c.actorType, c.actorName, c.actorRole)}
                {c.reason ? ` · Motivo: ${c.reason}` : ""}
              </p>
            </li>
          ))}
        </ul>
      ) : null}

      {view === "avisos" ? (
        <ul className="flex flex-col gap-2">
          {notifications?.length === 0 ? <li className="text-sm text-muted">No se han enviado avisos.</li> : null}
          {notifications?.map((n) => (
            <li key={n.id} className="rounded-lg border border-border px-3 py-2 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="text-obsessed">
                  {n.kind} <span className="text-muted">→ {n.recipient}</span>
                </p>
                <Badge tone={n.status === "SENT" ? "success" : n.status === "FAILED" ? "danger" : n.status === "SKIPPED" ? "neutral" : "warning"}>
                  {label(notificationStatusLabels, n.status)}
                </Badge>
              </div>
              <p className="text-xs text-muted">
                {n.subject} · {formatDateTime(n.createdAt)}
                {n.attempts > 1 ? ` · ${n.attempts} intentos` : ""}
              </p>
              {n.lastError ? <p className="mt-1 text-xs text-danger-text">Error de entrega: {n.lastError}</p> : null}
            </li>
          ))}
        </ul>
      ) : null}
    </Card>
  );
}
