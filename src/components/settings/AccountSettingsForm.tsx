"use client";

import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { useToast } from "@/components/ui/Toast";
import { useDemoApp } from "@/context/DemoAppProvider";
import { ROLES } from "@/data/permissions";
import { useState } from "react";

export function AccountSettingsForm() {
  const { currentUser, updateUser } = useDemoApp();
  const { showToast } = useToast();
  const [name, setName] = useState(currentUser.name);
  const [email, setEmail] = useState(currentUser.email ?? "");
  const role = ROLES.find((r) => r.id === currentUser.roleId);

  const handleSave = () => {
    updateUser(currentUser.id, (u) => ({
      ...u,
      name: name.trim() || u.name,
      email: email.trim() || undefined,
    }));
    showToast("Datos de la cuenta actualizados.");
  };

  return (
    <Card>
      <CardHeader title="Cuenta" description="Datos del personal interno con la sesión activa." />
      <div className="grid gap-4 sm:grid-cols-2">
        <Input label="Nombre completo" value={name} onChange={(e) => setName(e.target.value)} />
        <Input
          label="Correo electrónico"
          type="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          placeholder="nombre@c21genera.com"
        />
        <Input label="Rol" value={role?.name ?? ""} readOnly containerClassName="sm:col-span-2" />
      </div>
      <div className="mt-4 flex justify-end">
        <Button onClick={handleSave}>Guardar cambios</Button>
      </div>
    </Card>
  );
}
