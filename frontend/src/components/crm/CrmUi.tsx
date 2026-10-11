"use client";

import { Badge } from "@/components/ui/Badge";
import { StateMessage } from "@/components/ui/StateMessage";
import { useAuth } from "@/context/AuthProvider";
import { propertyAddress, propertyPrice, sourceLabels, stageLabels, stageTones } from "@/lib/crm/labels";
import { cn, initials } from "@/lib/utils";
import type { CrmAdvisor, CrmProperty, LeadSource, LeadStage } from "@/types/crm";
import { Building2, Footprints, Globe, Home, Info, Megaphone, MessageCircle, UserPlus, type LucideIcon } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";

/** Roles que ven el CRM mientras es vista previa (el backend aún no tiene permisos de leads). */
const CRM_ROLES = ["ADMINISTRATOR", "DIRECTOR", "ADMIN"];

export function useCanSeeCrm(): boolean {
  const { user } = useAuth();
  return !!user && (CRM_ROLES.includes(user.role) || user.permissions.includes("*"));
}

/** Oculta las pantallas del CRM a quien no debe verlas todavía. */
export function CrmGate({ children }: { children: ReactNode }) {
  const allowed = useCanSeeCrm();
  if (!allowed) {
    return (
      <div className="mx-auto w-full max-w-[1440px] px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
        <StateMessage kind="restricted" title="Acceso restringido" description="El CRM está en vista previa y por ahora solo lo ven administración y dirección." />
      </div>
    );
  }
  return <>{children}</>;
}

export function PreviewNotice({ children }: { children?: ReactNode }) {
  return (
    <p className="mb-5 flex items-start gap-2.5 rounded-xl border border-info-text/20 bg-info-bg px-4 py-3 text-sm text-info-text">
      <Info className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
      <span>
        <strong className="font-semibold">Vista previa con datos de ejemplo.</strong>{" "}
        {children ?? "Los leads son ficticios y las propiedades son una muestra de 21Online. El bot de WhatsApp y la sincronización aún no están conectados; los cambios se pierden al recargar."}
      </span>
    </p>
  );
}

export function StageBadge({ stage }: { stage: LeadStage }) {
  return <Badge tone={stageTones[stage]}>{stageLabels[stage]}</Badge>;
}

export const sourceIcons: Record<LeadSource, LucideIcon> = {
  WHATSAPP_BOT: MessageCircle,
  PORTAL: Building2,
  CAMPAIGN: Megaphone,
  WEBSITE: Globe,
  REFERRAL: UserPlus,
  WALK_IN: Footprints,
};

export function SourceLabel({ source, detail, className }: { source: LeadSource; detail?: string; className?: string }) {
  const Icon = sourceIcons[source];
  return (
    <span className={cn("inline-flex min-w-0 items-center gap-1.5 text-sm text-muted", className)} title={detail ? `${sourceLabels[source]} · ${detail}` : sourceLabels[source]}>
      <Icon className="h-3.5 w-3.5 shrink-0" aria-hidden />
      <span className="truncate">{detail ?? sourceLabels[source]}</span>
    </span>
  );
}

export function Avatar({ name, className }: { name: string; className?: string }) {
  return (
    <span className={cn("flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gold/25 text-xs font-semibold text-dark-gold", className)} aria-hidden>
      {initials(name)}
    </span>
  );
}

export function AdvisorChip({ advisor, emptyLabel = "Sin asignar" }: { advisor: CrmAdvisor | undefined; emptyLabel?: string }) {
  if (!advisor) {
    return (
      <Badge tone="warning">
        <UserPlus aria-hidden /> {emptyLabel}
      </Badge>
    );
  }
  return (
    <span className="flex min-w-0 items-center gap-2">
      <Avatar name={advisor.name} className="h-7 w-7 text-[0.65rem]" />
      <span className="truncate text-sm text-obsessed">{advisor.name}</span>
    </span>
  );
}

export function PropertyMini({ property, emptyLabel = "Sin propiedad identificada" }: { property: CrmProperty | undefined; emptyLabel?: string }) {
  if (!property) return <span className="text-sm text-muted">{emptyLabel}</span>;
  return (
    <Link
      href={`/propiedades/${property.id}`}
      className="group flex min-w-0 items-start gap-2 rounded-lg focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30"
      title={propertyAddress(property)}
    >
      <Home className="mt-0.5 h-4 w-4 shrink-0 text-muted" aria-hidden />
      <span className="min-w-0">
        <span className="block truncate text-sm font-medium text-obsessed group-hover:text-dark-gold">
          {property.internalKey} · {property.neighborhood}
        </span>
        <span className="block truncate text-xs text-muted">
          {property.subtype} en {property.operation} · {propertyPrice(property)}
        </span>
      </span>
    </Link>
  );
}

