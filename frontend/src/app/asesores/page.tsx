"use client";

import { Avatar, PreviewNotice } from "@/components/crm/CrmUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { Badge } from "@/components/ui/Badge";
import { Card, CardHeader } from "@/components/ui/Card";
import { StateMessage } from "@/components/ui/StateMessage";
import { Toggle } from "@/components/ui/Toggle";
import { openStages } from "@/lib/crm/labels";
import { toggleAdvisor, useCrm, type CrmState } from "@/lib/crm/store";
import { cn } from "@/lib/utils";
import type { CrmAdvisor } from "@/types/crm";
import { Clock, Home, Minus, Plus, Shuffle } from "lucide-react";
import Link from "next/link";

export default function AsesoresPage() {
  const crm = useCrm();
  return (
    <PageContainer title="Asesores" subtitle="Quién recibe leads, con qué peso y en qué horario.">
      <PreviewNotice>Los asesores salen de 21Online. Los pesos, límites y horarios son de ejemplo y los cambios se pierden al recargar.</PreviewNotice>
      {crm ? <AdvisorsContent crm={crm} /> : <StateMessage kind="loading" title="Cargando asesores…" />}
    </PageContainer>
  );
}

function AdvisorsContent({ crm }: { crm: CrmState }) {
  const startOfDay = new Date();
  startOfDay.setHours(0, 0, 0, 0);
  const stats = (a: CrmAdvisor) => {
    const mine = crm.leads.filter((l) => l.advisorId === a.id);
    return {
      today: mine.filter((l) => new Date(l.createdAt) >= startOfDay).length,
      open: mine.filter((l) => openStages.includes(l.stage)).length,
      won: mine.filter((l) => l.stage === "WON").length,
      properties: crm.properties.filter((p) => p.advisorId === a.id).length,
    };
  };
  const activeWeight = crm.advisors.filter((a) => a.active && a.weight > 0).reduce((s, a) => s + a.weight, 0);

  return (
    <div className="space-y-5">
      <Card>
        <CardHeader title="¿Cómo se asigna un lead?" />
        <ol className="grid grid-cols-1 gap-4 text-sm md:grid-cols-2">
          <li className="flex gap-3">
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gold/20 text-dark-gold">
              <Home className="h-4 w-4" aria-hidden />
            </span>
            <span>
              <strong className="font-semibold text-obsessed">Si pregunta por una propiedad</strong>
              <span className="block text-muted">va directo al asesor que la tiene en 21Online.</span>
            </span>
          </li>
          <li className="flex gap-3">
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gold/20 text-dark-gold">
              <Shuffle className="h-4 w-4" aria-hidden />
            </span>
            <span>
              <strong className="font-semibold text-obsessed">Si solo explora o la propiedad no tiene asesor</strong>
              <span className="block text-muted">entra a la ruleta: se reparte entre los asesores activos, en su horario y sin rebasar su límite diario, en proporción a su peso.</span>
            </span>
          </li>
        </ol>
      </Card>

      <div className="overflow-hidden rounded-xl border border-border bg-card shadow-[0_1px_2px_rgba(36,39,35,0.04)]">
        <div className="hidden grid-cols-[minmax(0,2fr)_minmax(0,1.2fr)_minmax(0,1fr)_minmax(0,1.6fr)_auto] gap-4 border-b border-border bg-app-bg px-5 py-3 text-xs font-medium uppercase tracking-wide text-muted lg:grid">
          <span>Asesor</span>
          <span>Peso en la ruleta</span>
          <span>Límite diario</span>
          <span>Leads</span>
          <span>Recibe leads</span>
        </div>
        <ul className="divide-y divide-border">
          {crm.advisors.map((a) => {
            const s = stats(a);
            const share = a.active && activeWeight ? Math.round((a.weight / activeWeight) * 100) : 0;
            return (
              <li
                key={a.id}
                className={cn(
                  "grid grid-cols-1 gap-3 px-4 py-4 sm:px-5 lg:grid-cols-[minmax(0,2fr)_minmax(0,1.2fr)_minmax(0,1fr)_minmax(0,1.6fr)_auto] lg:items-center lg:gap-4",
                  !a.active && "opacity-60",
                )}
              >
                <div className="flex min-w-0 items-center gap-3">
                  <Avatar name={a.name} />
                  <div className="min-w-0">
                    <p className="truncate font-semibold text-obsessed">{a.name}</p>
                    <p className="flex items-center gap-1 text-xs text-muted">
                      <Clock className="h-3 w-3" aria-hidden /> {a.shift ?? "Sin horario"} · {s.properties} propiedad{s.properties === 1 ? "" : "es"}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <Stepper
                    value={a.weight}
                    label={`Peso de ${a.name}`}
                    onChange={(weight) => toggleAdvisor(a.id, { weight })}
                    min={0}
                    max={10}
                  />
                  <span className="text-xs text-muted">{a.active ? `≈${share}% de la ruleta` : "—"}</span>
                </div>
                <div className="text-sm text-obsessed">
                  <span className="lg:sr-only">Límite diario: </span>
                  {a.dailyLimit ? `${s.today} de ${a.dailyLimit} hoy` : `${s.today} hoy · sin límite`}
                </div>
                <div className="flex flex-wrap gap-1.5">
                  <Link href={`/leads?asesor=${a.id}`} className="inline-flex">
                    <Badge tone="info">{s.open} en seguimiento</Badge>
                  </Link>
                  {s.won ? <Badge tone="success">{s.won} ganados</Badge> : null}
                </div>
                <div className="lg:w-28">
                  <Toggle id={`active-${a.id}`} checked={a.active} onChange={(active) => toggleAdvisor(a.id, { active })} label={a.active ? "Activo" : "En pausa"} />
                </div>
              </li>
            );
          })}
        </ul>
      </div>
    </div>
  );
}

function Stepper({ value, onChange, min, max, label }: { value: number; onChange: (v: number) => void; min: number; max: number; label: string }) {
  return (
    <div className="inline-flex items-center rounded-xl border border-border" role="group" aria-label={label}>
      <button
        type="button"
        onClick={() => onChange(Math.max(min, value - 1))}
        disabled={value <= min}
        aria-label="Bajar peso"
        className="flex h-9 w-9 items-center justify-center rounded-l-xl text-muted hover:bg-app-bg hover:text-obsessed disabled:opacity-40"
      >
        <Minus className="h-3.5 w-3.5" aria-hidden />
      </button>
      <span className="w-8 text-center text-sm font-semibold tabular-nums text-obsessed" aria-live="polite">
        {value}
      </span>
      <button
        type="button"
        onClick={() => onChange(Math.min(max, value + 1))}
        disabled={value >= max}
        aria-label="Subir peso"
        className="flex h-9 w-9 items-center justify-center rounded-r-xl text-muted hover:bg-app-bg hover:text-obsessed disabled:opacity-40"
      >
        <Plus className="h-3.5 w-3.5" aria-hidden />
      </button>
    </div>
  );
}
