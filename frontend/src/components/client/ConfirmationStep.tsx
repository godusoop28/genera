"use client";

import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { useDemoApp } from "@/context/DemoAppProvider";
import { makeActivity } from "@/lib/expediente-factory";
import type { Expediente } from "@/types/expediente";
import { CheckCircle2, Circle, Send } from "lucide-react";
import { useState } from "react";

function Item({ done, label }: { done: boolean; label: string }) {
  return (
    <li className="flex items-center gap-2.5 text-sm">
      {done ? (
        <CheckCircle2 className="h-4 w-4 shrink-0 text-success-text" aria-hidden />
      ) : (
        <Circle className="h-4 w-4 shrink-0 text-muted" aria-hidden />
      )}
      <span className={done ? "text-obsessed" : "text-muted"}>{label}</span>
    </li>
  );
}

export function ConfirmationStep({ expediente }: { expediente: Expediente }) {
  const { updateExpediente } = useDemoApp();
  const [justSubmitted, setJustSubmitted] = useState(false);

  const requiredDocs = expediente.documentRequirements.filter((r) => r.required);
  const uploadedCount = requiredDocs.filter((r) => {
    const doc = expediente.documents[r.id];
    return doc && doc.status !== "pending";
  }).length;

  const privacyDone = expediente.privacyConsent.mainConsent;
  const dataDone = Boolean(expediente.manualData.email && expediente.manualData.phone);
  const docsDone = uploadedCount === requiredDocs.length && requiredDocs.length > 0;
  const signatureDone = Boolean(expediente.privacyConsent.signatureDataUrl);
  const alreadySent = expediente.status !== "waiting_privacy" && expediente.status !== "waiting_documents";

  const canSubmit = privacyDone && docsDone && signatureDone;

  const handleSubmit = () => {
    const now = new Date().toISOString();
    updateExpediente(expediente.id, (current) => ({
      ...current,
      status: "documents_received",
      activity: [
        makeActivity("ia_procesada", `Validación automática completada para ${requiredDocs.length} documentos.`, now),
        makeActivity("documentos_enviados", "El cliente envió su documentación.", now),
        ...current.activity,
      ],
    }));
    setJustSubmitted(true);
  };

  if (alreadySent || justSubmitted) {
    return (
      <Card className="flex flex-col items-center py-10 text-center">
        <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-success-bg">
          <CheckCircle2 className="h-7 w-7 text-success-text" aria-hidden />
        </div>
        <h3 className="text-lg font-semibold text-obsessed">Documentación enviada correctamente</h3>
        <p className="mt-2 max-w-md text-sm text-muted">
          CENTURY 21 Genera revisará la información y podrá solicitar correcciones. Puedes
          regresar a este mismo enlace para consultar el estado o subir un documento nuevo si te
          lo solicitan.
        </p>
      </Card>
    );
  }

  return (
    <Card>
      <h3 className="text-lg font-semibold text-obsessed">Confirmación</h3>
      <ul className="mt-4 space-y-2.5">
        <Item done={privacyDone} label="Aviso de privacidad aceptado" />
        <Item done={dataDone} label="Datos complementarios completos" />
        <Item done={docsDone} label={`${uploadedCount}/${requiredDocs.length} documentos cargados`} />
        <Item done={signatureDone} label="Firma registrada" />
      </ul>
      <div className="mt-6 flex justify-end">
        <Button size="lg" disabled={!canSubmit} onClick={handleSubmit}>
          <Send className="h-4 w-4" /> Enviar documentación
        </Button>
      </div>
    </Card>
  );
}
