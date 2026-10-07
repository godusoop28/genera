"use client";

import { Button } from "@/components/ui/Button";
import { StateMessage } from "@/components/ui/StateMessage";
import { ApiError } from "@/lib/api/client";
import { listPermissions, listRoles } from "@/lib/api/roles";
import type { PermissionResponse, RoleResponse } from "@/lib/api/types";
import { Check, RefreshCw } from "lucide-react";
import { useCallback, useEffect, useState } from "react";

export function RolePermissionMatrix() {
  const [roles, setRoles] = useState<RoleResponse[] | null>(null);
  const [permissions, setPermissions] = useState<PermissionResponse[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    Promise.all([listRoles(), listPermissions()])
      .then(([r, p]) => {
        setRoles(r);
        setPermissions(p);
      })
      .catch((err) =>
        setError(
          err instanceof ApiError && err.status === 403
            ? "Tu sesión no tiene permiso para consultar los roles."
            : "No se pudieron cargar los roles y permisos. Intenta de nuevo.",
        ),
      );
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  if (error) {
    return (
      <StateMessage
        kind="error"
        title="No se pudieron cargar los roles"
        description={error}
        action={
          <Button
            variant="secondary"
            onClick={() => {
              setError(null);
              load();
            }}
          >
            <RefreshCw className="h-4 w-4" aria-hidden /> Reintentar
          </Button>
        }
      />
    );
  }

  if (roles === null) {
    return <StateMessage kind="loading" title="Cargando roles…" />;
  }

  return (
    <div className="space-y-5">
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        {roles.map((role) => (
          <div key={role.id} className="rounded-xl border border-border bg-card p-4 shadow-[0_1px_2px_rgba(36,39,35,0.04)]">
            <p className="text-sm font-semibold text-obsessed">{role.name}</p>
            <p className="mt-1 text-sm text-muted">{role.description}</p>
            <p className="mt-2 text-xs text-muted">
              {role.permissions.length} permiso{role.permissions.length === 1 ? "" : "s"}
            </p>
          </div>
        ))}
      </div>

      {/* La tabla puede desplazarse horizontalmente dentro de su tarjeta en pantallas angostas; la página no. */}
      <div className="overflow-x-auto rounded-xl border border-border bg-card shadow-[0_1px_2px_rgba(36,39,35,0.04)]">
        <table className="w-full min-w-[640px] text-left text-sm">
          <caption className="sr-only">Permisos incluidos en cada rol</caption>
          <thead className="border-b border-border bg-app-bg text-xs uppercase tracking-wide text-muted">
            <tr>
              <th scope="col" className="sticky left-0 bg-app-bg px-5 py-3 font-medium">
                Permiso
              </th>
              {roles.map((role) => (
                <th key={role.id} scope="col" className="px-4 py-3 text-center font-medium">
                  {role.name}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {permissions.map((perm) => (
              <tr key={perm.code}>
                <th scope="row" className="sticky left-0 bg-card px-5 py-3 font-normal text-obsessed">
                  {perm.description}
                </th>
                {roles.map((role) => (
                  <td key={role.id} className="px-4 py-3 text-center">
                    {role.permissions.includes(perm.code) ? (
                      <Check className="mx-auto h-4 w-4 text-success-text" aria-label="Incluido" />
                    ) : (
                      <span className="text-muted/60" aria-label="No incluido">
                        —
                      </span>
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
