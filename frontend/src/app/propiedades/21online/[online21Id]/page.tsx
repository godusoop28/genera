"use client";

import { AdvisorChip, LoadError, useApi } from "@/components/crm/CrmUi";
import { BarList, ColumnChart, type ChartDatum } from "@/components/crm/PropertyCharts";
import { PropertyLeads } from "@/components/crm/PropertyLeads";
import { Features, ListingStatusBadge } from "@/components/crm/PropertyUi";
import { PageContainer } from "@/components/layout/PageContainer";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Modal } from "@/components/ui/Modal";
import { StateMessage } from "@/components/ui/StateMessage";
import { Tabs } from "@/components/ui/Tabs";
import { useToast } from "@/components/ui/Toast";
import { getBlob } from "@/lib/api/client";
import {
  cancelInventoryItem,
  getInventoryItem,
  linkInventoryItem,
  getPropertyClients,
  getPropertyDetails,
  getPropertyFollowUps,
  getPropertyPortals,
  propertySheetPath,
  type DossierField,
  type PortalListing,
  type PropertyDetails,
  type RelatedClients,
  type SheetVariant,
} from "@/lib/api/properties";
import { ONLINE21_BASE_URL, formatDateTime } from "@/lib/crm/labels";
import { contractLabels, priceOf } from "@/lib/crm/property-labels";
import { errorText } from "@/lib/errors";
import { useCan } from "@/lib/permissions";
import { cn } from "@/lib/utils";
import { AlertTriangle, ArrowLeft, Ban, ChevronDown, Copy, Download, ExternalLink, FileText, Globe, ImageOff, MapPin, MessageCircle, Pencil, Phone, Users } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { use, useEffect, useRef, useState, type ReactNode } from "react";

type TabId = "datos" | "portales" | "clientes" | "leads" | "seguimientos" | "resumen";

const tabs: { id: TabId; label: string }[] = [
  { id: "datos", label: "Datos generales" },
  { id: "portales", label: "Portales" },
  { id: "clientes", label: "Clientes relacionados" },
  { id: "leads", label: "Leads en Genera" },
  { id: "seguimientos", label: "Seguimientos" },
  { id: "resumen", label: "Resumen" },
];

const sheetVariants: { id: SheetVariant; label: string; description: string }[] = [
  { id: "ADVISOR", label: "Con datos del asesor", description: "Para el cliente: incluye teléfono y correo del asesor." },
  { id: "NO_CONTACT", label: "Sin datos de contacto", description: "Para compartir con otras inmobiliarias." },
  { id: "NO_LOGO", label: "Sin logo", description: "Neutra: sin logotipo ni datos de contacto." },
];

/**
 * Ficha de una propiedad de 21Online, como su pantalla "Ver propiedad": datos generales completos,
 * portales donde se publica (con su liga), clientes relacionados, seguimientos, resumen con gráficas
 * y la ficha técnica en PDF. Se edita en 21Online o, si se capturó aquí, en su ficha de Genera.
 */
export default function Online21PropertyPage({ params }: { params: Promise<{ online21Id: string }> }) {
  const { online21Id } = use(params);
  const detail = useApi(() => getPropertyDetails(online21Id), [online21Id]);
  const [tab, setTab] = useState<TabId>("datos");

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

  const data = detail.data;
  const l = data.details.listing;
  const linkClass =
    "inline-flex min-h-11 items-center gap-2 rounded-xl border border-border bg-card px-4 text-sm font-medium text-obsessed transition-colors hover:border-gold/60 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30";

  return (
    <PageContainer
      title={l.title}
      subtitle={
        <Link href="/propiedades" className="inline-flex items-center gap-1 hover:text-obsessed">
          <ArrowLeft className="h-4 w-4" aria-hidden /> Inventario · {l.key}
          {l.internalKey ? ` · ${l.internalKey}` : ""}
        </Link>
      }
      action={
        <div className="flex flex-wrap gap-2">
          <SheetMenu id={l.id} />
          <ShareMenu details={data} />
          <a href={`${ONLINE21_BASE_URL}/propiedades/ver/${l.id}`} target="_blank" rel="noreferrer" className={linkClass}>
            Abrir en 21Online <ExternalLink className="h-4 w-4" aria-hidden />
          </a>
        </div>
      }
    >
      <Tabs tabs={tabs} value={tab} onChange={setTab} label="Secciones de la propiedad" idPrefix="propiedad" className="mb-6" />
      <div role="tabpanel" id={`propiedad-panel-${tab}`} aria-labelledby={`propiedad-tab-${tab}`}>
        {tab === "datos" ? <GeneralTab data={data} online21Id={online21Id} /> : null}
        {tab === "portales" ? <PortalsTab online21Id={online21Id} publicUrl={data.details.publicUrl} /> : null}
        {tab === "clientes" ? <ClientsTab online21Id={online21Id} /> : null}
        {tab === "leads" ? <PropertyLeads online21Id={Number(online21Id)} /> : null}
        {tab === "seguimientos" ? <FollowUpsTab online21Id={online21Id} /> : null}
        {tab === "resumen" ? <SummaryTab online21Id={online21Id} details={data} /> : null}
      </div>
    </PageContainer>
  );
}

