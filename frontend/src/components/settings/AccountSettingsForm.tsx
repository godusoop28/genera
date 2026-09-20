"use client";

import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { useToast } from "@/components/ui/Toast";
import { useAuth } from "@/context/AuthProvider";
import { ApiError } from "@/lib/api/client";
import { updateUser } from "@/lib/api/users";
import type { BackendRoleCode } from "@/lib/api/types";
import { useState } from "react";

export function AccountSettingsForm() {
  const { user, refreshUser } = useAuth();
  const { showToast } = useToast();
  const [name, setName] = useState(user?.name ?? "");
  const [email, setEmail] = useState(user?.email ?? "");
  const [saving, setSaving] = useState(false);

  const handleSave = async () => {
    if (!user || !name.trim() || !email.trim()) return;
    setSaving(true);
    try {
      await updateUser(user.id, name.trim(), email.trim(), user.role as BackendRoleCode);
      await refreshUser();
      showToast("Datos de la cuenta actualizados.");
    } catch (err) {
      showToast(err instanceof ApiError ? `No se pudo guardar (${err.status}): ${err.message}` : "Error de conexión.");
    } finally {
      setSaving(false);
    }
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
        <Input label="Rol" value={user?.role ?? ""} readOnly containerClassName="sm:col-span-2" />
      </div>
      <div className="mt-4 flex justify-end">
        <Button onClick={handleSave} disabled={saving || !name.trim() || !email.trim()}>
          Guardar cambios
        </Button>
      </div>
    </Card>
  );
}
