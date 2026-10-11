"use client";

import {
  AdvisorChip,
  Avatar,
  BarList,
  PreviewNotice,
  PropertyMini,
  Segmented,
  SourceLabel,
  StageBadge,
  StatCard,
  periodLabels,
  periodStart,
  sourceIcons,
  type Period,
} from "@/components/crm/CrmUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { Card, CardHeader } from "@/components/ui/Card";
import { StateMessage } from "@/components/ui/StateMessage";
import { openStages, sourceLabels, stageLabels, stageOrder, timeAgo } from "@/lib/crm/labels";
import { useCrm, type CrmState } from "@/lib/crm/store";
import type { LeadSource } from "@/types/crm";
import { AlertTriangle, ArrowRight, CheckCircle2, Clock, TrendingUp, Users } from "lucide-react";
import Link from "next/link";
import { useMemo, useState } from "react";

export default function DashboardPage() {
  const crm = useCrm();
  const [period, setPeriod] = useState<Period>("30d");

  return (
    <PageContainer
      title="Dashboard"
      subtitle="Cómo van los leads: de dónde llegan, a quién se asignan y en qué etapa están."
      action={
        <Segmented label="Periodo" value={period} onChange={setPeriod} options={(Object.keys(periodLabels) as Period[]).map((id) => ({ id, label: periodLabels[id] }))} />
      }
    >
      <PreviewNotice />
      {crm ? <DashboardContent crm={crm} period={period} /> : <StateMessage kind="loading" title="Cargando indicadores…" />}
    </PageContainer>
  );
}

function pct(part: number, total: number): string {
  return total === 0 ? "0%" : `${Math.round((part / total) * 100)}%`;
}

