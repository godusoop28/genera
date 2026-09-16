"use client";

import { ClientPortal } from "@/components/client/ClientPortal";
import { useDemoApp } from "@/context/DemoAppProvider";
import { use } from "react";

export default function CargaLinkPage({ params }: PageProps<"/carga/[linkId]">) {
  const { linkId } = use(params);
  const { getExpedienteByLink } = useDemoApp();
  const expediente = getExpedienteByLink(linkId);

  if (!expediente || expediente.linkStatus === "revoked") {
    return (
      <div className="flex min-h-screen items-center justify-center bg-app-bg px-4 text-center">
        <p className="text-sm text-muted">Esta liga no es válida o fue revocada.</p>
      </div>
    );
  }

  return <ClientPortal expediente={expediente} />;
}
