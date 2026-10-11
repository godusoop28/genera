"use client";

import { AdvisorChip, PreviewNotice, SourceLabel, StageBadge } from "@/components/crm/CrmUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { Card, CardHeader } from "@/components/ui/Card";
import { StateMessage } from "@/components/ui/StateMessage";
import { ONLINE21_BASE_URL } from "@/data/crm/properties";
import { formatDate, propertyAddress, propertyPrice, timeAgo } from "@/lib/crm/labels";
import { useCrm } from "@/lib/crm/store";
import { ArrowLeft, ArrowRight, ExternalLink } from "lucide-react";
import Link from "next/link";
import { use, type ReactNode } from "react";

export default function PropertyDetailPage({ params }: PageProps<"/propiedades/[id]">) {
  const { id } = use(params);
  const crm = useCrm();

  if (!crm) {
    return (
      <PageContainer title="Propiedad">
        <StateMessage kind="loading" title="Cargando propiedad…" />
      </PageContainer>
    );
  }

  const property = crm.properties.find((p) => String(p.id) === id);
  if (!property) {
    return (
      <PageContainer title="Propiedad no encontrada">
        <StateMessage
          kind="no-results"
          title="Esta propiedad no está en la muestra"
          action={
            <Link href="/propiedades" className="text-sm font-medium text-dark-gold hover:text-obsessed">
              Volver a propiedades
            </Link>
          }
        />
      </PageContainer>
    );
  }

  const advisor = crm.advisors.find((a) => a.id === property.advisorId);
  const leads = crm.leads.filter((l) => l.propertyId === property.id).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  const d = property.details;

  return (
    <PageContainer
      title={`${property.subtype} en ${property.operation} · ${property.neighborhood}`}
      subtitle={
        <span className="font-mono text-xs">
          {property.key} · {property.internalKey}
        </span>
      }
      action={
        <>
          <Link
            href="/propiedades"
            className="inline-flex min-h-10 items-center gap-2 rounded-xl border border-border bg-card px-4 py-2.5 text-sm font-medium text-obsessed hover:bg-app-bg focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-obsessed/15"
          >
            <ArrowLeft className="h-4 w-4" aria-hidden /> Propiedades
          </Link>
          <a
            href={`${ONLINE21_BASE_URL}/propiedades/ver/${property.id}`}
            target="_blank"
            rel="noreferrer"
            className="inline-flex min-h-10 items-center gap-2 rounded-xl bg-gold px-4 py-2.5 text-sm font-medium text-brand-ink shadow-sm hover:bg-gold-hover focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/40"
          >
            Ficha en 21Online <ExternalLink className="h-4 w-4" aria-hidden />
          </a>
        </>
      }
    >
      <PreviewNotice>Datos de solo lectura tomados de 21Online. Los leads de esta propiedad son de ejemplo.</PreviewNotice>
      <div className="grid grid-cols-1 gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
        <div className="min-w-0 space-y-5">
          <Card>
            <p className="text-3xl font-semibold tabular-nums text-obsessed">{propertyPrice(property)}</p>
            <p className="mt-1 text-sm text-muted">{propertyAddress(property)}, {property.state}</p>
            <dl className="mt-5 grid grid-cols-2 gap-x-6 gap-y-4 text-sm sm:grid-cols-3">
              <Field label="Tipo">{property.subtype}</Field>
              <Field label="Operación">{property.operation === "venta" ? "Venta" : "Renta"}</Field>
              <Field label="Modificada en 21Online">{formatDate(property.updatedAt + "T12:00:00")}</Field>
              {d?.landM2 ? <Field label="Terreno">{d.landM2} m²</Field> : null}
              {d?.builtM2 ? <Field label="Construcción">{d.builtM2} m²</Field> : null}
              {d?.bedrooms ? <Field label="Recámaras">{d.bedrooms}</Field> : null}
              {d?.bathrooms ? <Field label="Baños">{d.bathrooms}</Field> : null}
              {d?.parking ? <Field label="Estacionamientos">{d.parking}</Field> : null}
              {d?.acceptsCredit !== undefined ? <Field label="Acepta créditos">{d.acceptsCredit ? "Sí" : "No"}</Field> : null}
              {d?.omniMlsId ? <Field label="Id Omni MLS">{d.omniMlsId}</Field> : null}
            </dl>
            {d?.description ? <p className="mt-5 text-sm leading-relaxed text-obsessed">{d.description}</p> : null}
            {d?.features?.length ? (
              <ul className="mt-4 flex flex-wrap gap-2">
                {d.features.map((f) => (
                  <li key={f} className="rounded-full border border-border bg-app-bg px-3 py-1 text-xs text-obsessed">
                    {f}
                  </li>
                ))}
              </ul>
            ) : null}
            {!d ? (
              <p className="mt-5 rounded-xl bg-app-bg px-4 py-3 text-sm text-muted">
                La ficha completa (recámaras, m², descripción, fotos) se traerá de 21Online cuando se conecte la sincronización.
              </p>
            ) : null}
          </Card>

          <Card>
            <CardHeader title="Leads de esta propiedad" description={`${leads.length} prospecto${leads.length === 1 ? "" : "s"} han preguntado por ella.`} />
            {leads.length === 0 ? (
              <p className="text-sm text-muted">Nadie ha preguntado todavía por esta propiedad.</p>
            ) : (
              <ul className="divide-y divide-border">
                {leads.map((lead) => (
                  <li key={lead.id}>
                    <Link
                      href={`/leads/${lead.id}`}
                      className="grid grid-cols-1 gap-2 py-3 hover:bg-app-bg/50 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30 sm:grid-cols-[minmax(0,1fr)_auto_auto] sm:items-center sm:gap-4"
                    >
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-semibold text-obsessed">{lead.name}</span>
                        <SourceLabel source={lead.source} detail={lead.sourceDetail} className="text-xs" />
                      </span>
                      <StageBadge stage={lead.stage} />
                      <span className="flex items-center gap-1 text-xs text-muted">
                        {timeAgo(lead.createdAt)} <ArrowRight className="h-3.5 w-3.5" aria-hidden />
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>

        <aside className="min-w-0 space-y-5">
          <Card>
            <CardHeader title="Asesor de la propiedad" description="Los leads que pregunten por ella se le asignan directamente." />
            <AdvisorChip advisor={advisor} emptyLabel="Sin asesor (entra a la ruleta)" />
            {advisor?.email ? <p className="mt-2 text-sm text-muted">{advisor.email}</p> : null}
          </Card>
        </aside>
      </div>
    </PageContainer>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="mt-0.5 text-obsessed">{children}</dd>
    </div>
  );
}
