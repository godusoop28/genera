import { AppShell } from "@/components/layout/AppShell";
import type { ReactNode } from "react";

export default function ConfiguracionLayout({ children }: { children: ReactNode }) {
  return <AppShell>{children}</AppShell>;
}
