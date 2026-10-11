import { RequireAuth } from "@/components/auth/RequireAuth";
import { CrmGate } from "@/components/crm/CrmUi";
import { AppShell } from "@/components/layout/AppShell";
import type { ReactNode } from "react";

/** Layout común de las pantallas del CRM (sesión + menú + acceso de vista previa). */
export function CrmShell({ children }: { children: ReactNode }) {
  return (
    <RequireAuth>
      <AppShell>
        <CrmGate>{children}</CrmGate>
      </AppShell>
    </RequireAuth>
  );
}
