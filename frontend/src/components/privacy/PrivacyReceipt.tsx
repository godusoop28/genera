import { Card, CardHeader } from "@/components/ui/Card";
import { ORG_LEGAL_REPRESENTATIVE } from "@/data/organization";
import { formatDateEs } from "@/lib/calculations";
import type { Expediente } from "@/types/expediente";
import { Check, X } from "lucide-react";

function SignatureRow({ label, signed }: { label: string; signed: boolean }) {
  return (
    <div className="flex items-center justify-between border-b border-border py-2 text-sm last:border-0">
      <span className="text-muted">{label}</span>
      {signed ? (
        <span className="inline-flex items-center gap-1 font-medium text-success-text">
          <Check className="h-3.5 w-3.5" /> Firmado
        </span>
      ) : (
        <span className="inline-flex items-center gap-1 text-muted">
          <X className="h-3.5 w-3.5" /> Pendiente
        </span>
      )}
    </div>
  );
}

// Resumen dinámico junto al "Formato interno de recepción documental" (fuente:
// aviso de privacidad simplificado). No sustituye ni altera el documento fuente.
export function PrivacyReceipt({ expediente }: { expediente: Expediente }) {
  return (
    <Card>
      <CardHeader title="Formato de recepción documental" description="Resumen dinámico del expediente" />
      <div className="grid gap-x-6 gap-y-1 text-sm sm:grid-cols-2">
        <p>
          <span className="text-muted">Fecha: </span>
          {expediente.privacyConsent.signedAt ? formatDateEs(expediente.privacyConsent.signedAt) : "—"}
        </p>
        <p>
          <span className="text-muted">Inmueble: </span>
          {expediente.propertyAddress ?? "—"}
        </p>
        <p>
          <span className="text-muted">Nombre de titular: </span>
          {expediente.ownerName}
        </p>
        <p>
          <span className="text-muted">Asesor: </span>
          {ORG_LEGAL_REPRESENTATIVE}
        </p>
        <p>
          <span className="text-muted">Teléfono/correo: </span>
          {expediente.manualData.phone ?? expediente.manualData.email ?? "—"}
        </p>
      </div>
      <div className="mt-4">
        <SignatureRow label="Firma titular" signed={Boolean(expediente.privacyConsent.signatureDataUrl)} />
        <SignatureRow label="Firma asesor" signed={Boolean(expediente.privacyConsent.signedAt)} />
        <SignatureRow label="Firma representante legal" signed={Boolean(expediente.receptionSignedAt)} />
      </div>
    </Card>
  );
}
