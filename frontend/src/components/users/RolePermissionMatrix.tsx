"use client";

import { listPermissions, listRoles } from "@/lib/api/roles";
import type { PermissionResponse, RoleResponse } from "@/lib/api/types";
import { cn } from "@/lib/utils";
import { Check } from "lucide-react";
import { useEffect, useState } from "react";

export function RolePermissionMatrix() {
  const [roles, setRoles] = useState<RoleResponse[] | null>(null);
  const [permissions, setPermissions] = useState<PermissionResponse[]>([]);

  useEffect(() => {
    listRoles()
      .then(setRoles)
      .catch(() => setRoles([]));
    listPermissions()
      .then(setPermissions)
      .catch(() => undefined);
  }, []);

  if (roles === null) {
    return <p className="text-sm text-muted">Cargando roles…</p>;
  }

  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-3">
        {roles.map((role) => (
          <div key={role.id} className="rounded-2xl border border-border bg-card p-4">
            <p className="text-sm font-semibold text-obsessed">{role.name}</p>
            <p className="mt-1 text-xs text-muted">{role.description}</p>
          </div>
        ))}
      </div>

      <div className="overflow-x-auto rounded-2xl border border-border bg-card">
        <table className="w-full min-w-[640px] text-left text-sm">
          <thead className="border-b border-border bg-app-bg text-xs uppercase tracking-wide text-muted">
            <tr>
              <th className="px-5 py-3 font-medium">Permiso</th>
              {roles.map((role) => (
                <th key={role.id} className="px-5 py-3 text-center font-medium">
                  {role.name}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {permissions.map((perm) => (
              <tr key={perm.code}>
                <td className="px-5 py-3 text-obsessed">{perm.description}</td>
                {roles.map((role) => (
                  <td key={role.id} className="px-5 py-3 text-center">
                    {role.permissions.includes(perm.code) ? (
                      <Check className={cn("mx-auto h-4 w-4", "text-dark-gold")} aria-label="Incluido" />
                    ) : (
                      <span className="mx-auto block h-1 w-1 rounded-full bg-border" aria-hidden />
                    )}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