// --- Datos generales ------------------------------------------------------------

function GeneralTab({ data, online21Id }: { data: PropertyDetails; online21Id: string }) {
  const d = data.details;
  const l = d.listing;
  const [photo, setPhoto] = useState(0);
  const photos = d.photos.length > 0 ? d.photos : l.photoUrl ? [l.photoUrl] : [];
  const sections = groupBy(d.fields);

  return (
    <div className="grid grid-cols-1 gap-5 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
      <div className="space-y-5">
        <div className="overflow-hidden rounded-xl border border-border bg-app-bg">
          {photos.length > 0 ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={photos[Math.min(photo, photos.length - 1)]} alt={`${l.title}, foto ${photo + 1} de ${photos.length}`} className="aspect-[16/9] w-full object-cover" />
          ) : (
            <div className="flex aspect-[16/9] flex-col items-center justify-center gap-2 text-sm text-muted">
              <ImageOff className="h-6 w-6" aria-hidden /> Sin fotos en 21Online
            </div>
          )}
        </div>
        {photos.length > 1 ? (
          <div className="flex gap-2 overflow-x-auto pb-1" role="group" aria-label="Fotos">
            {photos.map((url, i) => (
              <button
                key={url}
                type="button"
                onClick={() => setPhoto(i)}
                aria-label={`Ver foto ${i + 1}`}
                aria-pressed={photo === i}
                className={cn("shrink-0 overflow-hidden rounded-lg border-2", photo === i ? "border-gold" : "border-transparent")}
              >
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src={url} alt="" className="h-16 w-24 object-cover" loading="lazy" />
              </button>
            ))}
          </div>
        ) : null}
        <Card>
          <CardHeader
            title={priceOf(l)}
            description={`${l.subtypeLabel} en ${l.operation === "renta" ? "renta" : "venta"}${d.photos.length ? ` · ${d.photos.length} fotos` : ""}`}
            action={<ListingStatusBadge status={l.status} />}
          />
          <Features bedrooms={l.bedrooms} bathrooms={l.bathrooms} builtM2={l.builtM2} landM2={l.landM2} />
          {l.description ? <p className="mt-4 whitespace-pre-line text-sm text-obsessed">{l.description}</p> : <p className="mt-4 text-sm text-muted">Sin descripción.</p>}
          {d.headlineEnglish || d.descriptionEnglish ? (
            <details className="mt-4 text-sm">
              <summary className="cursor-pointer font-medium text-obsessed">Versión en inglés</summary>
              {d.headlineEnglish ? <p className="mt-2 font-medium text-obsessed">{d.headlineEnglish}</p> : null}
              {d.descriptionEnglish ? <p className="mt-1 whitespace-pre-line text-obsessed">{d.descriptionEnglish}</p> : null}
            </details>
          ) : null}
        </Card>
        {sections.map(([section, fields]) => (
          <Card key={section}>
            <CardHeader title={section} />
            <dl className="grid grid-cols-1 gap-x-6 gap-y-3 text-sm sm:grid-cols-2">
              {fields.map((f) => (
                <Row key={f.label} label={f.label}>
                  <span className="whitespace-pre-line">{f.value}</span>
                </Row>
              ))}
            </dl>
          </Card>
        ))}
        {d.floorPlans.length > 0 ? (
          <Card>
            <CardHeader title="Planos" />
            <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
              {d.floorPlans.map((url, i) => (
                <a key={url} href={url} target="_blank" rel="noreferrer" className="overflow-hidden rounded-lg border border-border">
                  {/* eslint-disable-next-line @next/next/no-img-element */}
                  <img src={url} alt={`Plano ${i + 1}`} className="aspect-[4/3] w-full object-cover" loading="lazy" />
                </a>
              ))}
            </div>
          </Card>
        ) : null}
      </div>
      <div className="space-y-5">
        <Card>
          <CardHeader title="Asesor" />
          {d.advisor?.name ? (
            <div className="space-y-2 text-sm">
              <p className="font-medium text-obsessed">{d.advisor.name}</p>
              {d.advisor.mobile ? (
                <a href={`tel:${d.advisor.mobile}`} className="flex items-center gap-2 text-obsessed hover:underline">
                  <Phone className="h-4 w-4 text-muted" aria-hidden /> Cel. {d.advisor.mobile}
                </a>
              ) : null}
              {d.advisor.officePhone ? <p className="text-muted">Oficina: {d.advisor.officePhone}</p> : null}
              {d.advisor.email ? (
                <a href={`mailto:${d.advisor.email}`} className="block break-all text-obsessed hover:underline">
                  {d.advisor.email}
                </a>
              ) : null}
            </div>
          ) : (
            <p className="text-sm text-muted">Sin asesor asignado en 21Online.</p>
          )}
          <div className="mt-3 border-t border-border pt-3 text-sm">
            <span className="text-muted">En Genera: </span>
            <AdvisorChip advisor={data.generaAdvisor} emptyLabel={l.advisorOnline21Id ? `Usuario ${l.advisorOnline21Id} sin vincular` : "Sin asesor"} />
          </div>
        </Card>
        <Card>
          <CardHeader title="Datos en 21Online" />
          <dl className="space-y-3 text-sm">
            <Row label="Número">{l.key}</Row>
            {l.contractType ? <Row label="Contrato">{contractLabels[l.contractType] ?? l.contractType}</Row> : null}
            <Row label="En internet">{l.publishedOnline === null ? "—" : l.publishedOnline ? "Sí" : "No"}</Row>
            {l.createdAt ? <Row label="Alta">{formatDateTime(l.createdAt)}</Row> : null}
            {l.updatedAt ? <Row label="Modificada">{formatDateTime(l.updatedAt)}</Row> : null}
          </dl>
          <div className="mt-4 flex flex-wrap gap-2">
            {d.latitude !== null && d.longitude !== null ? (
              <a
                href={`https://www.google.com/maps?q=${d.latitude},${d.longitude}`}
                target="_blank"
                rel="noreferrer"
                className="inline-flex items-center gap-1.5 text-sm font-medium text-dark-gold hover:underline"
              >
                <MapPin className="h-4 w-4" aria-hidden /> Ver en el mapa
              </a>
            ) : null}
            {d.videoUrl ? (
              <a href={d.videoUrl} target="_blank" rel="noreferrer" className="inline-flex items-center gap-1.5 text-sm font-medium text-dark-gold hover:underline">
                <ExternalLink className="h-4 w-4" aria-hidden /> Video
              </a>
            ) : null}
          </div>
        </Card>
        <EditInGenera online21Id={online21Id} status={l.status} onCancelled={() => window.location.reload()} />
      </div>
    </div>
  );
}

