"use client";

import { AdvisorChip, SourceLabel, StageBadge, useApi } from "@/components/crm/CrmUi";
import { Card, CardHeader } from "@/components/ui/Card";
import { listLeads, type LeadSource } from "@/lib/api/crm";
import { sourceLabels, timeAgo } from "@/lib/crm/labels";
import { ArrowRight } from "lucide-react";
import Link from "next/link";

/**
 * Quién ha preguntado por esta propiedad en Genera (WhatsApp, portales, campañas…),
 * con un resumen por fuente. Solo lectura: cada lead se atiende en su detalle.
 */
export function PropertyLeads({ online21Id }: { online21Id: number }) {
  const leads = useApi(() => listLeads({ propertyId: online21Id, size: 100 }), [online21Id]);
  const data = leads.data;
  if (!data) {
    return (
      <Card>
        <CardHeader title="Leads de esta propiedad" />
        <p className="text-sm text-muted">{leads.error ?? "Cargando…"}</p>
      </Card>
    );
  }

  const bySource = new Map<LeadSource, number>();
  for (const l of data.items) bySource.set(l.source, (bySource.get(l.source) ?? 0) + 1);
  const open = data.items.filter((l) => l.stage !== "WON" && l.stage !== "LOST").length;

  return (
    <Card>
      <CardHeader
        title="Leads de esta propiedad"
        description={data.total === 0 ? "Nadie ha preguntado por ella en Genera todavía." : `${data.total} en total · ${open} en seguimiento`}
        action={
          data.total > 0 ? (
            <Link href={`/leads?propiedad=${online21Id}`} className="inline-flex items-center gap-1 text-sm font-medium text-dark-gold hover:text-obsessed">
              Ver en Leads <ArrowRight className="h-4 w-4" aria-hidden />
            </Link>
          ) : null
        }
      />
      {bySource.size > 0 ? (
        <ul className="mb-4 flex flex-wrap gap-2" aria-label="Por fuente">
          {[...bySource.entries()]
            .sort((a, b) => b[1] - a[1])
            .map(([source, count]) => (
              <li key={source} className="rounded-full bg-app-bg px-3 py-1 text-xs text-obsessed">
                {sourceLabels[source]} <strong className="font-semibold tabular-nums">{count}</strong>
              </li>
            ))}
        </ul>
      ) : null}
      {data.items.length > 0 ? (
        <ul className="divide-y divide-border">
          {data.items.slice(0, 15).map((lead) => (
            <li key={lead.id}>
              <Link
                href={`/leads/${lead.id}`}
                className="grid grid-cols-1 gap-2 py-3 hover:bg-app-bg/50 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30 sm:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)_auto] sm:items-center sm:gap-4"
              >
                <span className="min-w-0">
                  <span className="block truncate text-sm font-semibold text-obsessed">{lead.name}</span>
                  <SourceLabel source={lead.source} detail={lead.sourceDetail} className="text-xs" />
                </span>
                <AdvisorChip advisor={lead.advisor} />
                <span className="flex items-center gap-3 sm:justify-end">
                  <StageBadge stage={lead.stage} />
                  <span className="w-20 text-right text-xs text-muted">{timeAgo(lead.createdAt)}</span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      ) : null}
      {data.items.length > 15 ? <p className="mt-3 text-xs text-muted">Mostrando los 15 más recientes.</p> : null}
    </Card>
  );
}
