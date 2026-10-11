"use client";

import {
  AdvisorChip,
  Avatar,
  BarList,
  LoadError,
  PropertyMini,
  Segmented,
  SourceLabel,
  StageBadge,
  StatCard,
  periodFrom,
  periodLabels,
  sourceIcons,
  useApi,
  type Period,
} from "@/components/crm/CrmUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { Card, CardHeader } from "@/components/ui/Card";
import { StateMessage } from "@/components/ui/StateMessage";
import { getCrmDashboard, type CrmDashboard, type LeadSource } from "@/lib/api/crm";
import { sourceLabels, stageLabels, stageOrder, timeAgo } from "@/lib/crm/labels";
import { useCan } from "@/lib/permissions";
import { AlertTriangle, ArrowRight, CheckCircle2, Clock, TrendingUp, Users } from "lucide-react";
import Link from "next/link";
import { useState } from "react";

export default function DashboardPage() {
  const [period, setPeriod] = useState<Period>("30d");
  // La fecha de inicio se calcula al cambiar de periodo, no en cada render.
  const [from, setFrom] = useState<string | undefined>(() => periodFrom("30d"));
  const dashboard = useApi(() => getCrmDashboard(from), [from]);

  return (
    <PageContainer
      title="Dashboard"
      subtitle="Cómo van los leads: de dónde llegan, a quién se asignan y en qué etapa están."
      action={
        <Segmented
          label="Periodo"
          value={period}
          onChange={(p) => {
            setPeriod(p);
            setFrom(periodFrom(p));
          }}
          options={(Object.keys(periodLabels) as Period[]).map((id) => ({ id, label: periodLabels[id] }))}
        />
      }
    >
      {dashboard.error && !dashboard.data ? (
        <LoadError message={dashboard.error} onRetry={dashboard.reload} />
      ) : dashboard.data ? (
        <DashboardContent data={dashboard.data} />
      ) : (
        <StateMessage kind="loading" title="Cargando indicadores…" />
      )}
    </PageContainer>
  );
}

function pct(part: number, total: number): string {
  return total === 0 ? "0%" : `${Math.round((part / total) * 100)}%`;
}

function DashboardContent({ data }: { data: CrmDashboard }) {
  const can = useCan();
  const maxStage = Math.max(1, ...stageOrder.map((s) => data.byStage[s] ?? 0));
  const sources = (Object.entries(data.bySource) as [LeadSource, number][]).sort((a, b) => b[1] - a[1]);

  return (
    <div className="space-y-5">
      {data.unassigned > 0 && can("LEAD_ASSIGN") ? (
        <div className="flex flex-col gap-3 rounded-xl border border-warning-text/25 bg-warning-bg px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-start gap-3">
            <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-warning-text" aria-hidden />
            <div>
              <p className="text-sm font-semibold text-obsessed">
                {data.unassigned} lead{data.unassigned === 1 ? "" : "s"} sin asesor
              </p>
              <p className="text-xs text-muted">Llegaron cuando ningún asesor estaba disponible. Asígnalos para que no se enfríen.</p>
            </div>
          </div>
          <Link
            href="/leads?grupo=UNASSIGNED"
            className="inline-flex min-h-9 items-center gap-1.5 self-start rounded-xl bg-gold px-3 py-1.5 text-sm font-medium text-brand-ink hover:bg-gold-hover sm:self-auto"
          >
            Ver y asignar <ArrowRight className="h-4 w-4" aria-hidden />
          </Link>
        </div>
      ) : null}

      <section aria-label="Indicadores" className="grid grid-cols-2 gap-3 lg:grid-cols-4 lg:gap-4">
        <StatCard icon={Users} value={data.total} label="Leads del periodo" hint={`${data.open} siguen en seguimiento`} href="/leads" />
        <StatCard icon={CheckCircle2} tone="info" value={data.assigned} label="Asignados" hint={`${pct(data.assigned, data.total)} del total`} />
        <StatCard icon={Clock} tone="warning" value={data.staleNew} label="Sin primer contacto" hint="Nuevos con más de 24 h" href="/leads?grupo=NEW" />
        <StatCard
          icon={TrendingUp}
          tone="success"
          value={data.won}
          label="Cerrados ganados"
          hint={data.closed ? `${pct(data.won, data.closed)} de los cerrados` : "Aún sin cierres"}
          href="/leads?grupo=WON"
        />
      </section>

      <Card>
        <CardHeader title="Embudo" description="Leads del periodo por etapa de seguimiento." />
        {data.total === 0 ? (
          <p className="py-6 text-center text-sm text-muted">Sin leads en el periodo</p>
        ) : (
          <ol className="grid grid-cols-2 gap-3 sm:grid-cols-4 xl:grid-cols-7">
            {stageOrder.map((stage) => {
              const count = data.byStage[stage] ?? 0;
              return (
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
              );
            })}
          </ol>
        )}
      </Card>

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
        <Card>
          <CardHeader title="¿De dónde llegan?" description="Leads del periodo por fuente." />
          <BarList
            rows={sources.map(([source, count]) => {
              const Icon = sourceIcons[source];
              return { key: source, label: sourceLabels[source], count, leading: <Icon className="h-4 w-4 shrink-0 text-muted" aria-hidden />, href: `/leads?fuente=${source}` };
            })}
          />
        </Card>
        <Card>
          <CardHeader title="Leads por asesor" description="Asignados en el periodo y cuántos cerró cada uno." />
          <BarList
            emptyLabel="Sin asignaciones en el periodo"
            rows={data.byAdvisor.map((row) => ({
              key: row.advisorId,
              label: (
                <>
                  {row.name}
                  {row.won ? <span className="ml-1.5 text-xs text-success-text">· {row.won} ganado{row.won === 1 ? "" : "s"}</span> : null}
                </>
              ),
              count: row.leads,
              leading: <Avatar name={row.name} className="h-7 w-7 text-[0.65rem]" />,
              href: `/leads?asesor=${row.advisorId}`,
            }))}
          />
        </Card>
      </div>

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)]">
        <Card>
          <CardHeader title="Propiedades con más interés" description="Por cuántos leads preguntaron por ellas (datos de 21Online)." />
          {data.topProperties.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted">Ningún lead mencionó una propiedad en el periodo</p>
          ) : (
            <ul className="divide-y divide-border">
              {data.topProperties.map((p) => (
                <li key={`${p.online21Id ?? p.key}`} className="flex items-center justify-between gap-3 py-3 first:pt-0 last:pb-0">
                  <PropertyMini property={p} />
                  <span className="shrink-0 text-sm tabular-nums text-muted">
                    <strong className="font-semibold text-obsessed">{p.leads}</strong> lead{p.leads === 1 ? "" : "s"}
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
                    <AdvisorChip advisor={lead.advisor} />
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
