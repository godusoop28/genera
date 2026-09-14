"use client";

import { Button } from "@/components/ui/Button";
import { Modal } from "@/components/ui/Modal";
import { useToast } from "@/components/ui/Toast";
import { Copy, ExternalLink, Link2 } from "lucide-react";
import { useRouter } from "next/navigation";

const DEMO_LINK = "https://demo.century21.com/carga/demo-expediente";

interface GenerateLinkModalProps {
  open: boolean;
  onClose: () => void;
}

export function GenerateLinkModal({ open, onClose }: GenerateLinkModalProps) {
  const { showToast } = useToast();
  const router = useRouter();

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(DEMO_LINK);
      showToast("Liga copiada al portapapeles");
    } catch {
      showToast("No se pudo copiar la liga");
    }
  };

  const handleOpenClientView = () => {
    onClose();
    router.push("/carga/demo-expediente");
  };

  return (
    <Modal open={open} onClose={onClose} title="Liga generada">
      <div className="flex flex-col gap-4">
        <div className="flex items-center gap-3 rounded-xl border border-border bg-app-bg/60 px-4 py-3">
          <Link2 className="h-4 w-4 shrink-0 text-teal-dark" aria-hidden />
          <span className="truncate text-sm text-navy">{DEMO_LINK}</span>
        </div>
        <p className="text-sm text-muted">
          Comparte esta liga con el propietario para que suba sus documentos desde cualquier
          dispositivo.
        </p>
        <div className="flex flex-col gap-3 sm:flex-row">
          <Button variant="secondary" className="flex-1" onClick={handleCopy}>
            <Copy className="h-4 w-4" aria-hidden />
            Copiar liga
          </Button>
          <Button className="flex-1" onClick={handleOpenClientView}>
            <ExternalLink className="h-4 w-4" aria-hidden />
            Abrir vista del cliente
          </Button>
        </div>
      </div>
    </Modal>
  );
}
