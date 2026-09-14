"use client";

import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/Button";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { mockExpedientes } from "@/data/mock-expediente";
import { ArrowRight, Plus } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";

export default function ExpedientesPage() {
  const router = useRouter();

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
      <div className="overflow-hidden rounded-2xl border border-border bg-white shadow-sm">
        <table className="hidden w-full text-left text-sm md:table">
          <thead className="border-b border-border bg-app-bg/60">
            <tr className="text-xs font-medium uppercase tracking-wide text-muted">
              <th className="px-6 py-3.5">Folio</th>
              <th className="px-6 py-3.5">Cliente</th>
              <th className="px-6 py-3.5">Tipo</th>
              <th className="px-6 py-3.5">Estado</th>
              <th className="px-6 py-3.5">Última actualización</th>
              <th className="px-6 py-3.5 text-right">Acción</th>
            </tr>
          </thead>
          <tbody>
            {mockExpedientes.map((expediente) => (
              <tr
                key={expediente.id}
                onClick={() => router.push("/expedientes/demo")}
                className="cursor-pointer border-b border-border last:border-0 transition-colors hover:bg-app-bg/50"
              >
                <td className="px-6 py-4 font-medium text-navy">{expediente.folio}</td>
                <td className="px-6 py-4 text-navy">{expediente.ownerName}</td>
                <td className="px-6 py-4 text-muted">{expediente.contractType}</td>
                <td className="px-6 py-4">
                  <StatusBadge status={expediente.status} />
                </td>
                <td className="px-6 py-4 text-muted">{expediente.updatedAt}</td>
                <td className="px-6 py-4 text-right">
                  <Link
                    href="/expedientes/demo"
                    onClick={(e) => e.stopPropagation()}
                    className="inline-flex items-center gap-1 text-sm font-medium text-teal-dark hover:underline"
                  >
                    Ver expediente
                    <ArrowRight className="h-3.5 w-3.5" aria-hidden />
                  </Link>
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <div className="divide-y divide-border md:hidden">
          {mockExpedientes.map((expediente) => (
            <Link
              key={expediente.id}
              href="/expedientes/demo"
              className="block px-5 py-4 transition-colors hover:bg-app-bg/50"
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-sm font-semibold text-navy">{expediente.folio}</p>
                  <p className="mt-0.5 text-sm text-navy/80">{expediente.ownerName}</p>
                </div>
                <StatusBadge status={expediente.status} />
              </div>
              <div className="mt-3 flex items-center justify-between text-xs text-muted">
                <span>{expediente.contractType}</span>
                <span>{expediente.updatedAt}</span>
              </div>
            </Link>
          ))}
        </div>
      </div>
    </PageContainer>
  );
}
