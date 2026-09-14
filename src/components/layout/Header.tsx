"use client";

import { initials } from "@/lib/utils";
import { Menu } from "lucide-react";

interface HeaderProps {
  onMenuClick: () => void;
}

const userName = "Ana Rodríguez";

export function Header({ onMenuClick }: HeaderProps) {
  return (
    <header className="sticky top-0 z-30 flex items-center justify-between border-b border-border bg-white/90 px-4 py-3.5 backdrop-blur-sm lg:px-8">
      <button
        type="button"
        onClick={onMenuClick}
        aria-label="Abrir menú"
        className="rounded-lg p-2 text-navy hover:bg-app-bg lg:hidden"
      >
        <Menu className="h-5 w-5" />
      </button>
      <div className="hidden lg:block" />
      <div className="flex items-center gap-3">
        <div className="text-right leading-tight">
          <p className="text-sm font-medium text-navy">{userName}</p>
          <p className="text-xs text-muted">Century 21</p>
        </div>
        <div className="flex h-9 w-9 items-center justify-center rounded-full bg-navy text-xs font-semibold text-white">
          {initials(userName)}
        </div>
      </div>
    </header>
  );
}
