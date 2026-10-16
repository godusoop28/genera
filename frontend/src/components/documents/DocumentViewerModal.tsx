"use client";

import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { ApiError } from "@/lib/api/client";
import { getDownloadUrl, getViewer, getViewerPage } from "@/lib/api/documents";
import type { ViewerResponse } from "@/lib/api/types";
import { ChevronLeft, ChevronRight, Download, Loader2, Printer, ShieldCheck } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";

/**
 * Visor de documentos sin descarga (Control de Cambios #6): el servidor manda cada página como
 * imagen con marca de agua de quien consulta; el archivo original nunca llega al navegador.
 * Descargar e imprimir el original solo aparece para quien tiene el permiso (administrador).
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
  const loading = useRef(new Set<number>());
  const created = useRef<string[]>([]);

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
          {info?.canDownload ? (
            <div className="flex gap-2">
              <Button variant="secondary" size="sm" onClick={openOriginal}>
                <Download className="h-4 w-4" aria-hidden /> Descargar original
              </Button>
              <Button variant="ghost" size="sm" onClick={openOriginal}>
                <Printer className="h-4 w-4" aria-hidden /> Imprimir
              </Button>
            </div>
          ) : null}
        </div>

        <div
          className="c21-viewer relative flex min-h-[60vh] select-none items-center justify-center overflow-auto rounded-lg bg-app-bg"
          onContextMenu={(e) => e.preventDefault()}
          onDragStart={(e) => e.preventDefault()}
        >
          {error ? (
            <p className="p-6 text-sm text-danger-text">{error}</p>
          ) : !info || !urls[page] ? (
            <Loader2 className="h-6 w-6 animate-spin text-muted" aria-label="Cargando página" />
          ) : (
            // eslint-disable-next-line @next/next/no-img-element -- imagen generada al vuelo, sin optimización de Next
            <img src={urls[page]} alt={`${title}, página ${page} de ${total}`} draggable={false} className="pointer-events-none max-h-[75vh] w-auto max-w-full" />
          )}
        </div>

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
