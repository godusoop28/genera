"use client";

import { GenerateLinkModal } from "@/components/expediente/GenerateLinkModal";
import { SendDocumentsModal } from "@/components/email/SendDocumentsModal";
import { Button } from "@/components/ui/Button";
import { StatusBadge } from "@/components/ui/StatusBadge";
import type { Expediente } from "@/types/expediente";
import { Link2, Mail } from "lucide-react";
import { useState } from "react";

export function ExpedienteHeader({ expediente }: { expediente: Expediente }) {
  const [linkOpen, setLinkOpen] = useState(false);
  const [emailOpen, setEmailOpen] = useState(false);

  return (
    <div className="mb-6 flex flex-col gap-4 rounded-2xl border border-border bg-card p-5 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-xl font-semibold text-obsessed">{expediente.folio}</h1>
          <StatusBadge status={expediente.status} />
        </div>
        <p className="mt-1 text-sm text-muted">{expediente.ownerName}</p>
      </div>
      <div className="flex flex-wrap gap-2">
        <Button variant="secondary" size="sm" onClick={() => setLinkOpen(true)}>
          <Link2 className="h-3.5 w-3.5" /> Liga del cliente
        </Button>
        <Button variant="secondary" size="sm" onClick={() => setEmailOpen(true)}>
          <Mail className="h-3.5 w-3.5" /> Enviar por correo
        </Button>
      </div>

      <GenerateLinkModal open={linkOpen} onClose={() => setLinkOpen(false)} expediente={expediente} />
      <SendDocumentsModal open={emailOpen} onClose={() => setEmailOpen(false)} expediente={expediente} />
    </div>
  );
}
