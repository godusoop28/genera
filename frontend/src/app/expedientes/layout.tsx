import { AppShell } from "@/components/layout/AppShell";
import type { ReactNode } from "react";

export default function ExpedientesLayout({ children }: { children: ReactNode }) {
  return <AppShell>{children}</AppShell>;
}
