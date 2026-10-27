"use client";

import { LoadError, Segmented, StatCard, useApi } from "@/components/crm/CrmUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { NpsConfigPanel } from "@/components/surveys/NpsConfigPanel";
import { ScoreBadge, categoryDots, categoryLabels, channelShort, npsTone } from "@/components/surveys/SurveyUi";
import { Badge } from "@/components/ui/Badge";
import { Card, CardHeader } from "@/components/ui/Card";
import { Select } from "@/components/ui/Select";
import { StateMessage } from "@/components/ui/StateMessage";
import { getNpsDashboard, type NpsCategory, type NpsDashboard, type NpsScore, type Touchpoint } from "@/lib/api/surveys";
import { useCan } from "@/lib/permissions";
import { Gauge, MessageSquareText, Send, Users } from "lucide-react";
import Link from "next/link";
import { useState } from "react";

type Range = "30d" | "90d" | "12m";
const rangeLabels: Record<Range, string> = { "30d": "30 días", "90d": "90 días", "12m": "12 meses" };
const rangeDays: Record<Range, number> = { "30d": 30, "90d": 90, "12m": 365 };

/**
 * Control de Cambios #28, parte 5: dashboard de satisfacción (NPS global, por
 * punto de contacto, por asesor, tendencia y comentarios) y configuración de
 * las encuestas por punto de contacto.
 */
export default function NpsPage() {
  const can = useCan();
  const canManage = can("NPS_MANAGE");
  const [tab, setTab] = useState<"dashboard" | "config">("dashboard");
  return (
    <PageContainer
      title="Satisfacción del cliente (NPS)"
      subtitle="¿Qué tan probable es que nos recomienden? Resultados por etapa del proceso, por asesor y en el tiempo."
      action={
        canManage ? (
          <Segmented
            label="Sección"
            value={tab}
            onChange={setTab}
            options={[
              { id: "dashboard", label: "Resultados" },
              { id: "config", label: "Configuración" },
            ]}
          />
        ) : null
      }
    >
      {tab === "config" && canManage ? <NpsConfigPanel /> : <NpsResults seesAll={can("NPS_VIEW")} />}
    </PageContainer>
  );
}