/**
 * Editar o dar de baja desde Genera. Si aún no está vinculada, al editar se vincula (queda sincronizada:
 * lo que se cambie aquí se manda a 21Online y lo que se cambie allá se trae solo).
 */
function EditInGenera({ online21Id, status, onCancelled }: { online21Id: string; status: string | null; onCancelled: () => void }) {
  const can = useCan();
  const router = useRouter();
  const { showToast } = useToast();
  const item = useApi(() => getInventoryItem(online21Id), [online21Id]);
  const [busy, setBusy] = useState<"link" | "cancel" | null>(null);
  const [confirmCancel, setConfirmCancel] = useState(false);
  const manage = can("PROPERTY_MANAGE");
  const generaId = item.data?.generaId ?? null;
  if (!generaId && !manage) return null;

  const edit = async () => {
    if (generaId) {
      router.push(`/propiedades/${generaId}?editar=1`);
      return;
    }
    setBusy("link");
    try {
      const linked = await linkInventoryItem(online21Id);
      router.push(`/propiedades/${linked.id}?editar=1`);
    } catch (err) {
      showToast(errorText(err));
      setBusy(null);
    }
  };

  const cancel = async () => {
    setBusy("cancel");
    try {
      await cancelInventoryItem(online21Id);
      setConfirmCancel(false);
      showToast("Propiedad dada de baja: quedó cancelada en 21Online.");
      onCancelled();
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setBusy(null);
    }
  };

  return (
    <Card>
      {generaId ? <Badge tone="gold">Vinculada con Genera</Badge> : null}
      <p className="mt-2 text-sm text-muted">
        Los cambios que hagas desde Genera se mandan a 21Online (solo los campos que cambies) y lo que se edite en 21Online se trae solo cada pocos minutos.
      </p>
      <div className="mt-3 flex flex-wrap gap-2">
        <Button variant="secondary" size="sm" onClick={edit} disabled={busy !== null}>
          <Pencil className="h-4 w-4" aria-hidden /> {busy === "link" ? "Abriendo…" : "Editar desde Genera"}
        </Button>
        {manage && status !== "cancelada" && status !== "cerrada" ? (
          <Button variant="ghost" size="sm" onClick={() => setConfirmCancel(true)} disabled={busy !== null}>
            <Ban className="h-4 w-4" aria-hidden /> Dar de baja
          </Button>
        ) : null}
      </div>
      <Modal
        open={confirmCancel}
        onClose={() => setConfirmCancel(false)}
        title="Dar de baja la propiedad"
        footer={
          <>
            <Button variant="ghost" onClick={() => setConfirmCancel(false)} disabled={busy === "cancel"}>
              Cancelar
            </Button>
            <Button variant="danger" onClick={cancel} disabled={busy === "cancel"}>
              {busy === "cancel" ? "Dando de baja…" : "Dar de baja"}
            </Button>
          </>
        }
      >
        <p className="text-sm text-muted">
          21Online no permite borrar propiedades: quedará con estatus <strong>cancelada</strong> y dejará de promocionarse en portales. Puedes reactivarla
          después cambiando su estatus.
        </p>
      </Modal>
    </Card>
  );
}