const statTone = {
  neutral: "bg-gold/20 text-dark-gold",
  warning: "bg-warning-bg text-warning-text",
  info: "bg-info-bg text-info-text",
  success: "bg-success-bg text-success-text",
  danger: "bg-danger-bg text-danger-text",
} as const;

export function StatCard({
  icon: Icon,
  tone = "neutral",
  value,
  label,
  hint,
  href,
}: {
  icon: LucideIcon;
  tone?: keyof typeof statTone;
  value: ReactNode;
  label: string;
  hint?: ReactNode;
  href?: string;
}) {
  const body = (
    <>
      <span className={cn("flex h-11 w-11 shrink-0 items-center justify-center rounded-full sm:h-12 sm:w-12", statTone[tone])}>
        <Icon className="h-5 w-5" aria-hidden />
      </span>
      <div className="min-w-0">
        <p className="text-2xl font-semibold tabular-nums leading-none text-obsessed">{value}</p>
        <p className="mt-1 text-sm font-medium text-obsessed">{label}</p>
        {hint ? <p className="hidden text-xs text-muted sm:block">{hint}</p> : null}
      </div>
    </>
  );
  const className =
    "flex min-w-0 items-center gap-3 rounded-xl border border-border bg-card p-4 shadow-[0_1px_2px_rgba(36,39,35,0.04)] sm:gap-4 sm:p-5";
  return href ? (
    <Link href={href} className={cn(className, "transition-shadow duration-150 hover:shadow-md focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30")}>
      {body}
    </Link>
  ) : (
    <div className={className}>{body}</div>
  );
}

export interface BarRow {
  key: string;
  label: ReactNode;
  count: number;
  leading?: ReactNode;
  href?: string;
}

/** Lista de barras horizontales: el ancho es proporcional al máximo de la lista. */
export function BarList({ rows, emptyLabel = "Sin datos en el periodo" }: { rows: BarRow[]; emptyLabel?: string }) {
  const total = rows.reduce((sum, r) => sum + r.count, 0);
  const max = Math.max(1, ...rows.map((r) => r.count));
  if (rows.length === 0 || total === 0) return <p className="py-6 text-center text-sm text-muted">{emptyLabel}</p>;
  return (
    <ul className="space-y-3">
      {rows.map((row) => {
        const pct = Math.round((row.count / total) * 100);
        const content = (
          <>
            <div className="flex items-center justify-between gap-3 text-sm">
              <span className="flex min-w-0 items-center gap-2 text-obsessed">
                {row.leading}
                <span className="truncate">{row.label}</span>
              </span>
              <span className="shrink-0 tabular-nums text-muted">
                <strong className="font-semibold text-obsessed">{row.count}</strong> · {pct}%
              </span>
            </div>
            <div className="mt-1.5 h-2 overflow-hidden rounded-full bg-app-bg">
              <div className="h-full rounded-full bg-gold" style={{ width: `${(row.count / max) * 100}%` }} />
            </div>
          </>
        );
        return (
          <li key={row.key}>
            {row.href ? (
              <Link href={row.href} className="block rounded-lg hover:opacity-80 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30">
                {content}
              </Link>
            ) : (
              content
            )}
          </li>
        );
      })}
    </ul>
  );
}

export type Period = "today" | "7d" | "30d" | "all";

export const periodLabels: Record<Period, string> = { today: "Hoy", "7d": "7 días", "30d": "30 días", all: "Todo" };

export function periodStart(period: Period, now = Date.now()): number {
  if (period === "all") return 0;
  if (period === "today") {
    const d = new Date(now);
    d.setHours(0, 0, 0, 0);
    return d.getTime();
  }
  return now - (period === "7d" ? 7 : 30) * 86_400_000;
}

export function Segmented<T extends string>({
  value,
  onChange,
  options,
  label,
}: {
  value: T;
  onChange: (v: T) => void;
  options: ReadonlyArray<{ id: T; label: string; count?: number }>;
  label: string;
}) {
  return (
    <div className="flex flex-wrap items-center gap-2" role="group" aria-label={label}>
      {options.map((o) => {
        const active = value === o.id;
        return (
          <button
            key={o.id}
            type="button"
            onClick={() => onChange(o.id)}
            aria-pressed={active}
            className={cn(
              "inline-flex min-h-10 items-center gap-2 rounded-full border px-4 text-sm font-medium transition-colors duration-150 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30",
              active ? "border-gold bg-gold text-brand-ink" : "border-border bg-card text-muted hover:border-gold/60 hover:text-obsessed",
            )}
          >
            {o.label}
            {o.count !== undefined ? <span className={cn("rounded-full px-1.5 text-xs tabular-nums", active ? "bg-white/40" : "bg-app-bg")}>{o.count}</span> : null}
          </button>
        );
      })}
    </div>
  );
}
