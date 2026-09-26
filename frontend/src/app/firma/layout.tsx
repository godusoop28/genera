import type { Metadata } from "next";
import type { ReactNode } from "react";

// La liga de firma es personal: no debe indexarse ni filtrarse por el Referer.
export const metadata: Metadata = {
  title: "Firma de contrato · CENTURY 21 Genera",
  robots: { index: false, follow: false },
  referrer: "no-referrer",
};

export default function FirmaLayout({ children }: { children: ReactNode }) {
  return children;
}
