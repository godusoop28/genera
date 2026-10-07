"use client";

import { PageContainer } from "@/components/layout/PageContainer";
import { Button } from "@/components/ui/Button";
import { StateMessage } from "@/components/ui/StateMessage";
import { Tabs } from "@/components/ui/Tabs";
import { RolePermissionMatrix } from "@/components/users/RolePermissionMatrix";
import { UsersTable } from "@/components/users/UsersTable";
import { useCan } from "@/lib/permissions";
import { Plus } from "lucide-react";
import { useState } from "react";

const tabs = [
  { id: "usuarios", label: "Usuarios" },
  { id: "roles", label: "Roles y permisos" },
] as const;

type TabId = (typeof tabs)[number]["id"];

export default function UsuariosPage() {
  const [tab, setTab] = useState<TabId>("usuarios");
  const [createOpen, setCreateOpen] = useState(false);
  const can = useCan();

  // Administrar usuarios requiere USER_MANAGE (solo el rol Administrador). El
  // backend responde 403 a cualquier otro rol; aquí se explica en lugar de mostrar el error.
  if (!can("USER_MANAGE")) {
    return (
      <PageContainer title="Usuarios y permisos">
        <StateMessage
          kind="restricted"
          title="Acceso restringido"
          description="Solo un administrador puede ver y gestionar los usuarios y sus permisos. Si necesitas acceso, pídeselo a un administrador."
        />
      </PageContainer>
    );
  }

  return (
    <PageContainer
      title="Usuarios y permisos"
      subtitle="Organiza al personal interno y controla sus accesos. Los propietarios no tienen cuenta ni aparecen aquí."
      action={
        <Button
          onClick={() => {
            setTab("usuarios");
            setCreateOpen(true);
          }}
        >
          <Plus className="h-4 w-4" aria-hidden /> Crear usuario
        </Button>
      }
    >
      <Tabs tabs={tabs} value={tab} onChange={setTab} label="Secciones de usuarios y permisos" idPrefix="usuarios" className="mb-6" />

      <div role="tabpanel" id={`usuarios-panel-${tab}`} aria-labelledby={`usuarios-tab-${tab}`}>
        {tab === "usuarios" ? (
          <UsersTable createOpen={createOpen} onCreateOpenChange={setCreateOpen} onShowRoles={() => setTab("roles")} />
        ) : (
          <RolePermissionMatrix />
        )}
      </div>
    </PageContainer>
  );
}
