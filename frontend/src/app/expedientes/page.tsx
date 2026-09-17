"use client";

import { Badge } from "@/components/ui/Badge";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/Button";
import { listExpedientes } from "@/lib/api/expedientes";
import { ApiError } from "@/lib/api/client";
import { backendStatusLabels, backendStatusTone } from "@/lib/api/status-labels";
import type { BackendExpedienteStatus, ExpedienteResponse } from "@/lib/api/types";
import { cn, initials } from "@/lib/utils";
import { ArrowUpRight, Calendar, Home, Plus, RefreshCw } from "lucide-react";
import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";

const filters = [
  { id: "todos", label: "Todos" },
  { id: "pendientes", label: "Pendientes" },
  { id: "revision", label: "En revisión" },
  { id: "completos", label: "Completos" },
] as const;

type FilterId = (typeof filters)[number]["id"];

function matchesFilter(status: BackendExpedienteStatus, filter: FilterId): boolean {
  if (filter === "todos") return true;
  if (filter === "pendientes") return ["DRAFT", "WAITING_PRIVACY", "WAITING_DOCUMENTS"].includes(status);
  if (filter === "revision")
    return ["DOCUMENTS_RECEIVED", "UNDER_REVIEW", "CORRECTIONS_REQUESTED"].includes(status);
  if (filter === "completos")
    return [
      "DOCUMENTS_APPROVED",
      "RECEPTION_SIGNED",
      "CONTRACT_PREPARATION",
      "READY_FOR_SIGNATURE",
      "PROPERTY_ACCEPTED",
      "CLOSED",
    ].includes(status);
  return true;
}

export default function ExpedientesPage() {
  const [filter, setFilter] = useState<FilterId>("todos");
  const [expedientes, setExpedientes] = useState<ExpedienteResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    listExpedientes()
      .then((page) => {
        setExpedientes(page.items);
        setError(null);
      })
      .catch((err) => {
        setError(
          err instanceof ApiError
            ? `No se pudo cargar la lista (${err.status}): ${err.message}`
            : "No se pudo conectar con el backend en localhost:8080.",
        );
      });
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const filtered = useMemo(
    () => (expedientes ?? []).filter((e) => matchesFilter(e.status, filter)),
    [expedientes, filter],
  );

  return (
    <PageContainer
      title="Expedientes"
      subtitle="Consulta y administra los expedientes documentales (datos reales del backend)."
      action={
        <div className="flex items-center gap-2">
          <Button variant="secondary" size="md" onClick={load}>
            <RefreshCw className="h-4 w-4" aria-hidden />
          </Button>
          <Link href="/expedientes/nuevo">
            <Button size="md">
              <Plus className="h-4 w-4" aria-hidden />
              Nuevo expediente
            </Button>
          </Link>
        </div>
      }
    >
      {error ? (
        <div className="mb-6 rounded-xl border border-danger-text/30 bg-danger-bg p-4 text-sm text-danger-text">
          {error}
        </div>
      ) : null}

      <div className="mb-6 flex flex-wrap gap-2">
        {filters.map((f) => (
          <button
            key={f.id}
            onClick={() => setFilter(f.id)}
            className={cn(
              "rounded-full border px-3.5 py-1.5 text-sm font-medium transition-colors",
              filter === f.id
                ? "border-gold bg-gold/15 text-dark-gold"
                : "border-border bg-card text-muted hover:text-obsessed",
            )}
          >
            {f.label}
          </button>
        ))}
      </div>

      {expedientes === null && !error ? (
        <div className="rounded-2xl border border-dashed border-border bg-card p-10 text-center text-sm text-muted">
          Cargando expedientes…
        </div>
      ) : filtered.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-border bg-card p-10 text-center text-sm text-muted">
          No hay expedientes en este filtro.
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3">
          {filtered.map((expediente) => (
            <Link
              key={expediente.id}
              href={`/expedientes/${expediente.id}`}
              className="group relative flex flex-col overflow-hidden rounded-2xl border border-border bg-card shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lg hover:shadow-obsessed/5"
            >
              <span className="absolute inset-y-0 left-0 w-1 bg-gold" aria-hidden />

              <div className="flex flex-1 flex-col gap-4 p-5 pl-6">
                <div className="flex items-start justify-between gap-3">
                  <span className="rounded-lg bg-app-bg px-2.5 py-1 font-mono text-xs font-medium tracking-wide text-obsessed/70">
                    {expediente.folio}
                  </span>
                  <Badge tone={backendStatusTone[expediente.status]}>{backendStatusLabels[expediente.status]}</Badge>
                </div>

                <div className="flex items-center gap-3">
                  <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full border border-border bg-app-bg text-sm font-semibold text-obsessed">
                    {initials(expediente.ownerDisplayName)}
                  </div>
                  <div className="min-w-0">
                    <p className="truncate text-base font-semibold text-obsessed">{expediente.ownerDisplayName}</p>
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
