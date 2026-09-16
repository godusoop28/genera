"use client";

import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Modal } from "@/components/ui/Modal";
import { useToast } from "@/components/ui/Toast";
import { useDemoApp } from "@/context/DemoAppProvider";
import { RotateCcw } from "lucide-react";
import { useState } from "react";

export function DataResetCard() {
  const { resetDemoData } = useDemoApp();
  const { showToast } = useToast();
  const [open, setOpen] = useState(false);

  return (
    <Card>
      <CardHeader title="Datos de la demostración" description="Restablece expedientes y usuarios a su estado inicial." />
      <Button variant="danger" onClick={() => setOpen(true)}>
        <RotateCcw className="h-4 w-4" /> Restablecer datos de demostración
      </Button>

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title="Restablecer datos de demostración"
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>
              Cancelar
            </Button>
            <Button
              variant="danger"
              onClick={() => {
                resetDemoData();
                setOpen(false);
                showToast("Datos de la demostración restablecidos.");
              }}
            >
              Restablecer
            </Button>
          </>
        }
      >
        <p className="text-sm text-muted">
          Esto reemplaza todos los expedientes y usuarios creados durante esta sesión por los
          datos de ejemplo originales. El tema y el tamaño de fuente no se ven afectados.
        </p>
      </Modal>
    </Card>
  );
}
