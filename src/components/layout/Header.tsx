"use client";

import { useDemoApp } from "@/context/DemoAppProvider";
import { initials } from "@/lib/utils";
import { ROLES } from "@/data/permissions";
import { Menu } from "lucide-react";

interface HeaderProps {
  onMenuClick: () => void;
}

export function Header({ onMenuClick }: HeaderProps) {
  const { currentUser } = useDemoApp();
  const role = ROLES.find((r) => r.id === currentUser.roleId);

  return (
    <header className="sticky top-0 z-30 flex items-center justify-between border-b border-border bg-white/90 px-4 py-3.5 backdrop-blur-sm lg:px-8">
      <button
        type="button"
        onClick={onMenuClick}
        aria-label="Abrir menú"
        className="rounded-lg p-2 text-obsessed hover:bg-app-bg lg:hidden"
      >
        <Menu className="h-5 w-5" />
      </button>
      <div className="hidden lg:block" />
      <div className="flex items-center gap-3">
        <div className="text-right leading-tight">
          <p className="text-sm font-medium text-obsessed">{currentUser.name}</p>
          <p className="text-xs text-muted">{role?.name ?? "Personal interno"}</p>
        </div>
        <div className="flex h-9 w-9 items-center justify-center rounded-full bg-obsessed text-xs font-semibold text-gold">
          {initials(currentUser.name)}
        </div>
      </div>
    </header>
  );
}
