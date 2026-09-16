"use client";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { useToast } from "@/components/ui/Toast";
import { useDemoApp } from "@/context/DemoAppProvider";
import { generateId, makeActivity } from "@/lib/expediente-factory";
import type { Expediente } from "@/types/expediente";
import { Copy, ExternalLink, Link2, RotateCw, ShieldOff } from "lucide-react";
import { useRouter } from "next/navigation";

interface GenerateLinkModalProps {
  open: boolean;
  onClose: () => void;
  expediente: Expediente;
}

export function GenerateLinkModal({ open, onClose, expediente }: GenerateLinkModalProps) {
  const { showToast } = useToast();
  const { updateExpediente } = useDemoApp();
  const router = useRouter();

  const publicUrl = `https://documentos.c21genera.com/e/${expediente.linkId}`;
  const localPath = `/carga/${expediente.linkId}`;
  const revoked = expediente.linkStatus === "revoked";

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(publicUrl);
      showToast("Liga copiada al portapapeles.");
    } catch {
      showToast("No se pudo copiar la liga.");
    }
  };

  const handleOpenClientView = () => {
    onClose();
    router.push(localPath);
  };

  const handleRevoke = () => {
    updateExpediente(expediente.id, (current) => ({
      ...current,
      linkStatus: "revoked",
      activity: [makeActivity("liga_revocada", "La liga de recepción documental fue revocada.", new Date().toISOString()), ...current.activity],
    }));
    showToast("Liga revocada.");
  };

  const handleRegenerate = () => {
    updateExpediente(expediente.id, (current) => ({
      ...current,
      linkId: generateId("liga"),
      linkStatus: "active",
      activity: [makeActivity("liga_regenerada", "Se generó una nueva liga de recepción documental.", new Date().toISOString()), ...current.activity],
    }));
    showToast("Nueva liga generada.");
  };

  return (
    <Modal open={open} onClose={onClose} title="Liga de recepción documental">
      <div className="flex flex-col gap-4">
        <div className="flex items-center gap-3 rounded-xl border border-border bg-app-bg/60 px-4 py-3">
          <Link2 className="h-4 w-4 shrink-0 text-dark-gold" aria-hidden />
          <span className="truncate text-sm text-obsessed">{publicUrl}</span>
          {revoked ? <Badge tone="danger">Revocada</Badge> : <Badge tone="success">Activa</Badge>}
        </div>
        <p className="text-sm text-muted">
          Comparte esta liga con el propietario para que suba sus documentos desde cualquier
          dispositivo. <span className="font-medium text-obsessed">El cliente no necesita crear una cuenta.</span>
        </p>
        <div className="flex flex-col gap-3 sm:flex-row">
          <Button variant="secondary" className="flex-1" onClick={handleCopy}>
            <Copy className="h-4 w-4" aria-hidden />
            Copiar liga
          </Button>
          <Button className="flex-1" onClick={handleOpenClientView} disabled={revoked}>
            <ExternalLink className="h-4 w-4" aria-hidden />
            Abrir portal del cliente
          </Button>
        </div>
        <div className="flex flex-col gap-3 border-t border-border pt-4 sm:flex-row">
          <Button variant="ghost" className="flex-1" onClick={handleRegenerate}>
            <RotateCw className="h-4 w-4" aria-hidden />
            Regenerar liga
          </Button>
          <Button variant="danger" className="flex-1" onClick={handleRevoke} disabled={revoked}>
            <ShieldOff className="h-4 w-4" aria-hidden />
            Revocar liga
          </Button>
        </div>
      </div>
    </Modal>
  );
}
