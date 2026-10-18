import { PropertyShell } from "@/components/crm/PropertyUi";
import type { ReactNode } from "react";

export default function PropertiesLayout({ children }: { children: ReactNode }) {
  return <PropertyShell>{children}</PropertyShell>;
}
