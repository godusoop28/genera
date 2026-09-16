"use client";

import { ClientPortal } from "@/components/client/ClientPortal";
import { useDemoApp } from "@/context/DemoAppProvider";

export default function CargaDemoExpedientePage() {
  const { getExpediente } = useDemoApp();
  const expediente = getExpediente("demo");

  if (!expediente) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-app-bg px-4 text-center">
        <p className="text-sm text-muted">Expediente de demostración no disponible.</p>
      </div>
    );
  }

  return <ClientPortal expediente={expediente} />;
}