// --- Portales ---------------------------------------------------------------------

function PortalsTab({ online21Id, publicUrl }: { online21Id: string; publicUrl: string | null }) {
  const portals = useApi(() => getPropertyPortals(online21Id), [online21Id]);
  if (portals.error && !portals.data) return <LoadError message={portals.error} onRetry={portals.reload} />;
  if (!portals.data) return <StateMessage kind="loading" title="Consultando portales…" />;
  const list = portals.data;
  return (
    <div className="space-y-5">
      {publicUrl ? (
        <Card>
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <p className="font-medium text-obsessed">Sitio de CENTURY 21</p>
              <p className="break-all text-sm text-muted">{publicUrl}</p>
            </div>
            <a href={publicUrl} target="_blank" rel="noreferrer" className="inline-flex shrink-0 items-center gap-1.5 text-sm font-medium text-dark-gold hover:underline">
              Ver publicación <ExternalLink className="h-4 w-4" aria-hidden />
            </a>
          </div>
        </Card>
      ) : null}
      {list.length === 0 ? (
        <StateMessage kind="empty" title="Sin portales asignados" description="En 21Online esta propiedad no tiene portales asignados (Publicidad → Portales)." />
      ) : (
        <div className="overflow-hidden rounded-xl border border-border bg-card">
          <ul className="divide-y divide-border">
            {list.map((p, i) => (
              <PortalRow key={`${p.portal}-${i}`} portal={p} />
            ))}
          </ul>
        </div>
      )}
      <p className="text-xs text-muted">
        La liga aparece cuando 21Online la tiene registrada. Si un portal no la muestra, ábrelo desde 21Online → Ver portales.
      </p>
    </div>
  );
}

