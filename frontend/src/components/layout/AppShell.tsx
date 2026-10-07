"use client";

import { Header } from "@/components/layout/Header";
import { Sidebar } from "@/components/layout/Sidebar";
import { X } from "lucide-react";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState, type ReactNode } from "react";

export function AppShell({ children }: { children: ReactNode }) {
  const [openedAt, setOpenedAt] = useState<string | null>(null);
  const pathname = usePathname();
  const closeRef = useRef<HTMLButtonElement>(null);
  // El menú móvil se cierra solo al navegar: solo cuenta como abierto en la ruta donde se abrió.
  const mobileOpen = openedAt === pathname;
  const close = () => setOpenedAt(null);

  useEffect(() => {
    if (!mobileOpen) return;
    closeRef.current?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpenedAt(null);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [mobileOpen]);

  return (
    <div className="min-h-screen bg-app-bg">
      {/* Escritorio: el sidebar es fijo y el contenido se desplaza su ancho una sola vez (lg:pl-60). */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-60 lg:block">
        <Sidebar />
      </aside>

      {mobileOpen ? (
        <div className="fixed inset-0 z-50 lg:hidden" role="dialog" aria-modal="true" aria-label="Menú">
          <button
            type="button"
            aria-label="Cerrar menú"
            tabIndex={-1}
            className="animate-fade-in absolute inset-0 bg-brand-ink/50"
            onClick={close}
          />
          <div className="animate-fade-in absolute inset-y-0 left-0 flex w-72 max-w-[85vw] flex-col shadow-xl">
            <button
              ref={closeRef}
              type="button"
              aria-label="Cerrar menú"
              onClick={close}
              className="absolute right-3 top-5 z-10 rounded-lg p-2 text-white/70 hover:bg-white/10 hover:text-white focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/40"
            >
              <X className="h-5 w-5" aria-hidden />
            </button>
            <Sidebar onNavigate={close} />
          </div>
        </div>
      ) : null}

      <div className="flex min-h-screen min-w-0 flex-col lg:pl-60">
        <Header onMenuClick={() => setOpenedAt(pathname)} />
        <main className="min-w-0 flex-1">{children}</main>
      </div>
    </div>
  );
}
