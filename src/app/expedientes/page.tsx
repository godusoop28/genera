"use client";

import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/Button";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { useDemoApp } from "@/context/DemoAppProvider";
import { cn, initials } from "@/lib/utils";
import type { ExpedienteStatus } from "@/types/expediente";
import { ArrowUpRight, Calendar, Home, Plus } from "lucide-react";
import Link from "next/link";
import { useMemo, useState } from "react";

const filters = [
  { id: "todos", label: "Todos" },
  { id: "pendientes", label: "Pendientes" },
  { id: "revision", label: "En revisión" },
  { id: "completos", label: "Completos" },
] as const;

type FilterId = (typeof filters)[number]["id"];

function matchesFilter(status: ExpedienteStatus, filter: FilterId): boolean {
  if (filter === "todos") return true;
  if (filter === "pendientes") return ["draft", "waiting_privacy", "waiting_documents"].includes(status);
  if (filter === "revision") return ["documents_received", "under_review", "corrections_requested"].includes(status);
  if (filter === "completos")
    return ["documents_approved", "contract_preparation", "ready_for_signature", "property_accepted", "closed"].includes(
      status,
    );
  return true;
}

const statusAvatarTone: Record<ExpedienteStatus, string> = {
  draft: "bg-app-bg text-muted border border-border",
  waiting_privacy: "bg-warning-bg text-warning-text",
  waiting_documents: "bg-warning-bg text-warning-text",
  documents_received: "bg-info-bg text-info-text",
  under_review: "bg-gold/15 text-dark-gold",
  corrections_requested: "bg-warning-bg text-warning-text",
  documents_approved: "bg-success-bg text-success-text",
  contract_preparation: "bg-info-bg text-info-text",
  ready_for_signature: "bg-gold/15 text-dark-gold",
  property_accepted: "bg-success-bg text-success-text",
  property_rejected: "bg-danger-bg text-danger-text",
  closed: "bg-obsessed/10 text-obsessed",
};

export default function ExpedientesPage() {
  const { expedientes } = useDemoApp();
  const [filter, setFilter] = useState<FilterId>("todos");

  const filtered = useMemo(
    () => expedientes.filter((e) => matchesFilter(e.status, filter)),
    [expedientes, filter],
  );

  return (
    <PageContainer
      title="Expedientes"
      subtitle="Consulta y administra los expedientes documentales."
      action={
        <Link href="/expedientes/nuevo">
          <Button size="md">
            <Plus className="h-4 w-4" aria-hidden />
            Nuevo expediente
          </Button>
        </Link>
      }
    >
      <div className="mb-6 flex flex-wrap gap-2">
        {filters.map((f) => (
          <button
            key={f.id}
            onClick={() => setFilter(f.id)}
            className={cn(
              "rounded-full border px-3.5 py-1.5 text-sm font-medium transition-colors",
              filter === f.id
                ? "border-gold bg-gold/15 text-dark-gold"
                : "border-border bg-white text-muted hover:text-obsessed",
            )}
          >
            {f.label}
          </button>
        ))}
      </div>

      {filtered.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-border bg-white p-10 text-center text-sm text-muted">
          No hay expedientes en este filtro.
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3">
          {filtered.map((expediente) => (
            <Link
              key={expediente.id}
              href={`/expedientes/${expediente.id}`}
              className="group relative flex flex-col overflow-hidden rounded-2xl border border-border bg-white shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lg hover:shadow-obsessed/5"
            >
              <span className="absolute inset-y-0 left-0 w-1 bg-gold" aria-hidden />

              <div className="flex flex-1 flex-col gap-4 p-5 pl-6">
                <div className="flex items-start justify-between gap-3">
                  <span className="rounded-lg bg-app-bg px-2.5 py-1 font-mono text-xs font-medium tracking-wide text-obsessed/70">
                    {expediente.folio}
                  </span>
                  <StatusBadge status={expediente.status} />
                </div>

                <div className="flex items-center gap-3">
                  <div
                    className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-sm font-semibold ${statusAvatarTone[expediente.status]}`}
                  >
                    {initials(expediente.ownerName)}
                  </div>
                  <div className="min-w-0">
                    <p className="truncate text-base font-semibold text-obsessed">{expediente.ownerName}</p>
                    <p className="mt-0.5 flex items-center gap-1.5 text-xs text-muted">
                      <Home className="h-3.5 w-3.5" aria-hidden />
                      Intermediación exclusiva
                    </p>
                  </div>
                </div>

                <div className="mt-auto flex items-center justify-between border-t border-border pt-4">
                  <span className="flex items-center gap-1.5 text-xs text-muted">
                    <Calendar className="h-3.5 w-3.5" aria-hidden />
                    {new Date(expediente.updatedAt).toLocaleDateString("es-MX")}
                  </span>
                  <span className="inline-flex items-center gap-1 text-sm font-medium text-dark-gold">
                    Ver expediente
                    <ArrowUpRight
                      className="h-3.5 w-3.5 transition-transform duration-200 group-hover:translate-x-0.5 group-hover:-translate-y-0.5"
                      aria-hidden
                    />
                  </span>
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </PageContainer>
  );
}
