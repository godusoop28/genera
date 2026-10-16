"use client";

import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { ApiError } from "@/lib/api/client";
import { getDownloadUrl, getViewer, getViewerPage } from "@/lib/api/documents";
import type { ViewerResponse } from "@/lib/api/types";
import { ChevronLeft, ChevronRight, Download, Loader2, Maximize2, Printer, ShieldCheck, X, ZoomIn, ZoomOut } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";

const MIN_ZOOM = 0.5;
const MAX_ZOOM = 4;
const clampZoom = (z: number) => Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, Math.round(z * 100) / 100));

/**
 * Visor de documentos sin descarga (Control de Cambios #6): el servidor manda cada página como
 * imagen con marca de agua de quien consulta; el archivo original nunca llega al navegador.
 * Descargar e imprimir el original solo aparece para quien tiene el permiso (administrador y
 * directivos de C21 Genera, Control de Cambios #16); el asesor solo consulta. Zoom con botones,
 * rueda del ratón o pellizco, y una X visible para cerrar.
 */
export function DocumentViewerModal({
  versionId,
  title,
  onClose,
}: {
  versionId: string | null;
  title: string;
  onClose: () => void;
}) {
  const [info, setInfo] = useState<ViewerResponse | null>(null);
  const [page, setPage] = useState(1);
  const [urls, setUrls] = useState<Record<number, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [zoom, setZoom] = useState(1);
  const loading = useRef(new Set<number>());
  const created = useRef<string[]>([]);
  const stage = useRef<HTMLDivElement>(null);
  const pointers = useRef(new Map<number, { x: number; y: number }>());
  const pinchStart = useRef<{ distance: number; zoom: number } | null>(null);

  // Rueda del ratón: acerca/aleja (sin desplazar la página de atrás). Necesita un listener no pasivo.
  useEffect(() => {
    const el = stage.current;
    if (!el) return;
    const onWheel = (e: WheelEvent) => {
      e.preventDefault();
      setZoom((z) => clampZoom(z * (e.deltaY < 0 ? 1.1 : 1 / 1.1)));
    };
    el.addEventListener("wheel", onWheel, { passive: false });
    return () => el.removeEventListener("wheel", onWheel);
  }, [info]);

  // Arrastrar para moverse por la página ampliada; dos dedos para pellizcar (zoom).
  const onPointerDown = (e: React.PointerEvent<HTMLDivElement>) => {
    pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY });
    e.currentTarget.setPointerCapture(e.pointerId);
    if (pointers.current.size === 2) {
      const [a, b] = [...pointers.current.values()];
      pinchStart.current = { distance: Math.hypot(a.x - b.x, a.y - b.y), zoom };
    }
  };
  const onPointerMove = (e: React.PointerEvent<HTMLDivElement>) => {
    const previous = pointers.current.get(e.pointerId);
    if (!previous) return;
    pointers.current.set(e.pointerId, { x: e.clientX, y: e.clientY });
    if (pointers.current.size === 2 && pinchStart.current) {
      const [a, b] = [...pointers.current.values()];
      const distance = Math.hypot(a.x - b.x, a.y - b.y);
      if (pinchStart.current.distance > 0) setZoom(clampZoom(pinchStart.current.zoom * (distance / pinchStart.current.distance)));
    } else if (pointers.current.size === 1) {
      e.currentTarget.scrollLeft -= e.clientX - previous.x;
      e.currentTarget.scrollTop -= e.clientY - previous.y;
    }
  };
  const onPointerUp = (e: React.PointerEvent<HTMLDivElement>) => {
    pointers.current.delete(e.pointerId);
    if (pointers.current.size < 2) pinchStart.current = null;
  };

  useEffect(() => {
    if (!versionId) return;
    // Quien lo usa le pone key={versionId}: cada documento abre el visor con su estado desde cero.
    let cancelled = false;
    getViewer(versionId)
      .then((v) => !cancelled && setInfo(v))
      .catch((err) => !cancelled && setError(err instanceof ApiError ? err.message : "No se pudo abrir el documento."));
    const toRevoke = created.current;
    const pending = loading.current;
    return () => {
      cancelled = true;
      toRevoke.forEach((u) => URL.revokeObjectURL(u));
      toRevoke.length = 0;
      pending.clear();
    };
  }, [versionId]);

  const load = useCallback(
    (n: number) => {
      if (!versionId || !info || n < 1 || n > info.pageCount || urls[n] || loading.current.has(n)) return;
      loading.current.add(n);
      getViewerPage(versionId, n)
        .then((blob) => {
          const url = URL.createObjectURL(blob);
          created.current.push(url);
          setUrls((prev) => ({ ...prev, [n]: url }));
        })
        .catch((err) => setError(err instanceof ApiError ? err.message : "No se pudo mostrar la página."))
        .finally(() => loading.current.delete(n));
    },
    [versionId, info, urls],
  );

  // La página actual y la siguiente (para que pasar de página sea inmediato), de una en una.
  useEffect(() => {
    load(page);
    if (urls[page]) load(page + 1);
  }, [page, load, urls]);

  // Sin imprimir ni guardar desde el teclado mientras el visor está abierto.
  useEffect(() => {
    if (!versionId) return;
    document.body.classList.add("c21-viewer-open");
    const onKeyDown = (e: KeyboardEvent) => {
      const key = e.key.toLowerCase();
      if ((e.ctrlKey || e.metaKey) && (key === "p" || key === "s")) e.preventDefault();
      if (key === "arrowright") setPage((p) => (info ? Math.min(info.pageCount, p + 1) : p));
      if (key === "arrowleft") setPage((p) => Math.max(1, p - 1));
      if (key === "+" || key === "=") setZoom((z) => clampZoom(z + 0.25));
      if (key === "-") setZoom((z) => clampZoom(z - 0.25));
    };
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.body.classList.remove("c21-viewer-open");
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [versionId, info]);

  const openOriginal = async () => {
    if (!versionId) return;
    const tab = window.open("about:blank", "_blank");
    try {
      const { url } = await getDownloadUrl(versionId);
      if (tab) tab.location.href = url;
    } catch (err) {
      tab?.close();
      setError(err instanceof ApiError ? err.message : "No se pudo descargar el documento.");
    }
  };

  const total = info?.pageCount ?? 0;
  return (
    <Modal open={versionId !== null} onClose={onClose} title={title} size="xl">
      <div className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <p className="flex items-center gap-1.5 text-xs text-muted">
            <ShieldCheck className="h-4 w-4 text-dark-gold" aria-hidden />
            {info?.canDownload ? "Vista protegida con marca de agua." : "Solo consulta: el documento no se puede descargar ni imprimir."}
          </p>
          <div className="flex flex-wrap items-center gap-2">
            <div className="flex items-center gap-1 rounded-lg border border-border bg-card p-0.5" role="group" aria-label="Zoom">
              <Button variant="ghost" size="sm" onClick={() => setZoom((z) => clampZoom(z - 0.25))} disabled={zoom <= MIN_ZOOM} aria-label="Alejar">
                <ZoomOut className="h-4 w-4" aria-hidden />
              </Button>
              <span className="w-12 text-center text-xs tabular-nums text-obsessed">{Math.round(zoom * 100)}%</span>
              <Button variant="ghost" size="sm" onClick={() => setZoom((z) => clampZoom(z + 0.25))} disabled={zoom >= MAX_ZOOM} aria-label="Acercar">
                <ZoomIn className="h-4 w-4" aria-hidden />
              </Button>
              <Button variant="ghost" size="sm" onClick={() => setZoom(1)} disabled={zoom === 1} aria-label="Ajustar a la ventana">
                <Maximize2 className="h-4 w-4" aria-hidden />
              </Button>
            </div>
            {info?.canDownload ? (
              <>
                <Button variant="secondary" size="sm" onClick={openOriginal}>
                  <Download className="h-4 w-4" aria-hidden /> Descargar original
                </Button>
                <Button variant="ghost" size="sm" onClick={openOriginal}>
                  <Printer className="h-4 w-4" aria-hidden /> Imprimir
                </Button>
              </>
            ) : null}
            <Button variant="secondary" size="sm" onClick={onClose} aria-label="Cerrar el visor">
              <X className="h-4 w-4" aria-hidden /> Cerrar
            </Button>
          </div>
        </div>

        <div
          ref={stage}
          className="c21-viewer relative flex h-[65vh] cursor-grab touch-none select-none overflow-auto rounded-lg bg-app-bg active:cursor-grabbing"
          onContextMenu={(e) => e.preventDefault()}
          onDragStart={(e) => e.preventDefault()}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerUp}
          onPointerCancel={onPointerUp}
        >
          {error ? (
            <p className="m-auto p-6 text-sm text-danger-text">{error}</p>
          ) : !info || !urls[page] ? (
            <Loader2 className="m-auto h-6 w-6 animate-spin text-muted" aria-label="Cargando página" />
          ) : (
            // eslint-disable-next-line @next/next/no-img-element -- imagen generada al vuelo, sin optimización de Next
            <img
              src={urls[page]}
              alt={`${title}, página ${page} de ${total}`}
              draggable={false}
              // m-auto (no justify-center): al ampliar, la página se puede recorrer completa sin quedar cortada.
              className={zoom === 1 ? "pointer-events-none m-auto max-h-[calc(65vh-1rem)] w-auto max-w-full" : "pointer-events-none m-auto w-auto max-w-none shrink-0"}
              style={zoom === 1 ? undefined : { height: `calc(${(65 * zoom).toFixed(2)}vh - 1rem)` }}
            />
          )}
        </div>
        <p className="text-center text-xs text-muted">Acerca o aleja con los botones, la rueda del ratón o pellizcando la pantalla; arrastra para moverte.</p>

        {total > 1 ? (
          <div className="flex items-center justify-center gap-3">
            <Button variant="secondary" size="sm" onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={page <= 1} aria-label="Página anterior">
              <ChevronLeft className="h-4 w-4" aria-hidden />
            </Button>
            <span className="text-sm tabular-nums text-obsessed">
              Página {page} de {total}
            </span>
            <Button variant="secondary" size="sm" onClick={() => setPage((p) => Math.min(total, p + 1))} disabled={page >= total} aria-label="Página siguiente">
              <ChevronRight className="h-4 w-4" aria-hidden />
            </Button>
          </div>
        ) : null}
      </div>
    </Modal>
  );
}
