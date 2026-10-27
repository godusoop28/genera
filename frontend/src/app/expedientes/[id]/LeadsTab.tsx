"use client";

import { AdvisorChip, LoadError, SourceLabel, StageBadge, useApi } from "@/components/crm/CrmUi";
import { Card, CardHeader } from "@/components/ui/Card";
import { StateMessage } from "@/components/ui/StateMessage";
import { getAgenda, listLeads } from "@/lib/api/crm";
import { formatInstantMx, taskResultLabels, taskTypeEmoji, taskTypeLabels, timeAgo } from "@/lib/crm/labels";
import { ArrowRight } from "lucide-react";
import Link from "next/link";
import type { ExpedienteContext } from "./page";

/**
 * Control de Cambios #30, parte 7: prospectos vinculados al expediente del
 * inmueble (pueden ser varios interesados) y sus actividades, para tener el
 * historial completo de la transacción.
 */
export function LeadsTab({ expediente }: ExpedienteContext) {
  const leads = useApi(() => listLeads({ expedienteId: expediente.id, size: 50 }), [expediente.id]);
  const ids = (leads.data?.items ?? []).map((l) => l.id);
  // Actividades de los últimos 12 meses y próximas 3 de cada lead vinculado.
  const activities = useApi(
    () =>
      ids.length
        ? Promise.all(
            ids.map((leadId) =>
              getAgenda({ leadId, from: new Date(Date.now() - 365 * 86_400_000).toISOString(), to: new Date(Date.now() + 90 * 86_400_000).toISOString() }),
            ),
          ).then((all) => all.flat().sort((a, b) => (b.scheduledAt ?? "").localeCompare(a.scheduledAt ?? "")))
        : Promise.resolve([]),
    [ids.join(",")],
  );

  if (leads.error && !leads.data) return <LoadError message={leads.error} onRetry={leads.reload} />;
  if (!leads.data) return <StateMessage kind="loading" title="Cargando leads…" />;
  if (leads.data.items.length === 0) {
    return (
      <StateMessage
        kind="empty"
        title="Sin leads vinculados"
        description="Cuando un prospecto llegue a “Oferta aceptada” con la clave de este inmueble se vincula solo; también puedes vincularlo desde la ficha del lead con el folio."
      />
    );
  }
  return (
    <div className="space-y-5">
      <Card>
        <CardHeader title="Prospectos de este inmueble" description="Leads vinculados al expediente." />
        <ul className="divide-y divide-border">
          {leads.data.items.map((lead) => (
            <li key={lead.id}>
              <Link href={`/leads/${lead.id}`} className="grid grid-cols-1 gap-2 py-3 hover:bg-app-bg/50 sm:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)_auto] sm:items-center sm:gap-4">
                <span className="min-w-0">
                  <span className="block truncate text-sm font-semibold text-obsessed">{lead.name}</span>
                  <SourceLabel source={lead.source} label={lead.sourceLabel} detail={lead.sourceDetail} className="text-xs" />
                </span>
                <AdvisorChip advisor={lead.advisor} />
                <span className="flex items-center gap-3 sm:justify-end">
                  <StageBadge stage={lead.stage} />
                  <span className="text-xs text-muted">{timeAgo(lead.lastActivityAt)}</span>
                  <ArrowRight className="h-4 w-4 text-muted" aria-hidden />
                </span>
              </Link>
            </li>
          ))}
        </ul>
      </Card>
      <Card>
        <CardHeader title="Actividades con los prospectos" />
        {!activities.data ? (
          <p className="text-sm text-muted">Cargando…</p>
        ) : activities.data.length === 0 ? (
          <p className="text-sm text-muted">Sin actividades registradas.</p>
        ) : (
          <ul className="space-y-2">
            {activities.data.map((t) => (
              <li key={t.id} className="flex flex-wrap items-baseline gap-x-2 text-sm">
                <span aria-hidden>{taskTypeEmoji[t.type]}</span>
                <span className="font-medium text-obsessed">{t.leadName}</span>
                <span className="text-muted">
                  {t.title ?? taskTypeLabels[t.type]}
                  {t.result ? ` · ${taskResultLabels[t.result]}` : t.status === "SCHEDULED" ? " · programada" : ""}
                </span>
                <span className="ml-auto text-xs text-muted">{t.scheduledAt ? formatInstantMx(t.scheduledAt) : ""}</span>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}
