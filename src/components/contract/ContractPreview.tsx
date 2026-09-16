"use client";

import { PdfViewerModal } from "@/components/documents/PdfViewerModal";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import {
  CONTRACT_ANNEX_A_FIELDS,
  CONTRACT_ANNEX_B_ITEMS,
  CONTRACT_ANNEX_C_RIGHTS,
  CONTRACT_CLAUSES,
  CONTRACT_DECLARATIONS,
  CONTRACT_INTERMEDIARY_COMMERCIAL_NAME,
  CONTRACT_LEGAL_REPRESENTATIVE,
  CONTRACT_PROFECO_NUMBER,
  CONTRACT_PROFECO_REGISTRATION_DATE,
  CONTRACT_TITLE,
} from "@/data/contract-reference";
import type { ContractCalculations, Expediente } from "@/types/expediente";
import { Maximize2 } from "lucide-react";
import { useState } from "react";

// El contenido del "papel" simula un documento impreso: usa colores fijos
// (no reactivos al tema) para que siga siendo legible sobre fondo blanco en
// cualquier tema de la aplicación.
function ContractDocumentContent({ expediente, calculations }: { expediente: Expediente; calculations: ContractCalculations }) {
  return (
    <div className="text-[13px] leading-relaxed text-neutral-800">
      <p className="mb-1 text-center text-xs font-medium uppercase tracking-wide text-dark-gold">
        Vista previa de prototipo — no es el documento oficial generado
      </p>
      <h2 className="mb-1 text-center text-base font-bold tracking-wide text-neutral-900">{CONTRACT_TITLE}</h2>
      <p className="mb-6 text-center text-xs text-neutral-500">
        Registrado ante PROFECO {CONTRACT_PROFECO_NUMBER} · {CONTRACT_PROFECO_REGISTRATION_DATE}
      </p>

      <p className="mb-4 text-justify">
        Contrato que celebran, por una parte, Grupo WILGEN y Asociados S. de R.L. de C.V. ({CONTRACT_INTERMEDIARY_COMMERCIAL_NAME}),
        representada por {CONTRACT_LEGAL_REPRESENTATIVE} (&ldquo;la intermediaria&rdquo;), y por la otra,{" "}
        <span className="font-medium">{expediente.ownerName}</span>, en su carácter de{" "}
        <span className="font-medium">{expediente.config.signerCharacter}</span> (&ldquo;el cliente&rdquo;).
      </p>

      <h3 className="mb-2 mt-6 font-semibold">Declaraciones</h3>
      {CONTRACT_DECLARATIONS.map((d) => (
        <div key={d.id} className="mb-3">
          <p className="font-medium">{d.title}</p>
          <p className="text-justify text-neutral-700">{d.text}</p>
        </div>
      ))}

      <h3 className="mb-2 mt-6 font-semibold">Cláusulas</h3>
      {CONTRACT_CLAUSES.map((c) => (
        <div key={c.id} className="mb-3">
          <p className="font-medium">{c.title}</p>
          <p className="text-justify text-neutral-700">{c.text}</p>
        </div>
      ))}

      <h3 className="mb-2 mt-6 font-semibold">Anexo A — Características del inmueble</h3>
      <ul className="mb-4 list-disc pl-5 text-neutral-700">
        {CONTRACT_ANNEX_A_FIELDS.map((f) => (
          <li key={f}>{f}</li>
        ))}
      </ul>

      <h3 className="mb-2 mt-6 font-semibold">Anexo B — Información puesta a disposición del cliente</h3>
      <ul className="mb-4 list-disc pl-5 text-neutral-700">
        {CONTRACT_ANNEX_B_ITEMS.map((f) => (
          <li key={f}>{f}</li>
        ))}
      </ul>

      <h3 className="mb-2 mt-6 font-semibold">Anexo C — Carta de derechos del cliente</h3>
      <ul className="mb-4 list-disc pl-5 text-neutral-700">
        {CONTRACT_ANNEX_C_RIGHTS.map((f) => (
          <li key={f}>{f}</li>
        ))}
      </ul>

      <div className="mt-6 border-t border-neutral-200 pt-4 text-xs text-neutral-500">
        <p>Precio autorizado: {calculations.priceWritten || "sin definir"}</p>
        <p>Comisión + IVA: calculada automáticamente al capturar el precio autorizado.</p>
      </div>
    </div>
  );
}

export function ContractPreview({ expediente, calculations }: { expediente: Expediente; calculations: ContractCalculations }) {
  const [open, setOpen] = useState(false);

  return (
    <Card>
      <CardHeader
        title="Vista previa del contrato"
        action={
          <Button variant="secondary" size="sm" onClick={() => setOpen(true)}>
            <Maximize2 className="h-3.5 w-3.5" aria-hidden />
            Ver documento completo
          </Button>
        }
      />

      <button
        type="button"
        onClick={() => setOpen(true)}
        className="block w-full overflow-hidden rounded-xl border border-border bg-app-bg/40 p-4 text-left transition-shadow hover:shadow-sm"
      >
        <div className="mx-auto max-h-72 max-w-xs overflow-hidden rounded-sm bg-white p-5 shadow-sm">
          <Badge tone="gold" className="mb-2">Vista previa de prototipo</Badge>
          <h3 className="mb-3 text-center text-[11px] font-bold tracking-wide text-neutral-900">{CONTRACT_TITLE}</h3>
          <p className="line-clamp-[8] text-justify text-[9px] leading-relaxed text-neutral-700">
            {CONTRACT_DECLARATIONS[0].text}
          </p>
        </div>
      </button>

      <PdfViewerModal open={open} onClose={() => setOpen(false)} title={CONTRACT_TITLE}>
        <ContractDocumentContent expediente={expediente} calculations={calculations} />
      </PdfViewerModal>
    </Card>
  );
}
