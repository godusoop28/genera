import { RequireAuth } from "@/components/auth/RequireAuth";
import { AppShell } from "@/components/layout/AppShell";
import type { ReactNode } from "react";

// CC#32: el acceso se valida en el backend (cada asesor ve solo sus operaciones).
export default function OperacionesLayout({ children }: { children: ReactNode }) {
  return (
    <RequireAuth>
      <AppShell>{children}</AppShell>
    </RequireAuth>
  );
}
