"use client";

import { DocumentUploadCard } from "@/components/documents/DocumentUploadCard";
import { DocumentValidationPanel } from "@/components/documents/DocumentValidationPanel";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { ORG_OFFICE_ADDRESS, ORG_OFFICE_MAPS_URL } from "@/data/organization";
import type { Expediente } from "@/types/expediente";
import { MapPin } from "lucide-react";
import { useState } from "react";

export function DocumentsStep({ expediente, onContinue }: { expediente: Expediente; onContinue: () => void }) {
  const requirements = expediente.documentRequirements.filter((r) => r.required);
  const [showOfficeInfo, setShowOfficeInfo] = useState(false);

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1.6fr_1fr]">
      <div className="flex flex-col gap-5">
        <div className="flex flex-col gap-2 rounded-xl border border-border bg-app-bg/50 p-4 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-muted">¿No puedes subir tus documentos desde aquí?</p>
          <Button variant="secondary" size="sm" onClick={() => setShowOfficeInfo(true)}>
            <MapPin className="h-4 w-4" aria-hidden />
            Ir a la oficina
          </Button>
        </div>

        {requirements.map((req) => (
          <DocumentUploadCard
            key={req.id}
            expedienteId={expediente.id}
            requirement={req}
            allowErrorDemo={req.id === "recibo-cfe"}
          />
        ))}

        <div className="flex justify-end">
          <Button size="lg" onClick={onContinue}>
            Continuar
          </Button>
        </div>
      </div>

      <div className="lg:sticky lg:top-6 lg:self-start">
        <DocumentValidationPanel />
      </div>

      <Modal open={showOfficeInfo} onClose={() => setShowOfficeInfo(false)} title="Visítanos en la oficina" size="sm">
        <p className="text-sm text-muted">
          Si no puedes subir tus documentos en línea, puedes entregarlos directamente en nuestra oficina:
        </p>
        <p className="mt-3 text-sm font-medium text-obsessed">{ORG_OFFICE_ADDRESS}</p>
        <a
          href={ORG_OFFICE_MAPS_URL}
          target="_blank"
          rel="noopener noreferrer"
          className="mt-3 inline-flex items-center gap-1.5 text-sm font-medium text-dark-gold hover:underline"
        >
          <MapPin className="h-4 w-4" aria-hidden />
          Ver ubicación en el mapa
        </a>
      </Modal>
    </div>
  );
}
