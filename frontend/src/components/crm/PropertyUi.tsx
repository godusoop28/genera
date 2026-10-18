"use client";

import { RequireAuth } from "@/components/auth/RequireAuth";
import { AppShell } from "@/components/layout/AppShell";
import { Badge } from "@/components/ui/Badge";
import { StateMessage } from "@/components/ui/StateMessage";
import type { ListingStatus, SyncStatus } from "@/lib/api/properties";
import { listingStatusLabels, listingStatusTones, syncLabels, syncTones } from "@/lib/crm/property-labels";
import { useCan } from "@/lib/permissions";
import { AlertTriangle, Bath, BedDouble, CheckCircle2, Clock, Home, Ruler } from "lucide-react";
import type { ReactNode } from "react";

/** Layout de Propiedades: sesión + menú + permiso PROPERTY_VIEW (el backend vuelve a validar). */
export function PropertyShell({ children }: { children: ReactNode }) {
  return (
    <RequireAuth>
      <AppShell>
        <PropertyGate>{children}</PropertyGate>
      </AppShell>
    </RequireAuth>
  );
}

function PropertyGate({ children }: { children: ReactNode }) {
  const can = useCan();
  if (!can("PROPERTY_VIEW")) {
    return (
      <div className="mx-auto w-full max-w-[1440px] px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
        <StateMessage kind="restricted" title="Acceso restringido" description="Tu usuario no tiene permiso para consultar propiedades." />
      </div>
    );
  }
  return <>{children}</>;
}

export function ListingStatusBadge({ status }: { status: ListingStatus | null }) {
  if (!status) return null;
  return <Badge tone={listingStatusTones[status] ?? "neutral"}>{listingStatusLabels[status] ?? status}</Badge>;
}

export function SyncBadge({ status }: { status: SyncStatus }) {
  const Icon = status === "DONE" ? CheckCircle2 : status === "FAILED" ? AlertTriangle : Clock;
  return (
    <Badge tone={syncTones[status]}>
      <Icon aria-hidden /> {syncLabels[status]}
    </Badge>
  );
}

/** Aviso cuando falta la llave de 21Online en el backend. */
export function NotConfiguredNotice() {
  return (
    <div role="status" className="mb-5 flex items-start gap-3 rounded-xl border border-warning-text/20 bg-warning-bg px-4 py-3 text-sm text-warning-text">
      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
      <p>
        La conexión con 21Online aún no está configurada (falta la llave <code className="font-mono text-xs">ONLINE21_API_KEY</code> en el servidor). Puedes
        capturar propiedades: quedan pendientes y se envían a 21Online en cuanto se configure.
      </p>
    </div>
  );
}

/** Recámaras, baños y metros en una línea. */
export function Features({ bedrooms, bathrooms, builtM2, landM2 }: { bedrooms: number | null; bathrooms: number | null; builtM2: number | null; landM2: number | null }) {
  const parts: Array<{ icon: typeof Home; text: string }> = [];
  if (bedrooms) parts.push({ icon: BedDouble, text: `${bedrooms} rec.` });
  if (bathrooms) parts.push({ icon: Bath, text: `${bathrooms} baños` });
  if (builtM2) parts.push({ icon: Home, text: `${builtM2} m² const.` });
  if (landM2) parts.push({ icon: Ruler, text: `${landM2} m² terreno` });
  if (parts.length === 0) return null;
  return (
    <ul className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-muted">
      {parts.map(({ icon: Icon, text }) => (
        <li key={text} className="inline-flex items-center gap-1">
          <Icon className="h-3.5 w-3.5" aria-hidden /> {text}
        </li>
      ))}
    </ul>
  );
}
