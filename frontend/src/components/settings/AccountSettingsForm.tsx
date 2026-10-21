"use client";

import { Card, CardHeader } from "@/components/ui/Card";
import { useAuth } from "@/context/AuthProvider";
import { roleLabels } from "@/lib/labels";
import { useCan } from "@/lib/permissions";
import Link from "next/link";

/**
 * Control de Cambios #25: "Mi perfil". Los datos de la cuenta y el rol los administra el
 * administrador (Usuarios y permisos); aquí solo se consultan. La contraseña se cambia en
 * {@link ChangePasswordCard}.
 */
export function AccountSettingsForm() {
  const { user } = useAuth();
  const can = useCan();

  return (
    <Card>
      <CardHeader title="Mi perfil" description="Datos de la cuenta con la sesión activa." />
      <dl className="grid gap-4 sm:grid-cols-2">
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-muted">Nombre</dt>
          <dd className="mt-0.5 text-sm text-obsessed">{user?.name ?? "—"}</dd>
        </div>
        <div>
          <dt className="text-xs font-medium uppercase tracking-wide text-muted">Correo de acceso</dt>
          <dd className="mt-0.5 break-all text-sm text-obsessed">{user?.email ?? "—"}</dd>
        </div>
        <div className="sm:col-span-2">
          <dt className="text-xs font-medium uppercase tracking-wide text-muted">Rol</dt>
          <dd className="mt-0.5 text-sm text-obsessed">{user ? (roleLabels[user.role] ?? user.role) : "—"}</dd>
        </div>
      </dl>
      <p className="mt-4 text-sm text-muted">
        {can("USER_MANAGE") ? (
          <>
            Tu nombre, correo y rol se cambian desde{" "}
            <Link href="/usuarios" className="font-medium text-dark-gold underline-offset-2 hover:underline">
              Usuarios y permisos
            </Link>{" "}
            (tu propio rol solo lo puede cambiar otro administrador).
          </>
        ) : (
          "Para cambiar tu nombre, correo o rol, pídeselo al administrador."
        )}
      </p>
    </Card>
  );
}
