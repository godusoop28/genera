"use client";

import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { useToast } from "@/components/ui/Toast";
import { KeyRound } from "lucide-react";
import { useState } from "react";

export function SecuritySettingsCard() {
  const { showToast } = useToast();
  const [open, setOpen] = useState(false);

  const handleChangePassword = () => {
    setOpen(false);
    showToast("Contraseña actualizada (simulado).");
  };

  return (
    <Card>
      <CardHeader title="Seguridad" description="No hay autenticación real en este prototipo." />
      <Button variant="secondary" onClick={() => setOpen(true)}>
        <KeyRound className="h-4 w-4" /> Cambiar contraseña
      </Button>

      <Modal
        open={open}
        onClose={() => setOpen(false)}
        title="Cambiar contraseña"
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)}>
              Cancelar
            </Button>
            <Button onClick={handleChangePassword}>Guardar</Button>
          </>
        }
      >
        <div className="flex flex-col gap-4">
          <Input label="Contraseña actual" type="password" placeholder="••••••••" />
          <Input label="Nueva contraseña" type="password" placeholder="••••••••" />
          <Input label="Confirmar nueva contraseña" type="password" placeholder="••••••••" />
          <p className="text-xs text-muted">Simulado: no se valida ni almacena ninguna contraseña real.</p>
        </div>
      </Modal>
    </Card>
  );
}
