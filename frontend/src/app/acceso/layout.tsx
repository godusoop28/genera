import type { Metadata } from "next";
import type { ReactNode } from "react";

// El enlace para definir contraseña es personal y de un solo uso: no debe indexarse ni filtrarse por el Referer.
export const metadata: Metadata = {
  title: "Definir contraseña · CENTURY 21 Genera",
  robots: { index: false, follow: false },
  referrer: "no-referrer",
};

export default function AccesoLayout({ children }: { children: ReactNode }) {
  return children;
}
