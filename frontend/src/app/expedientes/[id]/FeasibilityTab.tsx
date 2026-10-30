"use client";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { MoneyInput } from "@/components/ui/MoneyInput";
import { useToast } from "@/components/ui/Toast";
import { getContractData } from "@/lib/api/contracts";
import {
  getFeasibilityDownload,
  listFeasibility,
  requestFeasibility,
  type ComparableGroup,
  type FeasibilityOperation,
  type FeasibilityResponse,
} from "@/lib/api/feasibility";
import { errorText } from "@/lib/errors";
import { shrinkImages } from "@/lib/image-shrink";
import { formatDateTime } from "@/lib/labels";
import { formatMoneyWithCurrency, moneyToRaw } from "@/lib/money";
import { FeasibilityChat } from "@/components/feasibility/FeasibilityChat";
import { Download, ExternalLink, ImagePlus, Loader2, MessagesSquare, Presentation, Sparkles, X } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ExpedienteContext } from "./page";

const MAX_PHOTOS = 10;
const POLL_MS = 15_000;

const classificationLabels: Record<string, { label: string; tone: "success" | "warning" | "danger" | "neutral" }> = {
  ALINEADO: { label: "Precio alineado al mercado", tone: "success" },
  LIMITE_SUPERIOR: { label: "En el límite superior", tone: "warning" },
  FUERA_DE_RANGO: { label: "Fuera del rango competitivo", tone: "danger" },
  SIN_DATOS: { label: "Sin evidencia suficiente", tone: "neutral" },
};

const groupLabels: Record<ComparableGroup, string> = {
  ARRIBA: "Por arriba",
  SIMILAR: "Similar",
  DEBAJO: "Por debajo",
};

/**
 * Factibilidad comercial con IA: con los documentos que envió el cliente, los datos del
 * expediente y una investigación de mercado en portales, genera el reporte "Analista de
 * Factibilidad 3.0" (Word) y la presentación para el propietario (PowerPoint).
 */