function DashboardContent({ crm, period }: { crm: CrmState; period: Period }) {
  // Hora de referencia fija mientras la pantalla está abierta.
  const [now] = useState(() => Date.now());
  const data = useMemo(() => {
    const from = periodStart(period, now);
    const leads = crm.leads.filter((l) => new Date(l.createdAt).getTime() >= from);
    const assigned = leads.filter((l) => l.advisorId);
    const unassigned = leads.filter((l) => !l.advisorId);
    const won = leads.filter((l) => l.stage === "WON");
    const closed = leads.filter((l) => l.stage === "WON" || l.stage === "LOST");
    const open = leads.filter((l) => openStages.includes(l.stage));
    // Nuevos que llevan más de 24 h sin primer contacto.
    const stale = leads.filter((l) => l.stage === "NEW" && now - new Date(l.createdAt).getTime() > 24 * 3_600_000);

    const bySource = (Object.keys(sourceLabels) as LeadSource[])
      .map((source) => ({ source, count: leads.filter((l) => l.source === source).length }))
      .filter((r) => r.count > 0)
      .sort((a, b) => b.count - a.count);

    const byAdvisor = crm.advisors
      .map((a) => ({
        advisor: a,
        count: leads.filter((l) => l.advisorId === a.id).length,
        won: leads.filter((l) => l.advisorId === a.id && l.stage === "WON").length,
      }))
      .filter((r) => r.count > 0)
      .sort((a, b) => b.count - a.count);

    const byProperty = crm.properties
      .map((p) => ({ property: p, count: leads.filter((l) => l.propertyId === p.id).length }))
      .filter((r) => r.count > 0)
      .sort((a, b) => b.count - a.count)
      .slice(0, 6);

    const byStage = stageOrder.map((stage) => ({ stage, count: leads.filter((l) => l.stage === stage).length }));
    const recent = [...leads].sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 6);
    return { leads, assigned, unassigned, won, closed, open, stale, bySource, byAdvisor, byProperty, byStage, recent };
  }, [crm, period, now]);

  const advisorById = new Map(crm.advisors.map((a) => [a.id, a]));
  const total = data.leads.length;
  const maxStage = Math.max(1, ...data.byStage.map((s) => s.count));

  return (
    <div className="space-y-5">
      {data.unassigned.length > 0 ? (
        <div className="flex flex-col gap-3 rounded-xl border border-warning-text/25 bg-warning-bg px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-start gap-3">
            <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-warning-text" aria-hidden />
            <div>
              <p className="text-sm font-semibold text-obsessed">
                {data.unassigned.length} lead{data.unassigned.length === 1 ? "" : "s"} sin asesor
              </p>
              <p className="text-xs text-muted">Llegaron cuando ningún asesor estaba disponible. Asígnalos para que no se enfríen.</p>
            </div>
          </div>
          <Link
            href="/leads?grupo=unassigned"
            className="inline-flex min-h-9 items-center gap-1.5 self-start rounded-xl bg-gold px-3 py-1.5 text-sm font-medium text-brand-ink hover:bg-gold-hover sm:self-auto"
          >
            Ver y asignar <ArrowRight className="h-4 w-4" aria-hidden />
          </Link>
        </div>
      ) : null}

      <section aria-label="Indicadores" className="grid grid-cols-2 gap-3 lg:grid-cols-4 lg:gap-4">
        <StatCard icon={Users} value={total} label="Leads del periodo" hint={`${data.open.length} siguen en seguimiento`} href="/leads" />
        <StatCard icon={CheckCircle2} tone="info" value={data.assigned.length} label="Asignados" hint={`${pct(data.assigned.length, total)} del total`} />
        <StatCard icon={Clock} tone="warning" value={data.stale.length} label="Sin primer contacto" hint="Nuevos con más de 24 h" href="/leads?grupo=new" />
        <StatCard
          icon={TrendingUp}
          tone="success"
          value={data.won.length}
          label="Cerrados ganados"
          hint={data.closed.length ? `${pct(data.won.length, data.closed.length)} de los cerrados` : "Aún sin cierres"}
          href="/leads?grupo=won"
        />
      </section>

      <Card>
        <CardHeader title="Embudo" description="Leads del periodo por etapa de seguimiento." />
        {total === 0 ? (
          <p className="py-6 text-center text-sm text-muted">Sin leads en el periodo</p>
        ) : (
          <ol className="grid grid-cols-2 gap-3 sm:grid-cols-4 xl:grid-cols-7">
            {data.byStage.map(({ stage, count }) => (
              <li key={stage} className="flex min-w-0 flex-col rounded-xl border border-border p-3">
                <span className="truncate text-xs text-muted">{stageLabels[stage]}</span>
                <span className="mt-1 text-2xl font-semibold tabular-nums text-obsessed">{count}</span>
                <span className="mt-2 h-1.5 overflow-hidden rounded-full bg-app-bg">
                  <span
                    className={`block h-full rounded-full ${stage === "LOST" ? "bg-danger-text/60" : stage === "WON" ? "bg-success-text" : "bg-gold"}`}
                    style={{ width: `${(count / maxStage) * 100}%` }}
                  />
                </span>
              </li>
            ))}
          </ol>
        )}
      </Card>

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
        <Card>
          <CardHeader title="¿De dónde llegan?" description="Leads del periodo por fuente." />
          <BarList
            rows={data.bySource.map(({ source, count }) => {
              const Icon = sourceIcons[source];
              return {
                key: source,
                label: sourceLabels[source],
                count,
                leading: <Icon className="h-4 w-4 shrink-0 text-muted" aria-hidden />,
                href: `/leads?fuente=${source}`,
              };
            })}
          />
        </Card>
        <Card>
          <CardHeader title="Leads por asesor" description="Asignados en el periodo y cuántos cerró cada uno." />
          <BarList
            emptyLabel="Sin asignaciones en el periodo"
            rows={data.byAdvisor.map(({ advisor, count, won }) => ({
              key: advisor.id,
              label: (
                <>
                  {advisor.name}
                  {won ? <span className="ml-1.5 text-xs text-success-text">· {won} ganado{won === 1 ? "" : "s"}</span> : null}
                </>
              ),
              count,
              leading: <Avatar name={advisor.name} className="h-7 w-7 text-[0.65rem]" />,
              href: `/leads?asesor=${advisor.id}`,
            }))}
          />
        </Card>
      </div>

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
        <Card>
          <CardHeader
            title="Propiedades con más interés"
            action={
              <Link href="/propiedades" className="inline-flex items-center gap-1 text-sm font-medium text-dark-gold hover:text-obsessed">
                Ver todas <ArrowRight className="h-4 w-4" aria-hidden />
              </Link>
            }
          />
          {data.byProperty.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted">Ningún lead mencionó una propiedad en el periodo</p>
          ) : (
            <ul className="divide-y divide-border">
              {data.byProperty.map(({ property, count }) => (
                <li key={property.id} className="flex items-center justify-between gap-3 py-3 first:pt-0 last:pb-0">
                  <PropertyMini property={property} />
                  <span className="shrink-0 text-sm tabular-nums text-muted">
                    <strong className="font-semibold text-obsessed">{count}</strong> lead{count === 1 ? "" : "s"}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card>
          <CardHeader
            title="Últimos leads"
            action={
              <Link href="/leads" className="inline-flex items-center gap-1 text-sm font-medium text-dark-gold hover:text-obsessed">
                Ver todos <ArrowRight className="h-4 w-4" aria-hidden />
              </Link>
            }
          />
          {data.recent.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted">Sin leads en el periodo</p>
          ) : (
            <ul className="divide-y divide-border">
              {data.recent.map((lead) => (
                <li key={lead.id}>
                  <Link
                    href={`/leads/${lead.id}`}
                    className="grid grid-cols-1 gap-2 py-3 hover:bg-app-bg/50 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30 sm:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)_auto] sm:items-center sm:gap-4"
                  >
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-semibold text-obsessed">{lead.name}</span>
                      <SourceLabel source={lead.source} detail={lead.sourceDetail} className="text-xs" />
                    </span>
                    <AdvisorChip advisor={lead.advisorId ? advisorById.get(lead.advisorId) : undefined} />
                    <span className="flex items-center gap-3 sm:justify-end">
                      <StageBadge stage={lead.stage} />
                      <span className="w-20 text-right text-xs text-muted">{timeAgo(lead.createdAt)}</span>
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  );
}
