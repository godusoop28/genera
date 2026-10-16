"use client";

import { Button } from "@/components/ui/Button";
import { getExpediente, getParticipants } from "@/lib/api/expedientes";
import type { ExpedienteResponse, ParticipantResponse } from "@/lib/api/types";
import {
  PRIVACY_CONSENT_TEXT,
  PRIVACY_INTRO,
  PRIVACY_RESPONSIBLE_ADDRESS,
  PRIVACY_RESPONSIBLE_LEGAL_NAME,
  PRIVACY_SECONDARY_OPT_OUT_TEXT,
  PRIVACY_SECTIONS,
  PRIVACY_SUBTITLE,
  PRIVACY_TITLE,
} from "@/data/privacy-reference";
import { Printer } from "lucide-react";
import { use, useEffect, useState } from "react";

/** Control de Cambios #2: aviso de privacidad impreso para que el titular lo firme en la oficina. */
export default function PrintablePrivacyNotice({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const [expediente, setExpediente] = useState<ExpedienteResponse | null>(null);
  const [participants, setParticipants] = useState<ParticipantResponse[]>([]);

  useEffect(() => {
    getExpediente(id).then(setExpediente).catch(() => undefined);
    getParticipants(id).then(setParticipants).catch(() => undefined);
  }, [id]);

  const signers = participants.filter((p) => p.role === "OWNER" || p.role === "CO_OWNER");

  return (
    <div className="mx-auto max-w-3xl bg-white p-8 text-sm leading-relaxed text-black print:p-0">
      <div className="mb-6 flex justify-end print:hidden">
        <Button onClick={() => window.print()}>
          <Printer className="h-4 w-4" aria-hidden /> Imprimir
        </Button>
      </div>
      <h1 className="text-xl font-semibold">{PRIVACY_TITLE}</h1>
      <p className="mb-1 font-medium">{PRIVACY_SUBTITLE}</p>
      <p className="mb-4 text-xs">
        {PRIVACY_RESPONSIBLE_LEGAL_NAME} · {PRIVACY_RESPONSIBLE_ADDRESS}
        {expediente ? ` · Expediente ${expediente.folio}` : ""}
      </p>
      <p className="mb-4">{PRIVACY_INTRO}</p>
      {PRIVACY_SECTIONS.map((s) => (
        <section key={s.id} className="mb-3">
          <h2 className="font-semibold">{s.title}</h2>
          <p>{s.text}</p>
        </section>
      ))}
      <p className="mt-6">{PRIVACY_CONSENT_TEXT}</p>
      <p className="mt-3">☐ {PRIVACY_SECONDARY_OPT_OUT_TEXT}</p>
      <p className="mt-6">Lugar y fecha: ____________________________________________</p>
      <div className="mt-10 grid gap-10 sm:grid-cols-2">
        {(signers.length > 0 ? signers.map((p) => p.fullName) : [""]).map((name, i) => (
          <div key={i} className="text-center">
            <div className="mb-1 border-t border-black pt-1">{name || "Nombre y firma del titular"}</div>
            <div className="text-xs">Nombre y firma</div>
          </div>
        ))}
      </div>
    </div>
  );
}
