"use client";

import { Modal } from "@/components/ui/Modal";
import { useToast } from "@/components/ui/Toast";
import { Download, Minus, Plus, Printer } from "lucide-react";
import { type ReactNode, useState } from "react";

interface PdfViewerModalProps {
  open: boolean;
  onClose: () => void;
  title?: string;
  children: ReactNode;
}

// Visor simulado de PDF. La barra de herramientas (zoom, paginación,
// descarga, imprimir) es decorativa: no hay generación real de PDF.
export function PdfViewerModal({ open, onClose, title, children }: PdfViewerModalProps) {
  const [zoom, setZoom] = useState(100);
  const { showToast } = useToast();

  return (
    <Modal open={open} onClose={onClose} size="xl" title={title}>
      <div className="-mx-4 -mt-4 mb-4 flex flex-wrap items-center justify-center gap-3 border-b border-border bg-app-bg/60 px-4 py-3 sm:-mx-6 sm:justify-between sm:px-6">
        <div className="flex items-center gap-2 text-sm text-obsessed">
          <button
            type="button"
            onClick={() => setZoom((z) => Math.max(50, z - 10))}
            className="rounded-lg px-2 py-1 hover:bg-card"
            aria-label="Reducir zoom"
          >
            <Minus className="h-3.5 w-3.5" aria-hidden />
          </button>
          <span className="w-12 text-center font-medium tabular-nums">{zoom}%</span>
          <button
            type="button"
            onClick={() => setZoom((z) => Math.min(150, z + 10))}
            className="rounded-lg px-2 py-1 hover:bg-card"
            aria-label="Aumentar zoom"
          >
            <Plus className="h-3.5 w-3.5" aria-hidden />
          </button>
        </div>

        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => showToast("Descarga simulada")}
            className="inline-flex items-center gap-1.5 text-sm font-medium text-obsessed hover:text-dark-gold"
          >
            <Download className="h-4 w-4" aria-hidden />
            Descargar
          </button>
          <button
            type="button"
            onClick={() => showToast("Impresión simulada")}
            className="inline-flex items-center gap-1.5 text-sm font-medium text-obsessed hover:text-dark-gold"
          >
            <Printer className="h-4 w-4" aria-hidden />
            Imprimir
          </button>
        </div>
      </div>

      <div className="-mx-4 flex justify-center overflow-auto bg-app-bg/40 p-3 sm:-mx-6 sm:p-6">
        {/* La "hoja" simula papel físico: se mantiene blanca a propósito en todos los temas. */}
        <div
          className="w-full max-w-2xl rounded-sm bg-white p-5 shadow-md sm:p-10"
          style={{ fontSize: `${zoom}%` }}
        >
          {children}
        </div>
      </div>
    </Modal>
  );
}
