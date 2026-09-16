"use client";

import { DocumentUploadCard } from "@/components/documents/DocumentUploadCard";
import { DocumentValidationPanel } from "@/components/documents/DocumentValidationPanel";
import { Button } from "@/components/ui/Button";
import type { Expediente } from "@/types/expediente";

export function DocumentsStep({ expediente, onContinue }: { expediente: Expediente; onContinue: () => void }) {
  const requirements = expediente.documentRequirements.filter((r) => r.required);

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[1.6fr_1fr]">
      <div className="flex flex-col gap-5">
        {requirements.map((req) => (
          <DocumentUploadCard
            key={req.id}
            expedienteId={expediente.id}
            requirement={req}
            allowErrorDemo={req.id === "domicilio"}
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
    </div>
  );
}
