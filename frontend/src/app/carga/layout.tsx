import type { Metadata } from "next";
import type { ReactNode } from "react";

// La liga del cliente es su credencial: no debe indexarse ni filtrarse por el Referer.
export const metadata: Metadata = {
  title: "Tu expediente · CENTURY 21 Genera",
  robots: { index: false, follow: false },
  referrer: "no-referrer",
};

export default function CargaLayout({ children }: { children: ReactNode }) {
  return children;
}