function PortalRow({ portal: p }: { portal: PortalListing }) {
  return (
    <li className="flex flex-col gap-2 p-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="min-w-0">
        <p className="flex flex-wrap items-center gap-2 font-medium text-obsessed">
          <Globe className="h-4 w-4 text-muted" aria-hidden /> {p.portal}
          <Badge tone={p.withError ? "danger" : p.active ? "success" : "neutral"}>{p.withError ? "Con error" : p.active ? "Activo" : "Inactivo"}</Badge>
          {p.status ? <span className="text-xs font-normal text-muted">{p.status}</span> : null}
        </p>
        {p.reference ? <p className="mt-0.5 text-xs text-muted">{p.reference}</p> : null}
        {p.withError && p.error ? (
          <p className="mt-1 flex items-start gap-1.5 text-sm text-danger-text">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden /> {p.error}
          </p>
        ) : null}
        {p.updatedAt ? <p className="mt-0.5 text-xs text-muted">Actualizado {formatDateTime(p.updatedAt)}</p> : null}
      </div>
      {p.url ? (
        <a href={p.url} target="_blank" rel="noreferrer" className="inline-flex shrink-0 items-center gap-1.5 text-sm font-medium text-dark-gold hover:underline">
          Ver en {p.portal} <ExternalLink className="h-4 w-4" aria-hidden />
        </a>
      ) : (
        <span className="text-xs text-muted">Sin liga en 21Online</span>
      )}
    </li>
  );
}

// --- Clientes relacionados ----------------------------------------------------------

