"use client";

import { Button } from "@/components/ui/Button";
import { StateMessage } from "@/components/ui/StateMessage";
import { listAccountEvents } from "@/lib/api/users";
import type { AuditEventResponse } from "@/lib/api/types";
import { errorText } from "@/lib/errors";
import { RefreshCw } from "lucide-react";
import { useCallback, useEffect, useState } from "react";

const PAGE_SIZE = 50;
const dateTime = new Intl.DateTimeFormat("es-MX", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });

const actionLabels: Record<string, string> = {
  UserCREATED: "Alta",
  UserUPDATED: "Edición",
  UserROLE_CHANGED: "Cambio de rol",
  UserDEACTIVATED: "Desactivación",
  UserACTIVATED: "Reactivación",
  UserOFFBOARDED: "Baja",
  UserINVITE_LINK_ISSUED: "Enlace de alta",
  UserRESET_LINK_ISSUED: "Restablecimiento",
  UserPASSWORD_SET: "Contraseña definida",
  UserPASSWORD_CHANGED: "Cambio de contraseña",
  UserLOCKED: "Bloqueo",
  UserUNLOCKED: "Desbloqueo",
};

const roleLabels: Record<string, string> = {
  ADMINISTRATOR: "Administrador",
  DIRECTOR: "Director",
  ADVISOR: "Asesor",
  DOCUMENT_REVIEWER: "Revisor documental",
};

/**
 * Control de Cambios #25: bitácora de cuentas. Cada registro dice quién hizo la acción, a qué
 * cuenta, qué y cuándo. Nunca contiene contraseñas ni enlaces.
 */
export function AccountAuditLog() {
  const [events, setEvents] = useState<AuditEventResponse[] | null>(null);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const load = useCallback((pageToLoad: number) => {
    listAccountEvents(pageToLoad, PAGE_SIZE)
      .then((result) => {
        setEvents((current) => (pageToLoad === 0 ? result.items : [...(current ?? []), ...result.items]));
        setPage(pageToLoad);
        setHasMore((pageToLoad + 1) * PAGE_SIZE < result.totalElements);
        setError(null);
      })
      .catch((err) => setError(errorText(err)))
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    load(0);
  }, [load]);

  if (error && events === null) {
    return (
      <StateMessage
        kind="error"
        title="No se pudo cargar la bitácora"
        description={error}
        action={
          <Button variant="secondary" onClick={() => load(0)}>
            <RefreshCw className="h-4 w-4" aria-hidden /> Reintentar
          </Button>
        }
      />
    );
  }
  if (events === null) return <StateMessage kind="loading" title="Cargando bitácora…" />;
  if (events.length === 0) {
    return <StateMessage kind="empty" title="Sin movimientos" description="Aquí aparecerán las altas, cambios de rol, bajas y restablecimientos de contraseña." />;
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-muted">Altas, cambios de rol y estado, restablecimientos y bloqueos. No se guarda ninguna contraseña.</p>
      <div className="overflow-hidden rounded-xl border border-border bg-card shadow-[0_1px_2px_rgba(36,39,35,0.04)]">
        <ul className="divide-y divide-border">
          {events.map((e) => (
            <li key={e.id} className="grid gap-1 px-4 py-3 sm:grid-cols-[10rem_9rem_minmax(0,1fr)] sm:gap-4 sm:px-5">
              <time dateTime={e.occurredAt} className="text-xs tabular-nums text-muted sm:text-sm">
                {dateTime.format(new Date(e.occurredAt))}
              </time>
              <span className="text-xs font-semibold uppercase tracking-wide text-dark-gold sm:text-sm sm:normal-case sm:tracking-normal">
                {actionLabels[e.eventType] ?? e.eventType}
              </span>
              <span className="min-w-0 text-sm text-obsessed">
                {e.summary}
                <span className="block text-xs text-muted">
                  Por:{" "}
                  {e.actorType === "SYSTEM"
                    ? "el sistema (automático)"
                    : `${e.actorName ?? "—"}${e.actorRole ? ` (${roleLabels[e.actorRole] ?? e.actorRole})` : ""}`}
                </span>
              </span>
            </li>
          ))}
        </ul>
      </div>
      {hasMore ? (
        <Button
          variant="secondary"
          onClick={() => {
            setLoading(true);
            load(page + 1);
          }}
          disabled={loading}
        >
          {loading ? "Cargando…" : "Ver más"}
        </Button>
      ) : null}
      {error ? (
        <p role="alert" className="text-sm text-danger-text">
          {error}
        </p>
      ) : null}
    </div>
  );
}
