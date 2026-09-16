"use client";

import { PageContainer } from "@/components/layout/PageContainer";
import { RolePermissionMatrix } from "@/components/users/RolePermissionMatrix";
import { UsersTable } from "@/components/users/UsersTable";
import { cn } from "@/lib/utils";
import { useState } from "react";

const tabs = [
  { id: "usuarios", label: "Usuarios" },
  { id: "roles", label: "Roles y permisos" },
] as const;

type TabId = (typeof tabs)[number]["id"];

export default function UsuariosPage() {
  const [tab, setTab] = useState<TabId>("usuarios");

  return (
    <PageContainer
      title="Usuarios y permisos"
      subtitle="Personal interno de CENTURY 21 Genera. Los propietarios no tienen cuenta ni aparecen aquí."
    >
      <div className="mb-6 flex gap-1 border-b border-border">
        {tabs.map((t) => (
          <button
            key={t.id}
            onClick={() => setTab(t.id)}
            className={cn(
              "border-b-2 px-4 py-2.5 text-sm font-medium transition-colors",
              tab === t.id
                ? "border-gold text-obsessed"
                : "border-transparent text-muted hover:text-obsessed",
            )}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === "usuarios" ? <UsersTable /> : <RolePermissionMatrix />}
    </PageContainer>
  );
}
