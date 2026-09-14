"use client";

import { Modal } from "@/components/ui/Modal";
import { useToast } from "@/components/ui/Toast";
import { contractMockText } from "@/data/mock-expediente";
import { Download, Minus, Plus, Printer } from "lucide-react";
import { useState } from "react";

interface PdfViewerModalProps {
  open: boolean;
  onClose: () => void;
}

const TOTAL_PAGES = 12;

export function PdfViewerModal({ open, onClose }: PdfViewerModalProps) {
  const [page, setPage] = useState(1);
  const [zoom, setZoom] = useState(100);
  const { showToast } = useToast();

  return (
    <Modal open={open} onClose={onClose} size="xl">
      <div className="-mx-6 -mt-4 mb-4 flex flex-wrap items-center justify-between gap-3 border-b border-border bg-app-bg/60 px-6 py-3">
        <div className="flex items-center gap-2 text-sm text-navy">
          <button
            type="button"
            onClick={() => setPage((p) => Math.max(1, p - 1))}
            className="rounded-lg px-2 py-1 hover:bg-white"
            aria-label="Página anterior"
          >
            <Minus className="h-3.5 w-3.5" aria-hidden />
          </button>
          <span className="font-medium tabular-nums">
            {page} / {TOTAL_PAGES}
          </span>
          <button
            type="button"
            onClick={() => setPage((p) => Math.min(TOTAL_PAGES, p + 1))}
            className="rounded-lg px-2 py-1 hover:bg-white"
            aria-label="Página siguiente"
          >
            <Plus className="h-3.5 w-3.5" aria-hidden />
          </button>
        </div>

        <div className="flex items-center gap-2 text-sm text-navy">
          <button
            type="button"
            onClick={() => setZoom((z) => Math.max(50, z - 10))}
            className="rounded-lg px-2 py-1 hover:bg-white"
            aria-label="Reducir zoom"
          >
            <Minus className="h-3.5 w-3.5" aria-hidden />
          </button>
          <span className="w-12 text-center font-medium tabular-nums">{zoom}%</span>
          <button
            type="button"
            onClick={() => setZoom((z) => Math.min(150, z + 10))}
            className="rounded-lg px-2 py-1 hover:bg-white"
            aria-label="Aumentar zoom"
          >
            <Plus className="h-3.5 w-3.5" aria-hidden />
          </button>
        </div>

        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => showToast("Descarga simulada")}
            className="inline-flex items-center gap-1.5 text-sm font-medium text-navy hover:text-teal-dark"
          >
            <Download className="h-4 w-4" aria-hidden />
            Descargar
          </button>
          <button
            type="button"
            onClick={() => showToast("Impresión simulada")}
            className="inline-flex items-center gap-1.5 text-sm font-medium text-navy hover:text-teal-dark"
          >
            <Printer className="h-4 w-4" aria-hidden />
            Imprimir
          </button>
        </div>
      </div>

      <div className="flex justify-center overflow-auto bg-app-bg/40 p-6">
        <div
          className="w-full max-w-2xl rounded-sm bg-white p-10 shadow-md"
          style={{ fontSize: `${zoom}%` }}
        >
          <h2 className="mb-6 text-center text-base font-bold tracking-wide text-navy">
            CONTRATO DE COMPRAVENTA
          </h2>
          <div className="whitespace-pre-line text-justify text-[13px] leading-relaxed text-navy/90">
            {contractMockText}
          </div>
        </div>
      </div>
    </Modal>
  );
}
