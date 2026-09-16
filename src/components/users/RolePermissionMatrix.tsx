import { Check } from "lucide-react";
import { Fragment } from "react";
import { ALL_PERMISSIONS, ROLES } from "@/data/permissions";
import { cn } from "@/lib/utils";

export function RolePermissionMatrix() {
  const groups = Array.from(new Set(ALL_PERMISSIONS.map((p) => p.group)));

  return (
    <div className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-3">
        {ROLES.map((role) => (
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
              {ROLES.map((role) => (
                <th key={role.id} className="px-5 py-3 text-center font-medium">
                  {role.name.split(" / ")[0]}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {groups.map((group) => (
              <Fragment key={group}>
                <tr className="bg-app-bg/60">
                  <td colSpan={ROLES.length + 1} className="px-5 py-1.5 text-xs font-semibold uppercase tracking-wide text-muted">
                    {group}
                  </td>
                </tr>
                {ALL_PERMISSIONS.filter((p) => p.group === group).map((perm) => (
                  <tr key={perm.id}>
                    <td className="px-5 py-3 text-obsessed">
                      {perm.label}
                      {perm.id === "decidir_inmueble" ? (
                        <span className="ml-2 rounded-full bg-gold/15 px-2 py-0.5 text-[11px] font-medium text-dark-gold">
                          Permiso especial
                        </span>
                      ) : null}
                    </td>
                    {ROLES.map((role) => (
                      <td key={role.id} className="px-5 py-3 text-center">
                        {role.permissions.includes(perm.id) ? (
                          <Check className={cn("mx-auto h-4 w-4", "text-dark-gold")} aria-label="Incluido" />
                        ) : (
                          <span className="mx-auto block h-1 w-1 rounded-full bg-border" aria-hidden />
                        )}
                      </td>
                    ))}
                  </tr>
                ))}
              </Fragment>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
