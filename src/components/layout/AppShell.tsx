"use client";

import { Header } from "@/components/layout/Header";
import { Sidebar } from "@/components/layout/Sidebar";
import { X } from "lucide-react";
import { useState, type ReactNode } from "react";

export function AppShell({ children }: { children: ReactNode }) {
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <div className="flex min-h-screen bg-app-bg">
      <aside className="hidden w-64 shrink-0 border-r border-border lg:block">
        <div className="fixed h-screen w-64">
          <Sidebar />
        </div>
      </aside>

      {mobileOpen ? (
        <div className="fixed inset-0 z-40 lg:hidden">
          <button
            aria-label="Cerrar menú"
            className="absolute inset-0 bg-navy/40"
            onClick={() => setMobileOpen(false)}
          />
          <div className="absolute inset-y-0 left-0 w-72 max-w-[80vw] shadow-xl">
            <div className="flex justify-end bg-white px-3 pt-3">
              <button
                aria-label="Cerrar menú"
                onClick={() => setMobileOpen(false)}
                className="rounded-lg p-2 text-navy hover:bg-app-bg"
              >
                <X className="h-5 w-5" />
              </button>
            </div>
            <Sidebar onNavigate={() => setMobileOpen(false)} />
          </div>
        </div>
      ) : null}

      <div className="flex min-h-screen flex-1 flex-col lg:pl-64">
        <Header onMenuClick={() => setMobileOpen(true)} />
        <main className="flex-1">{children}</main>
      </div>
    </div>
  );
}