function ClientsTab({ online21Id }: { online21Id: string }) {
  const clients = useApi(() => getPropertyClients(online21Id), [online21Id]);
  if (clients.error && !clients.data) return <LoadError message={clients.error} onRetry={clients.reload} />;
  if (!clients.data) return <StateMessage kind="loading" title="Consultando clientes…" />;
  const { contactsVisible, clients: list } = clients.data;
  if (list.length === 0) return <StateMessage kind="empty" title="Sin clientes relacionados" description="Nadie ha mostrado interés en esta propiedad en 21Online." />;
  return (
    <div className="space-y-3">
      {!contactsVisible ? (
        <p className="rounded-lg bg-info-bg px-3 py-2 text-sm text-info-text">Los datos de contacto solo los ve el asesor de la propiedad o quien ve todos los leads.</p>
      ) : null}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-muted">
          {list.length} cliente{list.length === 1 ? "" : "s"} interesado{list.length === 1 ? "" : "s"}
        </p>
        {contactsVisible ? <DownloadClientsCsv rows={list} online21Id={online21Id} /> : null}
      </div>
      <div className="overflow-x-auto rounded-xl border border-border bg-card">
        <table className="w-full min-w-[640px] text-left text-sm">
          <thead className="bg-app-bg text-xs uppercase tracking-wide text-muted">
            <tr>
              <th scope="col" className="px-4 py-3 font-medium">Cliente</th>
              <th scope="col" className="px-4 py-3 font-medium">Medio</th>
              <th scope="col" className="px-4 py-3 font-medium">Estatus</th>
              <th scope="col" className="px-4 py-3 font-medium">Fecha</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {list.map((c, i) => (
              <tr key={c.id ?? i} className="align-top">
                <td className="px-4 py-3">
                  {contactsVisible ? (
                    <>
                      <p className="font-medium text-obsessed">{c.name ?? "Sin nombre"}</p>
                      {c.phone ? <p className="text-xs text-muted">{c.phone}</p> : null}
                      {c.email ? <p className="break-all text-xs text-muted">{c.email}</p> : null}
                      {c.message ? <p className="mt-1 line-clamp-2 text-xs text-muted">“{c.message}”</p> : null}
                    </>
                  ) : (
                    <span className="text-muted">Cliente #{c.id ?? i + 1}</span>
                  )}
                </td>
                <td className="px-4 py-3 text-obsessed">{c.source ?? "—"}</td>
                <td className="px-4 py-3 text-obsessed">{c.status ?? "—"}</td>
                <td className="px-4 py-3 text-muted">{c.createdAt ? formatDateTime(c.createdAt) : "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/** Como "Descargar lista de clientes" de 21Online: CSV que abre en Excel. */
function DownloadClientsCsv({ rows, online21Id }: { rows: RelatedClients["clients"]; online21Id: string }) {
  const download = () => {
    const cell = (v: string | null | undefined) => `"${(v ?? "").replace(/"/g, '""')}"`;
    const lines = [
      ["Id", "Nombre", "Teléfono", "Correo", "Medio", "Estatus", "Fecha", "Mensaje"].join(","),
      ...rows.map((c) => [String(c.id ?? ""), c.name, c.phone, c.email, c.source, c.status, c.createdAt?.slice(0, 10), c.message].map(cell).join(",")),
    ];
    const blob = new Blob(["﻿" + lines.join("\r\n")], { type: "text/csv;charset=utf-8" });
    saveBlob(blob, `clientes-propiedad-${online21Id}.csv`);
  };
  return (
    <button type="button" onClick={download} className="inline-flex min-h-10 items-center gap-2 rounded-xl border border-border bg-card px-3.5 text-sm font-medium text-obsessed hover:border-gold/60">
      <Download className="h-4 w-4" aria-hidden /> Descargar lista de clientes
    </button>
  );
}

// --- Seguimientos ---------------------------------------------------------------------

function FollowUpsTab({ online21Id }: { online21Id: string }) {
  const followUps = useApi(() => getPropertyFollowUps(online21Id), [online21Id]);
  if (followUps.error && !followUps.data) return <LoadError message={followUps.error} onRetry={followUps.reload} />;
  if (!followUps.data) return <StateMessage kind="loading" title="Consultando seguimientos…" />;
  const list = followUps.data;
  if (list.length === 0) return <StateMessage kind="empty" title="Sin seguimientos" description="En 21Online no hay seguimientos (visitas, llamadas…) registrados para esta propiedad." />;
  return (
    <div className="overflow-hidden rounded-xl border border-border bg-card">
      <ul className="divide-y divide-border">
        {list.map((f, i) => (
          <li key={f.id ?? i} className="grid gap-1 px-4 py-3 sm:grid-cols-[10rem_minmax(0,1fr)_minmax(0,1fr)] sm:gap-4">
            <span className="text-sm text-muted">{f.createdAt ? formatDateTime(f.createdAt) : "—"}</span>
            <span className="text-sm font-medium text-obsessed">
              {capitalize(f.type) ?? "Seguimiento"}
              {f.subtype ? <span className="font-normal text-muted"> · {f.subtype}</span> : null}
            </span>
            <span className="text-sm text-obsessed">
              {f.client ? `Cliente: ${f.client}` : null}
              {f.advisor ? <span className="block text-xs text-muted">Asesor: {f.advisor}</span> : null}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

// --- Resumen ------------------------------------------------------------------------------

function SummaryTab({ online21Id, details }: { online21Id: string; details: PropertyDetails }) {
  const clients = useApi(() => getPropertyClients(online21Id), [online21Id]);
  const followUps = useApi(() => getPropertyFollowUps(online21Id), [online21Id]);
  const portals = useApi(() => getPropertyPortals(online21Id), [online21Id]);
  const error = clients.error ?? followUps.error ?? portals.error;
  if (error && !(clients.data && followUps.data && portals.data)) {
    return (
      <LoadError
        message={error}
        onRetry={() => {
          clients.reload();
          followUps.reload();
          portals.reload();
        }}
      />
    );
  }
  if (!clients.data || !followUps.data || !portals.data) return <StateMessage kind="loading" title="Armando el resumen…" />;

  const list = clients.data.clients;
  const now = new Date();
  const last30 = list.filter((c) => c.createdAt && now.getTime() - new Date(c.createdAt).getTime() <= 30 * 86_400_000).length;
  const visits = followUps.data.filter((f) => /visita|cita/i.test(`${f.type ?? ""} ${f.subtype ?? ""}`)).length;
  const activePortals = portals.data.filter((p) => p.active && !p.withError).length;
  const since = details.details.fields.find((f) => f.label === "En promoción desde")?.value ?? null;

  return (
    <div className="space-y-5">
      <section aria-label="Indicadores" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Tile label="Clientes interesados" value={list.length} hint={since ? `En promoción desde ${since}` : "Desde el alta en 21Online"} icon={<Users className="h-5 w-5" aria-hidden />} />
        <Tile label="Últimos 30 días" value={last30} hint="Clientes nuevos" icon={<Users className="h-5 w-5" aria-hidden />} />
        <Tile label="Visitas y citas" value={visits} hint={`${followUps.data.length} seguimientos en total`} icon={<MapPin className="h-5 w-5" aria-hidden />} />
        <Tile label="Portales activos" value={activePortals} hint={`${portals.data.length} asignados`} icon={<Globe className="h-5 w-5" aria-hidden />} />
      </section>
      <Card>
        <CardHeader title="Clientes interesados por mes" description="Últimos 12 meses, según la fecha en que llegó cada cliente a 21Online." />
        <ColumnChart data={byMonth(list.map((c) => c.createdAt))} title="Clientes interesados por mes" unit="clientes" />
      </Card>
      <div className="grid grid-cols-1 gap-5 lg:grid-cols-2">
        <Card>
          <CardHeader title="¿Por dónde llegaron?" description="Medio de origen de cada cliente (portal, redes, letrero…)." />
          <BarList data={countBy(list.map((c) => c.source ?? "Sin medio"))} unit="clientes" empty="Sin clientes todavía." />
        </Card>
        <Card>
          <CardHeader title="Seguimientos por tipo" description="Visitas, llamadas y demás seguimientos registrados en 21Online." />
          <BarList data={countBy(followUps.data.map((f) => capitalize(f.type) ?? "Sin tipo"))} unit="seguimientos" empty="Sin seguimientos todavía." />
        </Card>
      </div>
      <p className="text-xs text-muted">
        Las vistas de cada anuncio dentro de los portales (Mercado Libre, Inmuebles24…) no las comparte la API de 21Online; para eso abre el
        resumen en 21Online.
      </p>
    </div>
  );
}

function Tile({ label, value, hint, icon }: { label: string; value: number; hint: string; icon: ReactNode }) {
  return (
    <div className="rounded-xl border border-border bg-card p-4 shadow-[0_1px_2px_rgba(36,39,35,0.04)]">
      <div className="flex items-center justify-between text-muted">
        <span className="text-sm">{label}</span>
        {icon}
      </div>
      <p className="mt-2 text-3xl font-semibold tabular-nums text-obsessed">{value}</p>
      <p className="mt-1 text-xs text-muted">{hint}</p>
    </div>
  );
}

// --- Ficha técnica y compartir --------------------------------------------------------------

function SheetMenu({ id }: { id: number }) {
  const { showToast } = useToast();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState<SheetVariant | null>(null);
  const ref = useClickOutside(() => setOpen(false));

  const download = async (variant: SheetVariant) => {
    setBusy(variant);
    try {
      const blob = await getBlob(propertySheetPath(id, variant));
      saveBlob(blob, `ficha-${id}${variant === "ADVISOR" ? "" : variant === "NO_CONTACT" ? "-sin-contacto" : "-sin-logo"}.pdf`);
      setOpen(false);
    } catch (err) {
      showToast(errorText(err, "No se pudo generar la ficha técnica."));
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="inline-flex min-h-11 items-center gap-2 rounded-xl bg-gold px-4 text-sm font-medium text-brand-ink hover:bg-gold-hover focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/40"
      >
        <FileText className="h-4 w-4" aria-hidden /> Descargar ficha técnica <ChevronDown className="h-4 w-4" aria-hidden />
      </button>
      {open ? (
        <div className="absolute right-0 z-20 mt-2 w-72 overflow-hidden rounded-xl border border-border bg-card shadow-lg">
          {sheetVariants.map((v) => (
            <button
              key={v.id}
              type="button"
              disabled={busy !== null}
              onClick={() => download(v.id)}
              className="block w-full px-4 py-3 text-left hover:bg-app-bg disabled:opacity-60"
            >
              <span className="block text-sm font-medium text-obsessed">{busy === v.id ? "Generando…" : v.label}</span>
              <span className="block text-xs text-muted">{v.description}</span>
            </button>
          ))}
        </div>
      ) : null}
    </div>
  );
}

function ShareMenu({ details }: { details: PropertyDetails }) {
  const { showToast } = useToast();
  const [open, setOpen] = useState(false);
  const ref = useClickOutside(() => setOpen(false));
  const url = details.details.publicUrl;
  if (!url) return null;
  const l = details.details.listing;
  const text = `${l.title} · ${priceOf(l)}\n${url}`;
  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={open}
        className="inline-flex min-h-11 items-center gap-2 rounded-xl border border-border bg-card px-4 text-sm font-medium text-obsessed hover:border-gold/60"
      >
        Compartir <ChevronDown className="h-4 w-4" aria-hidden />
      </button>
      {open ? (
        <div className="absolute right-0 z-20 mt-2 w-60 overflow-hidden rounded-xl border border-border bg-card shadow-lg">
          <button
            type="button"
            className="flex w-full items-center gap-2 px-4 py-3 text-left text-sm text-obsessed hover:bg-app-bg"
            onClick={async () => {
              try {
                await navigator.clipboard.writeText(url);
                showToast("Liga copiada.");
              } catch {
                showToast("No se pudo copiar la liga.");
              }
              setOpen(false);
            }}
          >
            <Copy className="h-4 w-4" aria-hidden /> Copiar liga
          </button>
          <a
            href={`https://wa.me/?text=${encodeURIComponent(text)}`}
            target="_blank"
            rel="noreferrer"
            className="flex items-center gap-2 px-4 py-3 text-sm text-obsessed hover:bg-app-bg"
          >
            <MessageCircle className="h-4 w-4" aria-hidden /> WhatsApp
          </a>
          <a
            href={`https://www.facebook.com/sharer/sharer.php?u=${encodeURIComponent(url)}`}
            target="_blank"
            rel="noreferrer"
            className="flex items-center gap-2 px-4 py-3 text-sm text-obsessed hover:bg-app-bg"
          >
            <Globe className="h-4 w-4" aria-hidden /> Facebook
          </a>
        </div>
      ) : null}
    </div>
  );
}

// --- Utilidades ------------------------------------------------------------------------------

function useClickOutside(onOutside: () => void) {
  const ref = useRef<HTMLDivElement>(null);
  const handler = useRef(onOutside);
  useEffect(() => {
    handler.current = onOutside;
  });
  useEffect(() => {
    const listener = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) handler.current();
    };
    document.addEventListener("mousedown", listener);
    return () => document.removeEventListener("mousedown", listener);
  }, []);
  return ref;
}

function saveBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function groupBy(fields: DossierField[]): [string, DossierField[]][] {
  const map = new Map<string, DossierField[]>();
  for (const f of fields) map.set(f.section, [...(map.get(f.section) ?? []), f]);
  return [...map.entries()];
}

const monthFormat = new Intl.DateTimeFormat("es-MX", { month: "short", year: "2-digit" });

function byMonth(dates: (string | null)[]): ChartDatum[] {
  const now = new Date();
  const months: ChartDatum[] = [];
  const keys: string[] = [];
  for (let i = 11; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    keys.push(`${d.getFullYear()}-${d.getMonth()}`);
    months.push({ label: monthFormat.format(d).replace(".", ""), value: 0 });
  }
  for (const iso of dates) {
    if (!iso) continue;
    const d = new Date(iso);
    const index = keys.indexOf(`${d.getFullYear()}-${d.getMonth()}`);
    if (index >= 0) months[index].value += 1;
  }
  return months;
}

function countBy(values: string[]): ChartDatum[] {
  const map = new Map<string, number>();
  for (const v of values) map.set(v, (map.get(v) ?? 0) + 1);
  return [...map.entries()].map(([label, value]) => ({ label, value })).sort((a, b) => b.value - a.value);
}

function capitalize(s: string | null): string | null {
  return s ? s.charAt(0).toUpperCase() + s.slice(1).replace(/_/g, " ") : null;
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid grid-cols-[8.5rem_minmax(0,1fr)] gap-3">
      <dt className="text-muted">{label}</dt>
      <dd className="min-w-0 text-obsessed">{children}</dd>
    </div>
  );
}