function NpsResults({ seesAll }: { seesAll: boolean }) {
  const [range, setRange] = useState<Range>("90d");
  const [from, setFrom] = useState(() => new Date(Date.now() - rangeDays["90d"] * 86_400_000).toISOString());
  const [advisorId, setAdvisorId] = useState("");
  const [touchpoint, setTouchpoint] = useState<Touchpoint | "">("");
  const [category, setCategory] = useState<NpsCategory | "">("");
  const data = useApi(() => getNpsDashboard({ from, advisorId, touchpoint, category }), [from, advisorId, touchpoint, category]);

  if (data.error && !data.data) {
    return data.error.includes("permiso") || data.error.includes("dirección") ? (
      <StateMessage kind="restricted" title="Acceso restringido" description="Los resultados de satisfacción solo los consulta dirección." />
    ) : (
      <LoadError message={data.error} onRetry={data.reload} />
    );
  }
  if (!data.data) return <StateMessage kind="loading" title="Cargando resultados…" />;
  const d = data.data;

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <Segmented
          label="Periodo"
          value={range}
          onChange={(r) => {
            setRange(r);
            setFrom(new Date(Date.now() - rangeDays[r] * 86_400_000).toISOString());
          }}
          options={(Object.keys(rangeLabels) as Range[]).map((id) => ({ id, label: rangeLabels[id] }))}
        />
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:w-[34rem]">
          {seesAll ? (
            <Select
              aria-label="Filtrar por asesor"
              value={advisorId}
              onChange={(e) => setAdvisorId(e.target.value)}
              options={[{ value: "", label: "Todos los asesores" }, ...d.byAdvisor.filter((a) => a.advisorId).map((a) => ({ value: a.advisorId as string, label: a.name }))]}
            />
          ) : null}
          <Select
            aria-label="Filtrar por punto de contacto"
            value={touchpoint}
            onChange={(e) => setTouchpoint(e.target.value as Touchpoint | "")}
            options={[{ value: "", label: "Todos los puntos de contacto" }, ...d.byTouchpoint.map((t) => ({ value: t.touchpoint, label: `${t.number}. ${t.label}` }))]}
          />
        </div>
      </div>
      {!seesAll ? <p className="text-sm text-muted">Estás viendo el NPS de los clientes que atendiste.</p> : null}

      <section aria-label="Indicadores" className="grid grid-cols-2 gap-3 lg:grid-cols-4 lg:gap-4">
        <div className="col-span-2 flex flex-col justify-center rounded-xl border border-border bg-card p-4 lg:col-span-1">
          <span className="flex items-center gap-1.5 text-xs text-muted">
            <Gauge className="h-4 w-4" aria-hidden /> NPS global
          </span>
          <span className={`mt-1 text-4xl font-semibold tabular-nums ${d.global.responses ? npsTone(d.global.nps) : "text-muted"}`}>{d.global.responses ? d.global.nps : "—"}</span>
          <span className="text-xs text-muted">% promotores − % detractores</span>
        </div>
        <StatCard icon={MessageSquareText} value={d.global.responses} label="Respuestas" hint={`${d.responseRate}% de las enviadas`} />
        <StatCard icon={Send} tone="info" value={d.sent} label="Encuestas enviadas" hint="Sin contar QR fijos" />
        <StatCard icon={Users} tone="danger" value={d.global.detractors} label="Detractores" hint="Calificaron de 0 a 6" />
      </section>

      <Card>
        <CardHeader title="Distribución" description="Promotores (9–10), pasivos (7–8) y detractores (0–6)." />
        <Distribution score={d.global} />
      </Card>

      <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
        <Card>
          <CardHeader title="Tendencia" description={d.trendGranularity === "WEEK" ? "NPS por semana." : "NPS por mes."} />
          <Trend trend={d.trend} />
        </Card>
        <Card>
          <CardHeader title="Por asesor" description="Satisfacción de los clientes que atendió cada asesor." />
          {d.byAdvisor.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted">Sin respuestas en el periodo</p>
          ) : (
            <ul className="divide-y divide-border">
              {d.byAdvisor.map((a) => (
                <li key={a.advisorId ?? a.name} className="flex items-center gap-3 py-2.5 text-sm">
                  <span className="min-w-0 flex-1 truncate font-medium text-obsessed">{a.name}</span>
                  <span className="text-xs text-muted">{a.score.responses} resp.</span>
                  <span className={`w-14 text-right text-lg font-semibold tabular-nums ${npsTone(a.score.nps)}`}>{a.score.nps}</span>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      <Card>
        <CardHeader title="Por punto de contacto" description="Qué etapa del proceso tiene mayor o menor satisfacción." />
        <div className="overflow-x-auto">
          <table className="w-full min-w-[720px] text-sm">
            <thead>
              <tr className="border-b border-border text-xs text-muted">
                <th className="py-2 pr-3 text-left font-medium">Punto de contacto</th>
                <th className="px-2 py-2 text-left font-medium">Tipo</th>
                <th className="px-2 py-2 text-right font-medium">Enviadas</th>
                <th className="px-2 py-2 text-right font-medium">Respuestas</th>
                <th className="w-48 px-2 py-2 text-left font-medium">Distribución</th>
                <th className="px-2 py-2 text-right font-medium">NPS</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {d.byTouchpoint.map((t) => (
                <tr key={t.touchpoint}>
                  <td className="py-2 pr-3 text-obsessed">
                    {t.number}. {t.label}
                  </td>
                  <td className="px-2 py-2">
                    <Badge tone={t.systemTriggered ? "info" : "manual"}>{t.systemTriggered ? "Sistema" : "Asesor"}</Badge>
                  </td>
                  <td className="px-2 py-2 text-right tabular-nums">{t.sent}</td>
                  <td className="px-2 py-2 text-right tabular-nums">{t.score.responses}</td>
                  <td className="px-2 py-2">{t.score.responses ? <Distribution score={t.score} compact /> : <span className="text-xs text-muted">—</span>}</td>
                  <td className={`px-2 py-2 text-right text-base font-semibold tabular-nums ${t.score.responses ? npsTone(t.score.nps) : "text-muted"}`}>{t.score.responses ? t.score.nps : "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      <Card>
        <CardHeader
          title="Comentarios abiertos"
          description="Respuestas de texto libre (las más recientes primero)."
          action={
            <Select
              aria-label="Filtrar comentarios por calificación"
              value={category}
              onChange={(e) => setCategory(e.target.value as NpsCategory | "")}
              options={[{ value: "", label: "Todas las calificaciones" }, ...(["DETRACTOR", "PASSIVE", "PROMOTER"] as NpsCategory[]).map((c) => ({ value: c, label: `${categoryDots[c]} ${categoryLabels[c]}` }))]}
            />
          }
        />
        {d.comments.length === 0 ? (
          <p className="py-6 text-center text-sm text-muted">Sin comentarios con estos filtros</p>
        ) : (
          <ul className="space-y-2">
            {d.comments.map((c) => (
              <li key={c.surveyId} className="rounded-xl border border-border p-3 text-sm">
                <div className="flex flex-wrap items-center gap-2">
                  <ScoreBadge score={c.score} category={c.category} />
                  <span className="text-xs text-muted">
                    {c.touchpointLabel} · {channelShort[c.channel]}
                    {c.advisorName ? ` · ${c.advisorName}` : ""}
                  </span>
                  <span className="ml-auto text-xs text-muted">{new Date(c.answeredAt).toLocaleDateString("es-MX", { day: "numeric", month: "short", year: "numeric" })}</span>
                </div>
                <p className="mt-1.5 whitespace-pre-wrap text-obsessed">“{c.comment}”</p>
                <p className="mt-1 text-xs text-muted">
                  {c.clientName ?? "Cliente"}
                  {c.expedienteId ? (
                    <>
                      {" · "}
                      <Link href={`/expedientes/${c.expedienteId}`} className="font-medium text-dark-gold hover:text-obsessed">
                        {c.expedienteFolio}
                      </Link>
                    </>
                  ) : c.leadId ? (
                    <>
                      {" · "}
                      <Link href={`/leads/${c.leadId}`} className="font-medium text-dark-gold hover:text-obsessed">
                        Ver lead
                      </Link>
                    </>
                  ) : null}
                </p>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}

function Distribution({ score, compact }: { score: NpsScore; compact?: boolean }) {
  if (score.responses === 0) return <p className="text-sm text-muted">Sin respuestas en el periodo</p>;
  const pct = (n: number) => (n * 100) / score.responses;
  const parts = [
    { key: "p", n: score.promoters, cls: "bg-success-text", label: "Promotores" },
    { key: "s", n: score.passives, cls: "bg-warning-text", label: "Pasivos" },
    { key: "d", n: score.detractors, cls: "bg-danger-text", label: "Detractores" },
  ];
  return (
    <div>
      <div className={`flex overflow-hidden rounded-full bg-app-bg ${compact ? "h-2" : "h-4"}`} role="img" aria-label={parts.map((p) => `${p.label}: ${p.n}`).join(", ")}>
        {parts.map((p) => (p.n ? <span key={p.key} className={p.cls} style={{ width: `${pct(p.n)}%` }} /> : null))}
      </div>
      {!compact ? (
        <div className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-sm">
          {parts.map((p) => (
            <span key={p.key} className="inline-flex items-center gap-1.5">
              <span className={`h-2.5 w-2.5 rounded-full ${p.cls}`} aria-hidden />
              {p.label}: <strong className="font-semibold tabular-nums">{p.n}</strong> <span className="text-muted">({Math.round(pct(p.n))}%)</span>
            </span>
          ))}
        </div>
      ) : null}
    </div>
  );
}

/** Barras de NPS por periodo (escala −100 a 100, la línea central es 0). */
function Trend({ trend }: { trend: NpsDashboard["trend"] }) {
  if (trend.every((t) => t.score.responses === 0)) return <p className="py-6 text-center text-sm text-muted">Sin respuestas en el periodo</p>;
  return (
    <div className="overflow-x-auto">
      <div className="flex h-48 min-w-max items-stretch gap-1.5">
        {trend.map((t) => {
          const v = t.score.nps;
          const has = t.score.responses > 0;
          return (
            <div key={t.period} className="flex w-9 flex-col items-center" title={has ? `${t.label}: NPS ${v} (${t.score.responses} resp.)` : `${t.label}: sin respuestas`}>
              <div className="relative w-full flex-1">
                <span className="absolute inset-x-0 top-1/2 border-t border-border" aria-hidden />
                {has ? (
                  <span
                    className={`absolute inset-x-1 rounded ${v >= 0 ? "bg-success-text/80" : "bg-danger-text/80"}`}
                    style={v >= 0 ? { bottom: "50%", height: `${Math.max(2, v / 2)}%` } : { top: "50%", height: `${Math.max(2, -v / 2)}%` }}
                  />
                ) : null}
              </div>
              <span className="mt-1 text-[0.65rem] tabular-nums text-obsessed">{has ? v : ""}</span>
              <span className="whitespace-nowrap text-[0.6rem] text-muted">{t.label}</span>
            </div>
          );
        })}
      </div>
    </div>
  );
}

