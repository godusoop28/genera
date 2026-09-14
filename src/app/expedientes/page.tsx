"use client";

import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/Button";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { mockExpedientes } from "@/data/mock-expediente";
import { initials } from "@/lib/utils";
import type { ExpedienteStatus } from "@/types/expediente";
import { ArrowUpRight, Calendar, Home, Key, Plus } from "lucide-react";
import Link from "next/link";

const statusAccent: Record<ExpedienteStatus, string> = {
  Borrador: "bg-muted/40",
  "Esperando documentos": "bg-[var(--color-warning-text)]",
  "Documentos recibidos": "bg-teal",
  "En revisión": "bg-navy",
  "Listo para contrato": "bg-[var(--color-success-text)]",
};

const statusAvatarTone: Record<ExpedienteStatus, string> = {
  Borrador: "bg-app-bg text-muted border border-border",
  "Esperando documentos": "bg-warning-bg text-warning-text",
  "Documentos recibidos": "bg-teal-light text-teal-dark",
  "En revisión": "bg-navy/10 text-navy",
  "Listo para contrato": "bg-success-bg text-success-text",
};

export default function ExpedientesPage() {
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
      <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 xl:grid-cols-3">
        {mockExpedientes.map((expediente) => (
          <Link
            key={expediente.id}
            href="/expedientes/demo"
            className="group relative flex flex-col overflow-hidden rounded-2xl border border-border bg-white shadow-sm transition-all duration-200 hover:-translate-y-0.5 hover:shadow-lg hover:shadow-navy/5"
          >
            <span
              className={`absolute inset-y-0 left-0 w-1 ${statusAccent[expediente.status]}`}
              aria-hidden
            />

            <div className="flex flex-1 flex-col gap-4 p-5 pl-6">
              <div className="flex items-start justify-between gap-3">
                <span className="rounded-lg bg-app-bg px-2.5 py-1 font-mono text-xs font-medium tracking-wide text-navy/70">
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
                  <p className="truncate text-base font-semibold text-navy">
                    {expediente.ownerName}
                  </p>
                  <p className="mt-0.5 flex items-center gap-1.5 text-xs text-muted">
                    {expediente.contractType === "Arrendamiento" ? (
                      <Key className="h-3.5 w-3.5" aria-hidden />
                    ) : (
                      <Home className="h-3.5 w-3.5" aria-hidden />
                    )}
                    {expediente.contractType}
                  </p>
                </div>
              </div>

              <div className="mt-auto flex items-center justify-between border-t border-border pt-4">
                <span className="flex items-center gap-1.5 text-xs text-muted">
                  <Calendar className="h-3.5 w-3.5" aria-hidden />
                  {expediente.updatedAt}
                </span>
                <span className="inline-flex items-center gap-1 text-sm font-medium text-teal-dark">
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
    </PageContainer>
  );
}
