"use client";

import { AdvisorChip, LoadError, errorText, useApi } from "@/components/crm/CrmUi";
import { PropertyForm } from "@/components/crm/PropertyForm";
import { Features, ListingStatusBadge, SyncBadge } from "@/components/crm/PropertyUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { StateMessage } from "@/components/ui/StateMessage";
import { useToast } from "@/components/ui/Toast";
import { listAdvisors } from "@/lib/api/crm";
import { getCaptured, retryPropertySync, updateProperty, type CapturedProperty } from "@/lib/api/properties";
import { ONLINE21_BASE_URL, formatDateTime } from "@/lib/crm/labels";
import { contractLabels, operationLabels, priceOf, subtypeLabel, typeLabels } from "@/lib/crm/property-labels";
import { useCan } from "@/lib/permissions";
import { AlertTriangle, ArrowLeft, ExternalLink, Pencil, RefreshCw } from "lucide-react";
import Link from "next/link";
import { use, useState, type ReactNode } from "react";

export default function CapturedPropertyPage({ params }: PageProps<"/propiedades/[id]">) {
  const { id } = use(params);
  const can = useCan();
  const seesAll = can("LEAD_VIEW_ALL");
  const detail = useApi(() => getCaptured(id), [id]);
  const advisors = useApi(() => (seesAll ? listAdvisors() : Promise.resolve([])), [seesAll]);
  const [editing, setEditing] = useState(false);

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
        <StateMessage kind="loading" title="Cargando propiedad…" />
      </PageContainer>
    );
  }

  const p = detail.data;
  return (
    <PageContainer
      title={p.data.headline}
      subtitle={
        <Link href="/propiedades?vista=capturadas" className="inline-flex items-center gap-1 hover:text-obsessed">
          <ArrowLeft className="h-4 w-4" aria-hidden /> Propiedades capturadas
        </Link>
      }
      action={
        p.canEdit && !editing ? (
          <Button variant="secondary" onClick={() => setEditing(true)}>
            <Pencil className="h-4 w-4" aria-hidden /> Editar
          </Button>
        ) : null
      }
    >
      <SyncCard property={p} onChange={detail.setData} />
      {editing ? (
        <PropertyForm
          initial={p.data}
          advisors={advisors.data ?? []}
          canChooseAdvisor={seesAll}
          submitLabel={p.online21Id ? "Guardar y actualizar en 21Online" : "Guardar y publicar en 21Online"}
          onCancel={() => setEditing(false)}
          onSubmit={async (data) => {
            const saved = await updateProperty(p.id, data);
            detail.setData(saved);
            setEditing(false);
          }}
        />
      ) : (
        <Summary property={p} />
      )}
    </PageContainer>
  );
}

function SyncCard({ property: p, onChange }: { property: CapturedProperty; onChange: (p: CapturedProperty) => void }) {
  const { showToast } = useToast();
  const [busy, setBusy] = useState(false);
  const retry = async () => {
    setBusy(true);
    try {
      const updated = await retryPropertySync(p.id);
      onChange(updated);
      showToast(updated.syncStatus === "DONE" ? "Propiedad enviada a 21Online" : "21Online aún no la aceptó");
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card className="mb-5">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="min-w-0 space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <SyncBadge status={p.syncStatus} />
            {p.online21Id ? (
              <a
                href={`${ONLINE21_BASE_URL}/propiedades/ver/${p.online21Id}`}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-1 text-sm font-medium text-dark-gold hover:text-obsessed"
              >
                Número {p.online21Id} en 21Online <ExternalLink className="h-3.5 w-3.5" aria-hidden />
              </a>
            ) : null}
          </div>
          {p.syncStatus === "DONE" && p.syncedAt ? <p className="text-sm text-muted">Última sincronización: {formatDateTime(p.syncedAt)}.</p> : null}
          {p.syncError ? <p className="text-sm text-danger-text">{p.syncError}</p> : null}
          {p.syncUncertain ? (
            <p className="flex items-start gap-2 text-sm text-warning-text">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              Antes de reintentar, busca la propiedad en 21Online: pudo haberse guardado aunque no hubo respuesta, y reintentar la duplicaría.
            </p>
          ) : null}
          {p.advisor && !p.advisorLinked ? (
            <p className="text-sm text-muted">
              {p.advisor.name} no tiene vinculado su usuario de 21Online (CRM → Asesores), así que 21Online la registra a nombre de la llave de la API.
            </p>
          ) : null}
        </div>
        {p.canEdit && p.syncStatus !== "DONE" ? (
          <Button variant="secondary" onClick={retry} disabled={busy} className="shrink-0">
            <RefreshCw className="h-4 w-4" aria-hidden /> {busy ? "Enviando…" : "Reintentar envío"}
          </Button>
        ) : null}
      </div>
    </Card>
  );
}

function Summary({ property: p }: { property: CapturedProperty }) {
  const d = p.data;
  const address = [d.street && (d.extNumber ? `${d.street} ${d.extNumber}` : d.street), d.neighborhood, d.municipality, d.state, d.postalCode].filter(Boolean).join(", ");
  return (
    <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
      <Card>
        <CardHeader title={priceOf(d)} description={`${subtypeLabel(d.subtype)} · ${typeLabels[d.propertyType]} · ${operationLabels[d.operation]}`} action={<ListingStatusBadge status={d.status} />} />
        <Features bedrooms={d.bedrooms} bathrooms={d.bathrooms} builtM2={d.builtM2} landM2={d.landM2} />
        {d.description ? <p className="mt-4 whitespace-pre-line text-sm text-obsessed">{d.description}</p> : <p className="mt-4 text-sm text-muted">Sin descripción.</p>}
      </Card>
      <Card>
        <CardHeader title="Datos" />
        <dl className="space-y-3 text-sm">
          <Row label="Asesor">
            <AdvisorChip advisor={p.advisor} emptyLabel="Sin asesor" />
          </Row>
          <Row label="Ubicación">{address}</Row>
          <Row label="En internet">
            {d.publishOnline ? "Sí" : "No"}
            {d.hidePrice ? " · precio oculto" : ""}
            {d.hideStreet ? " · calle oculta" : d.hideNumber ? " · número oculto" : ""}
          </Row>
          {d.contractType ? <Row label="Contrato">{contractLabels[d.contractType] ?? d.contractType}</Row> : null}
          {d.commission !== null ? <Row label="Comisión">{d.commission}%</Row> : null}
          {d.halfBathrooms ? <Row label="Medios baños">{d.halfBathrooms}</Row> : null}
          {d.parking !== null ? <Row label="Estacionamientos">{d.parking}</Row> : null}
          {d.levels !== null ? <Row label="Niveles">{d.levels}</Row> : null}
          {d.age !== null ? <Row label="Antigüedad">{d.age} años</Row> : null}
          {d.maintenanceFee !== null ? <Row label="Mantenimiento">{priceOf({ operation: "venta", salePrice: d.maintenanceFee, rentPrice: null, currency: d.currency })}</Row> : null}
          <Row label="Capturó">
            {p.createdBy.name} · {formatDateTime(p.createdAt)}
          </Row>
        </dl>
      </Card>
    </div>
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
