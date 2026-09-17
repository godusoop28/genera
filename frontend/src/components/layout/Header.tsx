"use client";

import { useAuth } from "@/context/AuthProvider";
import { initials } from "@/lib/utils";
import { Menu, Settings } from "lucide-react";
import Link from "next/link";

interface HeaderProps {
  onMenuClick: () => void;
}

export function Header({ onMenuClick }: HeaderProps) {
  const { user } = useAuth();
  const displayName = user?.name ?? "Personal interno";
  const roleName = user?.role ?? "";

  return (
    <header className="sticky top-0 z-30 flex items-center justify-between border-b border-border bg-card/90 px-4 py-3.5 backdrop-blur-sm lg:px-8">
      <button
        type="button"
        onClick={onMenuClick}
        aria-label="Abrir menú"
        className="rounded-lg p-2 text-obsessed hover:bg-app-bg lg:hidden"
      >
        <Menu className="h-5 w-5" />
      </button>
      <div className="hidden lg:block" />
      <div className="flex items-center gap-2 sm:gap-3">
        <Link
          href="/configuracion"
          aria-label="Configuración"
          className="rounded-lg p-2 text-muted transition-colors hover:bg-app-bg hover:text-obsessed"
        >
          <Settings className="h-5 w-5" />
        </Link>
        <div className="hidden text-right leading-tight sm:block">
          <p className="text-sm font-medium text-obsessed">{displayName}</p>
          <p className="text-xs text-muted">{roleName}</p>
        </div>
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-ink text-xs font-semibold text-gold">
          {initials(displayName)}
        </div>
      </div>
    </header>
  );
}
