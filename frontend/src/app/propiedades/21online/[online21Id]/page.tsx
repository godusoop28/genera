"use client";

import { AdvisorChip, LoadError, useApi } from "@/components/crm/CrmUi";
import { Features, ListingStatusBadge } from "@/components/crm/PropertyUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { Badge } from "@/components/ui/Badge";
import { Card, CardHeader } from "@/components/ui/Card";
import { StateMessage } from "@/components/ui/StateMessage";
import { getInventoryItem } from "@/lib/api/properties";
import { ONLINE21_BASE_URL, formatDateTime } from "@/lib/crm/labels";
import { contractLabels, priceOf } from "@/lib/crm/property-labels";
import { ArrowLeft, ExternalLink, ImageOff, Pencil } from "lucide-react";
import Link from "next/link";
import { use, type ReactNode } from "react";

/** Ficha de una propiedad de 21Online (solo consulta; se edita en 21Online o, si se capturó aquí, en su ficha de Genera). */
export default function Online21PropertyPage({ params }: PageProps<"/propiedades/21online/[online21Id]">) {
  const { online21Id } = use(params);
  const detail = useApi(() => getInventoryItem(online21Id), [online21Id]);

  if (detail.error && !detail.data) {
    return (
      <PageContainer title="Propiedad">
        <LoadError message={detail.error} onRetry={detail.reload} />
      </PageContainer>
    );
  }
  if (!detail.data) {
    return (
      <PageContainer title="Propiedad">
        <StateMessage kind="loading" title="Consultando 21Online…" />
      </PageContainer>
    );
  }

  const { listing: l, advisor, generaId } = detail.data;
  const address = [l.street && (l.number ? `${l.street} ${l.number}` : l.street), l.neighborhood, l.municipality, l.state, l.postalCode].filter(Boolean).join(", ");
  const linkClass =
    "inline-flex min-h-11 items-center gap-2 rounded-xl border border-border bg-card px-4 text-sm font-medium text-obsessed transition-colors hover:border-gold/60 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30";

  return (
    <PageContainer
      title={l.title}
      subtitle={
        <Link href="/propiedades" className="inline-flex items-center gap-1 hover:text-obsessed">
          <ArrowLeft className="h-4 w-4" aria-hidden /> Inventario
        </Link>
      }
      action={
        <>
          {generaId ? (
            <Link href={`/propiedades/${generaId}`} className={linkClass}>
              <Pencil className="h-4 w-4" aria-hidden /> Editar en Genera
            </Link>
          ) : null}
          <a href={`${ONLINE21_BASE_URL}/propiedades/ver/${l.id}`} target="_blank" rel="noreferrer" className={linkClass}>
            Abrir en 21Online <ExternalLink className="h-4 w-4" aria-hidden />
          </a>
        </>
      }
    >
      <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <div className="space-y-5">
          <div className="overflow-hidden rounded-xl border border-border bg-app-bg">
            {l.photoUrl ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img src={l.photoUrl} alt={l.title} className="aspect-[16/9] w-full object-cover" />
            ) : (
              <div className="flex aspect-[16/9] flex-col items-center justify-center gap-2 text-sm text-muted">
                <ImageOff className="h-6 w-6" aria-hidden /> Sin fotos en 21Online
              </div>
            )}
          </div>
          <Card>
            <CardHeader
              title={priceOf(l)}
              description={`${l.subtypeLabel} en ${l.operation === "renta" ? "renta" : "venta"}${l.photoCount ? ` · ${l.photoCount} fotos` : ""}`}
              action={<ListingStatusBadge status={l.status} />}
            />
            <Features bedrooms={l.bedrooms} bathrooms={l.bathrooms} builtM2={l.builtM2} landM2={l.landM2} />
            {l.description ? <p className="mt-4 whitespace-pre-line text-sm text-obsessed">{l.description}</p> : <p className="mt-4 text-sm text-muted">Sin descripción.</p>}
          </Card>
        </div>
        <Card>
          <CardHeader title="Datos en 21Online" />
          <dl className="space-y-3 text-sm">
            <Row label="Número">{l.key}</Row>
            {l.internalKey ? <Row label="Clave">{l.internalKey}</Row> : null}
            <Row label="Asesor">
              <AdvisorChip advisor={advisor} emptyLabel={l.advisorOnline21Id ? `Usuario ${l.advisorOnline21Id} sin vincular` : "Sin asesor"} />
            </Row>
            {address ? <Row label="Ubicación">{address}</Row> : null}
            <Row label="En internet">{l.publishedOnline === null ? "—" : l.publishedOnline ? "Sí" : "No"}</Row>
            {l.contractType ? <Row label="Contrato">{contractLabels[l.contractType] ?? l.contractType}</Row> : null}
            {l.halfBathrooms ? <Row label="Medios baños">{l.halfBathrooms}</Row> : null}
            {l.parking !== null ? <Row label="Estacionamientos">{l.parking}</Row> : null}
            {l.createdAt ? <Row label="Alta">{formatDateTime(l.createdAt)}</Row> : null}
            {l.updatedAt ? <Row label="Modificada">{formatDateTime(l.updatedAt)}</Row> : null}
            {generaId ? (
              <Row label="Origen">
                <Badge tone="gold">Capturada en Genera</Badge>
              </Row>
            ) : null}
          </dl>
        </Card>
      </div>
    </PageContainer>
  );
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid grid-cols-[8.5rem_minmax(0,1fr)] gap-3">
      <dt className="text-muted">{label}</dt>
      <dd className="min-w-0 text-obsessed">{children}</dd>
    </div>
  );
}
