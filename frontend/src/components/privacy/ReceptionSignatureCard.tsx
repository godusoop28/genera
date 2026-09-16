"use client";

import { SignatureMock } from "@/components/privacy/SignatureMock";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Modal } from "@/components/ui/Modal";
import { useToast } from "@/components/ui/Toast";
import { useDemoApp } from "@/context/DemoAppProvider";
import { formatDateEs } from "@/lib/calculations";
import { makeActivity } from "@/lib/expediente-factory";
import { roleHasPermission } from "@/data/permissions";
import type { Expediente } from "@/types/expediente";
import { FileSignature } from "lucide-react";
import { useState } from "react";

export function ReceptionSignatureCard({ expediente }: { expediente: Expediente }) {
  const { currentUser, updateExpediente } = useDemoApp();
  const { showToast } = useToast();
  const [open, setOpen] = useState(false);
  const [signatureDataUrl, setSignatureDataUrl] = useState<string | undefined>();

  const canSign = roleHasPermission(currentUser.roleId, "firmar_recepcion");
  const hasDocuments = Object.keys(expediente.documents).length > 0;

  if (expediente.receptionSignedAt) {
    return (
      <Card>
        <CardHeader title="Recepción documental" action={<Badge tone="success">Firmada</Badge>} />
        <p className="text-sm text-obsessed">
          Firmada por {expediente.receptionSignedBy} el {formatDateEs(expediente.receptionSignedAt)}.
        </p>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader title="Recepción documental" />
      {!hasDocuments ? (
        <p className="text-sm text-muted">Disponible cuando se hayan recibido documentos del cliente.</p>
      ) : !canSign ? (
        <p className="text-sm text-muted">Este rol no cuenta con permiso para firmar la recepción.</p>
      ) : (
        <Button onClick={() => setOpen(true)}>
          <FileSignature className="h-4 w-4" /> Firmar recepción documental
        </Button>
      )}

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title="Firmar recepción documental"
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>
              Cancelar
            </Button>
            <Button
              disabled={!signatureDataUrl}
              onClick={() => {
                const now = new Date().toISOString();
                updateExpediente(expediente.id, (current) => ({
                  ...current,
                  receptionSignedAt: now,
                  receptionSignedBy: currentUser.name,
                  activity: [
                    makeActivity("recepcion_firmada", `${currentUser.name} firmó la recepción documental.`, now),
                    ...current.activity,
                  ],
                }));
                setOpen(false);
                showToast("Recepción firmada.");
              }}
            >
              Confirmar firma
            </Button>
          </>
        }
      >
        <p className="mb-3 text-sm text-muted">
          Firmante: <span className="font-medium text-obsessed">{currentUser.name}</span> · Representante legal
        </p>
        <SignatureMock onSign={setSignatureDataUrl} signed={Boolean(signatureDataUrl)} />
      </Modal>
    </Card>
  );
}
