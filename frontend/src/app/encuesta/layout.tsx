import { BrandLogo } from "@/components/brand/BrandLogo";
import type { Metadata } from "next";
import type { ReactNode } from "react";

// Control de Cambios #28: página pública de la encuesta NPS (el token de la liga es la credencial).
export const metadata: Metadata = {
  title: "Tu opinión · CENTURY 21 Genera",
  robots: { index: false, follow: false },
  referrer: "no-referrer",
};

export default function EncuestaLayout({ children }: { children: ReactNode }) {
  return (
    <div className="flex min-h-screen flex-col bg-app-bg">
      <header className="border-b border-border bg-card">
        <div className="mx-auto flex max-w-2xl items-center px-4 py-4">
          <BrandLogo tone="light" size="sm" />
        </div>
      </header>
      <main className="mx-auto w-full max-w-2xl flex-1 px-4 py-8">{children}</main>
    </div>
  );
}
