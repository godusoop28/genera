import type { Metadata } from "next";
import type { ReactNode } from "react";

// Control de Cambios #44: la liga para firmar el aviso es personal: no debe indexarse ni filtrarse por el Referer.
export const metadata: Metadata = {
  title: "Firma del aviso de privacidad · CENTURY 21 Genera",
  robots: { index: false, follow: false },
  referrer: "no-referrer",
};

export default function AvisoLayout({ children }: { children: ReactNode }) {
  return children;
}
