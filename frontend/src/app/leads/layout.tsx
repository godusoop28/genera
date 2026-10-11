import { CrmShell } from "@/components/crm/CrmShell";
import type { ReactNode } from "react";

export default function CrmLayout({ children }: { children: ReactNode }) {
  return <CrmShell>{children}</CrmShell>;
}
