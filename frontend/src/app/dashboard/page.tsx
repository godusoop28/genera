"use client";

import {
  AdvisorChip,
  Avatar,
  LoadError,
  PropertyMini,
  Segmented,
  SourceLabel,
  StageBadge,
  StatCard,
  periodFrom,
  periodLabels,
  useApi,
  type Period,
} from "@/components/crm/CrmUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { Card, CardHeader } from "@/components/ui/Card";
import { StateMessage } from "@/components/ui/StateMessage";
import { getCrmDashboard, listAdvisors, type CrmDashboard } from "@/lib/api/crm";
import { listCaptured } from "@/lib/api/properties";
import { formatPrice, sourceCategoryLabels, stageLabels, stageOrder, timeAgo } from "@/lib/crm/labels";
import { useCan } from "@/lib/permissions";
import { AlertTriangle, ArrowRight, CalendarCheck, CheckCircle2, Clock, ClipboardCheck, TrendingUp, Users, Wallet } from "lucide-react";
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

  return (
    <div className="space-y-5">
      {data.pendingAssignment > 0 ? (
        <div className="flex flex-col gap-3 rounded-xl border border-info-text/25 bg-info-bg px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-start gap-3">
            <Clock className="mt-0.5 h-5 w-5 shrink-0 text-info-text" aria-hidden />
            <div>
              <p className="text-sm font-semibold text-obsessed">
                {data.pendingAssignment} lead{data.pendingAssignment === 1 ? "" : "s"} esperando turno
              </p>
              <p className="text-xs text-muted">Se asignan solos cuando empiece un turno elegible. Revisa el calendario si no hay turnos próximos.</p>
            </div>
          </div>
          <Link href="/leads?grupo=PENDING" className="inline-flex min-h-9 items-center gap-1.5 self-start rounded-xl border border-border bg-card px-3 py-1.5 text-sm font-medium text-obsessed hover:bg-app-bg sm:self-auto">
            Ver pendientes <ArrowRight className="h-4 w-4" aria-hidden />
          </Link>
        </div>
      ) : null}

      {data.unassigned > 0 && can("LEAD_ASSIGN") ? (
        <div className="flex flex-col gap-3 rounded-xl border border-warning-text/25 bg-warning-bg px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-start gap-3">
            <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0 text-warning-text" aria-hidden />
            <div>
              <p className="text-sm font-semibold text-obsessed">
                {data.unassigned} lead{data.unassigned === 1 ? "" : "s"} sin asignar
              </p>
              <p className="text-xs text-muted">No están en espera automática (registrados sin asignar o antes del calendario). Asígnalos para que no se enfríen.</p>
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

      {can("LEAD_VIEW_ALL") ? <ReviewCard canSeeProperties={can("PROPERTY_VIEW")} /> : null}

      <section aria-label="Indicadores" className="grid grid-cols-2 gap-3 lg:grid-cols-4 lg:gap-4">
        <StatCard icon={Users} value={data.total} label="Leads del periodo" hint={`${data.open} siguen en seguimiento`} href="/leads" />
        <StatCard icon={CheckCircle2} tone="info" value={data.assigned} label="Asignados" hint={`${pct(data.assigned, data.total)} del total`} />
        <StatCard icon={Clock} tone="warning" value={data.staleNew} label="Sin primer contacto" hint="Nuevos con más de 24 h" href="/leads?grupo=NEW" />
        <StatCard
          icon={TrendingUp}
          tone="success"
          value={`${data.conversionRate}%`}
          label="Tasa de conversión"
          hint={`${data.converted} en oferta aceptada o cierre · ${data.won} cierre${data.won === 1 ? "" : "s"}`}
          href="/leads?grupo=WON"
        />
      </section>

      <section aria-label="Seguimiento" className="grid grid-cols-2 gap-3 lg:grid-cols-4 lg:gap-4">
        <StatCard icon={Wallet} tone="info" value={formatPrice(data.pipelineValue)} label="Valor del pipeline" hint="Presupuesto máximo en negociación u oferta aceptada" />
        <StatCard icon={CalendarCheck} value={data.pendingTasks} label="Actividades pendientes" hint={data.overdueTasks ? `${data.overdueTasks} vencidas sin resultado` : "Ninguna vencida"} href="/agenda" />
        <StatCard icon={AlertTriangle} tone="warning" value={data.staleLeads} label="Sin actividad reciente" hint={`Abiertos sin contacto en más de ${data.staleDays} días`} href="/leads?grupo=STALE" />
        <StatCard icon={Clock} tone="danger" value={data.atRisk.length} label="En riesgo de enfriarse" hint="Propuesta, negociación u oferta sin actividad" />
      </section>

      <Card>
        <CardHeader title="Embudo" description="Leads del periodo por etapa y tiempo promedio que pasan en cada una." />
        {data.total === 0 ? (
          <p className="py-6 text-center text-sm text-muted">Sin leads en el periodo</p>
        ) : (
          <ol className="grid grid-cols-2 gap-3 sm:grid-cols-5 xl:grid-cols-10">
            {stageOrder.map((stage) => {
              const count = data.byStage[stage] ?? 0;
              const time = data.stageTimes.find((t) => t.stage === stage);
              return (
                <li key={stage} className="flex min-w-0 flex-col rounded-xl border border-border p-3">
                  <span className="truncate text-xs text-muted" title={stageLabels[stage]}>
                    {stageLabels[stage]}
                  </span>
                  <span className="mt-1 text-2xl font-semibold tabular-nums text-obsessed">{count}</span>
                  <span className="mt-2 h-1.5 overflow-hidden rounded-full bg-app-bg">
                    <span
                      className={`block h-full rounded-full ${stage === "LOST" ? "bg-danger-text/60" : stage === "WON" || stage === "OFFER_ACCEPTED" ? "bg-success-text" : "bg-gold"}`}
                      style={{ width: `${(count / maxStage) * 100}%` }}
                    />
                  </span>
                  {time ? <span className="mt-1.5 text-[0.7rem] text-muted">{time.averageDays} días prom.</span> : null}
                </li>
              );
            })}
          </ol>
        )}
      </Card>

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
        <Card>
          <CardHeader title="¿De dónde llegan?" description="Leads del periodo por fuente, participación y conversión (oferta aceptada o cierre)." />
          {data.sources.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted">Sin leads en el periodo</p>
          ) : (
            <ul className="divide-y divide-border">
              {data.sources.map((s) => (
                <li key={s.code}>
                  <Link href={`/leads?fuente=${s.code}`} className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-3 py-2.5 hover:bg-app-bg/50">
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-medium text-obsessed">{s.label}</span>
                      <span className="text-xs text-muted">{sourceCategoryLabels[s.category]}</span>
                      <span className="mt-1 block h-1.5 overflow-hidden rounded-full bg-app-bg">
                        <span className="block h-full rounded-full bg-gold" style={{ width: `${s.share}%` }} />
                      </span>
                    </span>
                    <span className="text-right text-sm tabular-nums">
                      <strong className="font-semibold text-obsessed">{s.leads}</strong> <span className="text-muted">· {s.share}%</span>
                      <span className="block text-xs text-success-text">{s.conversionRate}% conv.</span>
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>
        <Card>
          <CardHeader title="Tendencia por fuente" description="Leads por mes de las fuentes con más registros (últimos 6 meses)." />
          <SourceTrendTable trend={data.sourceTrend} />
        </Card>
      </div>

      <Card>
        <CardHeader title="Productividad por asesor" description="Leads del periodo, actividades realizadas en el periodo y estado actual de su cartera." />
        <AdvisorProductivityTable rows={data.byAdvisor} />
      </Card>

      {data.atRisk.length ? (
        <Card>
          <CardHeader title="Leads en riesgo de enfriarse" description={`En propuesta, negociación u oferta aceptada, sin actividad en más de ${data.staleDays} días.`} />
          <ul className="divide-y divide-border">
            {data.atRisk.map((lead) => (
              <li key={lead.id}>
                <Link href={`/leads/${lead.id}`} className="grid grid-cols-1 gap-2 py-3 hover:bg-app-bg/50 sm:grid-cols-[minmax(0,1.3fr)_minmax(0,1fr)_auto] sm:items-center sm:gap-4">
                  <span className="truncate text-sm font-semibold text-obsessed">{lead.name}</span>
                  <AdvisorChip advisor={lead.advisor} />
                  <span className="flex items-center gap-3 sm:justify-end">
                    <StageBadge stage={lead.stage} />
                    <span className="w-28 text-right text-xs text-warning-text">{timeAgo(lead.lastActivityAt)}</span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </Card>
      ) : null}

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
                      <SourceLabel source={lead.source} label={lead.sourceLabel} detail={lead.sourceDetail} className="text-xs" />
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

const monthShort = ["ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"];

function SourceTrendTable({ trend }: { trend: CrmDashboard["sourceTrend"] }) {
  if (trend.series.length === 0) return <p className="py-6 text-center text-sm text-muted">Sin leads en los últimos 6 meses</p>;
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[420px] text-sm">
        <thead>
          <tr className="text-xs text-muted">
            <th className="py-1.5 pr-3 text-left font-medium">Fuente</th>
            {trend.months.map((m) => (
              <th key={m} className="px-1.5 py-1.5 text-right font-medium">
                {monthShort[Number(m.slice(5, 7)) - 1]}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {trend.series.map((s) => {
            const max = Math.max(1, ...s.counts);
            return (
              <tr key={s.code}>
                <td className="max-w-40 truncate py-2 pr-3 text-obsessed">{s.label}</td>
                {s.counts.map((c, i) => (
                  <td key={trend.months[i]} className="px-1.5 py-2 text-right tabular-nums">
                    <span className="rounded px-1.5 py-0.5" style={{ background: c ? `rgba(190, 175, 135, ${0.15 + (0.55 * c) / max})` : undefined }}>
                      {c}
                    </span>
                  </td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function AdvisorProductivityTable({ rows }: { rows: CrmDashboard["byAdvisor"] }) {
  if (rows.length === 0) return <p className="py-6 text-center text-sm text-muted">Sin asesores con leads</p>;
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[760px] text-sm">
        <thead>
          <tr className="border-b border-border text-xs text-muted">
            <th className="py-2 pr-3 text-left font-medium">Asesor</th>
            <th className="px-2 py-2 text-right font-medium">Leads del periodo</th>
            <th className="px-2 py-2 text-right font-medium">Conversión</th>
            <th className="px-2 py-2 text-right font-medium">Activos</th>
            <th className="px-2 py-2 text-right font-medium">Actividades hechas</th>
            <th className="px-2 py-2 text-right font-medium">Pendientes</th>
            <th className="px-2 py-2 text-right font-medium">Vencidas</th>
            <th className="px-2 py-2 text-right font-medium">Sin actividad</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-border">
          {rows.map((r) => (
            <tr key={r.advisorId}>
              <td className="py-2 pr-3">
                <Link href={`/leads?asesor=${r.advisorId}`} className="flex items-center gap-2 font-medium text-obsessed hover:text-dark-gold">
                  <Avatar name={r.name} className="h-7 w-7 text-[0.65rem]" /> {r.name}
                </Link>
              </td>
              <td className="px-2 py-2 text-right tabular-nums">{r.leads}</td>
              <td className="px-2 py-2 text-right tabular-nums">
                {r.conversionRate}% <span className="text-xs text-muted">({r.converted})</span>
              </td>
              <td className="px-2 py-2 text-right tabular-nums">{r.activeLeads}</td>
              <td className="px-2 py-2 text-right tabular-nums">{r.tasksDone}</td>
              <td className="px-2 py-2 text-right tabular-nums">{r.pendingTasks}</td>
              <td className={`px-2 py-2 text-right tabular-nums ${r.overdueTasks ? "font-semibold text-danger-text" : ""}`}>{r.overdueTasks}</td>
              <td className={`px-2 py-2 text-right tabular-nums ${r.staleLeads ? "font-semibold text-warning-text" : ""}`}>{r.staleLeads}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Pendientes de datos que conviene corregir (no bloquean nada; solo se muestran si hay alguno). */
function ReviewCard({ canSeeProperties }: { canSeeProperties: boolean }) {
  const properties = useApi(() => (canSeeProperties ? listCaptured() : Promise.resolve(null)), [canSeeProperties]);
  const advisors = useApi(() => listAdvisors(), []);
  const captured = properties.data?.captured ?? [];
  const routed = (advisors.data ?? []).filter((a) => a.receivesLeads);
  const rows = [
    { key: "sync", count: captured.filter((p) => p.syncStatus === "FAILED").length, label: "con error al sincronizar con 21Online", noun: "Propiedad", href: "/propiedades?vista=capturadas" },
    {
      key: "photos",
      count: captured.filter((p) => p.online21Id && p.data.status === "enPromocion" && (p.photos ?? []).length === 0).length,
      label: "en promoción sin fotos",
      noun: "Propiedad",
      href: "/propiedades?vista=capturadas",
    },
    { key: "link", count: routed.filter((a) => a.online21UserId === null).length, label: "sin usuario de 21Online vinculado", noun: "Asesor", href: "/asesores" },
    { key: "wa", count: routed.filter((a) => !a.whatsappPhone).length, label: "sin WhatsApp para recibir avisos de leads", noun: "Asesor", href: "/asesores" },
  ].filter((r) => r.count > 0);

  if (rows.length === 0) return null;
  const plural = (noun: string, n: number) => (n === 1 ? noun.toLowerCase() : noun === "Asesor" ? "asesores" : "propiedades");
  return (
    <Card>
      <CardHeader
        title={
          <span className="inline-flex items-center gap-2">
            <ClipboardCheck className="h-5 w-5 text-warning-text" aria-hidden /> Requiere revisión
          </span>
        }
        description="Datos incompletos o con error. No detienen nada, pero conviene corregirlos."
      />
      <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {rows.map((r) => (
          <li key={r.key}>
            <Link
              href={r.href}
              className="flex h-full items-start gap-3 rounded-xl border border-border p-3 transition-colors hover:border-gold/60 hover:bg-app-bg/50 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30"
            >
              <span className="text-2xl font-semibold tabular-nums leading-none text-warning-text">{r.count}</span>
              <span className="text-sm text-obsessed">
                {plural(r.noun, r.count)} {r.label}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </Card>
  );
}