export function FeasibilityTab({ expediente }: ExpedienteContext) {
  const { showToast } = useToast();
  const [available, setAvailable] = useState(true);
  const [reports, setReports] = useState<FeasibilityResponse[] | null>(null);
  const [operation, setOperation] = useState<FeasibilityOperation>(expediente.operationType === "RENT" ? "RENT" : "SALE");
  const [askingPrice, setAskingPrice] = useState("");
  const [authorizedPrice, setAuthorizedPrice] = useState("");
  const [notes, setNotes] = useState("");
  const [photos, setPhotos] = useState<File[]>([]);
  const [sending, setSending] = useState(false);
  const [progress, setProgress] = useState<number | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  const load = useCallback(
    () =>
      listFeasibility(expediente.id)
        .then((r) => {
          setAvailable(r.available);
          setReports(r.reports);
        })
        .catch((err) => showToast(errorText(err))),
    [expediente.id, showToast],
  );

  useEffect(() => {
    load();
    // El precio autorizado del contrato es el punto de partida del precio solicitado.
    getContractData(expediente.id)
      .then((c) => {
        if (Number(c.price) > 0) {
          const raw = moneyToRaw(c.price);
          setAuthorizedPrice(raw);
          // El precio autorizado del contrato es de venta: no aplica a una renta mensual.
          if (expediente.operationType !== "RENT") setAskingPrice((current) => current || raw);
        }
      })
      .catch(() => undefined);
  }, [load, expediente.id, expediente.operationType]);

  const running = (reports ?? []).some((r) => r.status === "ANALYZING" || r.status === "PRESENTING");

  useEffect(() => {
    if (!running) return;
    const timer = window.setInterval(load, POLL_MS);
    return () => window.clearInterval(timer);
  }, [running, load]);

  const previews = useMemo(() => photos.map((file) => ({ file, url: URL.createObjectURL(file) })), [photos]);
  useEffect(() => () => previews.forEach((p) => URL.revokeObjectURL(p.url)), [previews]);

  const addPhotos = (list: FileList | null) => {
    if (!list) return;
    const images = Array.from(list).filter((f) => /^image\/(jpeg|png|webp)$/.test(f.type));
    if (images.length < list.length) showToast("Solo se aceptan fotografías JPG, PNG o WEBP.");
    setPhotos((current) => {
      const next = [...current, ...images].slice(0, MAX_PHOTOS);
      if (current.length + images.length > MAX_PHOTOS) showToast(`Se usan como máximo ${MAX_PHOTOS} fotografías.`);
      return next;
    });
  };

  const submit = async () => {
    setSending(true);
    setProgress(null);
    try {
      const shrunk = await shrinkImages(photos);
      // CC#38: el formato es solo visual; se manda el importe tal cual ("5000000" o "5000000.5").
      const price = askingPrice.replace(/[^0-9.]/g, "").replace(/\.$/, "");
      await requestFeasibility(expediente.id, { operation, askingPrice: price || undefined, notes, photos: shrunk }, (p) => setProgress(p.percent));
      setPhotos([]);
      showToast("Factibilidad en proceso. La IA está analizando los documentos e investigando el mercado; tarda varios minutos.");
      await load();
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setSending(false);
      setProgress(null);
    }
  };

  const download = async (reportId: string, file: "docx" | "pptx") => {
    const tab = window.open("about:blank", "_blank");
    try {
      const { url } = await getFeasibilityDownload(reportId, file);
      if (tab) tab.location.href = url;
    } catch (err) {
      tab?.close();
      showToast(errorText(err));
    }
  };

  return (
    <div className="flex flex-col gap-6">
      <Card>
        <CardHeader
          title="Factibilidad comercial con IA"
          description="Con los documentos que envió el cliente (predial, escritura, plano, avalúo y demás), los datos del expediente y una investigación de mercado en portales, la IA elabora el reporte de factibilidad (Word, 27 secciones) y la presentación para el propietario (PowerPoint editable, 12 láminas con comparables, mapa y precio recomendado)."
        />
        {!available ? (
          <p className="mb-4 rounded-lg bg-warning-bg px-3 py-2 text-sm text-warning-text">
            La inteligencia artificial no está configurada en el servidor; por ahora no se puede generar la factibilidad.
          </p>
        ) : null}
        <div className="grid gap-4 sm:grid-cols-2">
          <div className="sm:col-span-2">
            <p className="mb-1.5 text-sm font-medium text-obsessed">¿Para qué operación es la factibilidad?</p>
            <div className="inline-flex rounded-xl border border-border p-1" role="radiogroup" aria-label="Operación">
              {(
                [
                  ["SALE", "Venta"],
                  ["RENT", "Renta"],
                ] as const
              ).map(([value, label]) => (
                <button
                  key={value}
                  type="button"
                  role="radio"
                  aria-checked={operation === value}
                  onClick={() => {
                    if (value === operation) return;
                    setOperation(value);
                    // Cambiar de operación cambia el sentido del precio (total de venta vs. renta mensual).
                    setAskingPrice(value === "SALE" ? authorizedPrice : "");
                  }}
                  className={
                    operation === value
                      ? "rounded-lg bg-brand-ink px-4 py-1.5 text-sm font-medium text-white"
                      : "rounded-lg px-4 py-1.5 text-sm text-muted hover:bg-app-bg"
                  }
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
          <MoneyInput
            label={operation === "RENT" ? "Renta mensual solicitada por el propietario" : "Precio de venta solicitado por el propietario"}
            placeholder={operation === "RENT" ? "Ej. $25,000" : "Ej. $6,900,000"}
            value={askingPrice}
            onChange={setAskingPrice}
            hint={
              operation === "RENT"
                ? "Renta por mes. Si no la sabes, la IA la marca como dato faltante y propone una."
                : "Se toma del precio autorizado si ya se capturó. Si no hay precio, la IA lo marca como dato faltante."
            }
          />
          <div className="sm:row-span-2">
            <label className="mb-1.5 block text-sm font-medium text-obsessed" htmlFor="feasibility-notes">
              Notas del asesor (opcional)
            </label>
            <textarea
              id="feasibility-notes"
              value={notes}
              maxLength={4000}
              onChange={(e) => setNotes(e.target.value)}
              rows={6}
              placeholder="Lo que no está en los documentos: estado y remodelaciones, amenidades, vista, motivo de venta, urgencia del propietario, si ya estuvo publicada y a qué precio…"
              className="w-full rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm text-obsessed outline-none focus:border-gold"
            />
          </div>
          <div>
            <p className="mb-1.5 text-sm font-medium text-obsessed">Fotografías del inmueble (opcional, hasta {MAX_PHOTOS})</p>
            <p className="mb-2 text-xs text-muted">La primera se usa como portada de la presentación. La IA también las usa para valorar el estado del inmueble.</p>
            <input
              ref={fileInput}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              multiple
              className="hidden"
              onChange={(e) => {
                addPhotos(e.target.files);
                e.target.value = "";
              }}
            />
            <Button type="button" variant="secondary" size="sm" onClick={() => fileInput.current?.click()} disabled={photos.length >= MAX_PHOTOS}>
              <ImagePlus className="h-4 w-4" /> Agregar fotos
            </Button>
          </div>
        </div>
        {previews.length > 0 ? (
          <ul className="mt-4 flex flex-wrap gap-2">
            {previews.map((p, i) => (
              <li key={p.url} className="relative h-20 w-28 overflow-hidden rounded-lg border border-border">
                {/* eslint-disable-next-line @next/next/no-img-element -- vista previa local (blob:) */}
                <img src={p.url} alt={`Foto ${i + 1}`} className="h-full w-full object-cover" />
                {i === 0 ? <span className="absolute left-1 top-1 rounded bg-brand-ink/80 px-1.5 text-[10px] text-white">Portada</span> : null}
                <button
                  type="button"
                  aria-label="Quitar foto"
                  onClick={() => setPhotos((current) => current.filter((f) => f !== p.file))}
                  className="absolute right-1 top-1 rounded-full bg-white/90 p-0.5 text-obsessed hover:bg-white"
                >
                  <X className="h-3.5 w-3.5" />
                </button>
              </li>
            ))}
          </ul>
        ) : null}
        <div className="mt-5 flex flex-wrap items-center gap-3">
          <Button onClick={submit} disabled={!available || sending || running}>
            {sending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
            {sending ? (progress !== null && progress < 100 ? `Subiendo fotos ${progress}%` : "Enviando…") : "Generar factibilidad con IA"}
          </Button>
          {running ? <p className="text-sm text-muted">Hay una factibilidad en proceso; espera a que termine para generar otra.</p> : null}
        </div>
        <p className="mt-3 text-xs text-muted">
          Es una estimación comercial orientativa elaborada con IA: no sustituye un avalúo. Los precios de portales son precios de oferta, no de cierre.
          Revisa el reporte antes de presentarlo al propietario.
        </p>
      </Card>

      {reports === null ? (
        <p className="text-sm text-muted">Cargando…</p>
      ) : reports.length === 0 ? (
        <p className="text-sm text-muted">Todavía no se ha generado ninguna factibilidad para este expediente.</p>
      ) : (
        reports.map((r) => <ReportCard key={r.id} report={r} onDownload={download} />)
      )}
    </div>
  );
}

function ReportCard({ report: r, onDownload }: { report: FeasibilityResponse; onDownload: (id: string, file: "docx" | "pptx") => void }) {
  const s = r.summary;
  const classification = s?.classification ? classificationLabels[s.classification] : null;
  const rent = r.operation === "RENT";
  const [chatOpen, setChatOpen] = useState(false);
  // CC#38: importes con separadores de miles y moneda ("$5,000,000 MXN").
  const money = (value: number | null) => (value === null ? "—" : rent ? `${formatMoneyWithCurrency(value)}/mes` : formatMoneyWithCurrency(value));
  return (
    <Card>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <h3 className="font-semibold text-obsessed">Factibilidad v{r.versionNumber}</h3>
          <Badge tone="obsessed">{r.operation === "RENT" ? "Renta" : "Venta"}</Badge>
          <StatusBadge status={r.status} />
          {classification ? <Badge tone={classification.tone}>{classification.label}</Badge> : null}
        </div>
        <p className="text-xs text-muted">
          {formatDateTime(r.requestedAt)}
          {r.requestedByName ? ` · ${r.requestedByName}` : ""}
        </p>
      </div>

      {r.status === "ANALYZING" || r.status === "PRESENTING" ? <Progress report={r} /> : null}
      {chatOpen ? <FeasibilityChat reportId={r.id} versionNumber={r.versionNumber} open onClose={() => setChatOpen(false)} /> : null}

      {r.status === "FAILED" ? (
        <p className="rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger-text">{r.error ?? "No se pudo completar la factibilidad."} Puedes volver a generarla.</p>
      ) : null}

      {r.status === "READY" ? (
        <>
          <div className="mb-4 flex flex-wrap gap-2">
            <Button variant="dark" size="sm" onClick={() => onDownload(r.id, "docx")} disabled={!r.hasDocx}>
              <Download className="h-4 w-4" /> Reporte de factibilidad (Word)
            </Button>
            <Button size="sm" onClick={() => onDownload(r.id, "pptx")} disabled={!r.hasPptx}>
              <Presentation className="h-4 w-4" /> Presentación para el propietario (PowerPoint)
            </Button>
            <Button variant="secondary" size="sm" onClick={() => setChatOpen(true)}>
              <MessagesSquare className="h-4 w-4" /> Consultar a la IA sobre esta factibilidad
            </Button>
          </div>
          {s ? (
            <>
              <dl className="mb-4 grid grid-cols-2 gap-3 text-sm sm:grid-cols-4">
                <Info k="Expectativa del propietario" v={money(s.ownerPrice)} />
                <Info k={rent ? "Rango de renta sugerido" : "Rango sugerido"} v={s.rangeMin && s.rangeMax ? `${formatMoneyWithCurrency(s.rangeMin)} – ${money(s.rangeMax)}` : "—"} />
                <Info k={rent ? "Renta mensual recomendada" : "Precio recomendado de salida"} v={money(s.recommendedPrice)} strong />
                <Info k="Comparables" v={String(s.comparables.length)} />
              </dl>
              {s.executiveSummary ? <p className="mb-3 whitespace-pre-line text-sm text-obsessed">{s.executiveSummary}</p> : null}
              {s.strategy ? <p className="mb-3 text-sm text-muted">{s.strategy}</p> : null}
              {s.comparables.length > 0 ? (
                <details className="mb-3 rounded-lg bg-app-bg px-3 py-2 text-sm">
                  <summary className="cursor-pointer font-medium text-obsessed">Comparables y sus enlaces ({s.comparables.length})</summary>
                  <ul className="mt-2 flex flex-col gap-1.5">
                    {s.comparables.map((c) => (
                      <li key={c.id} className="flex flex-wrap items-center gap-x-2 gap-y-0.5">
                        <span className="w-9 font-semibold text-obsessed">{c.id}</span>
                        <span className="text-xs text-muted">{groupLabels[c.group] ?? c.group}</span>
                        <span>{c.zone ?? "—"}</span>
                        <span className="font-medium">{money(c.price)}</span>
                        {c.url ? (
                          <a href={c.url} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 text-info-text underline">
                            Ver publicación <ExternalLink className="h-3 w-3" />
                          </a>
                        ) : (
                          <span className="text-xs text-muted">Sin liga</span>
                        )}
                        {c.url && !c.urlVerified ? <span className="text-xs text-warning-text">liga no verificada</span> : null}
                      </li>
                    ))}
                  </ul>
                </details>
              ) : null}
              {s.pendingItems.length > 0 ? (
                <details className="rounded-lg bg-warning-bg px-3 py-2 text-sm text-warning-text">
                  <summary className="cursor-pointer font-medium">Información pendiente o no verificable ({s.pendingItems.length})</summary>
                  <ul className="ml-5 mt-1 list-disc">
                    {s.pendingItems.map((item) => (
                      <li key={item}>{item}</li>
                    ))}
                  </ul>
                </details>
              ) : null}
            </>
          ) : null}
        </>
      ) : null}
    </Card>
  );
}

function Progress({ report }: { report: FeasibilityResponse }) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, []);
  const minutes = Math.max(0, Math.floor((now - new Date(report.requestedAt).getTime()) / 60_000));
  const stage =
    report.status === "ANALYZING"
      ? "Etapa 1 de 2: analizando los documentos e investigando el mercado (comparables, precios y absorción)."
      : "Etapa 2 de 2: preparando la presentación para el propietario (15 comparables, mapa y precio recomendado).";
  return (
    <div className="flex items-start gap-3 rounded-lg bg-info-bg px-3 py-2 text-sm text-info-text">
      <Loader2 className="mt-0.5 h-4 w-4 shrink-0 animate-spin" />
      <div>
        <p>{stage}</p>
        <p className="text-xs">
          {minutes === 0 ? "Comenzó hace menos de un minuto." : `Lleva ${minutes} min.`} Suele tardar entre 5 y 20 minutos; puedes salir de esta pantalla, el
          trabajo sigue en el servidor.
        </p>
      </div>
    </div>
  );
}

function StatusBadge({ status }: { status: FeasibilityResponse["status"] }) {
  switch (status) {
    case "READY":
      return <Badge tone="success">Lista</Badge>;
    case "FAILED":
      return <Badge tone="danger">Falló</Badge>;
    default:
      return <Badge tone="info">En proceso</Badge>;
  }
}

function Info({ k, v, strong }: { k: string; v: string; strong?: boolean }) {
  return (
    <div>
      <dt className="text-xs text-muted">{k}</dt>
      <dd className={strong ? "font-semibold text-obsessed" : "text-obsessed"}>{v}</dd>
    </div>
  );
}
