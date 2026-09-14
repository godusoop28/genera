"use client";

import { BrandMark } from "@/components/layout/BrandMark";
import { cn } from "@/lib/utils";
import { FilePlus2, Files, LogOut } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";

const navItems = [
  { href: "/expedientes", label: "Expedientes", icon: Files },
  { href: "/expedientes/nuevo", label: "Nuevo expediente", icon: FilePlus2 },
];

export function Sidebar({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();

  return (
    <div className="flex h-full flex-col bg-white">
      <div className="border-b border-border px-5 py-5">
        <BrandMark />
      </div>
      <nav className="flex-1 space-y-1 px-3 py-5">
        {navItems.map((item) => {
          const isActive =
            item.href === "/expedientes"
              ? pathname === "/expedientes"
              : pathname.startsWith(item.href);
          const Icon = item.icon;
          return (
            <Link
              key={item.href}
              href={item.href}
              onClick={onNavigate}
              className={cn(
                "flex items-center gap-3 rounded-xl px-3.5 py-2.5 text-sm font-medium transition-colors",
                isActive
                  ? "bg-teal-light text-teal-dark"
                  : "text-navy/70 hover:bg-app-bg hover:text-navy",
              )}
            >
              <Icon className="h-[18px] w-[18px]" aria-hidden />
              {item.label}
            </Link>
          );
        })}
      </nav>
      <div className="border-t border-border px-3 py-4">
        <Link
          href="/login"
          onClick={onNavigate}
          className="flex items-center gap-3 rounded-xl px-3.5 py-2.5 text-sm font-medium text-navy/70 transition-colors hover:bg-app-bg hover:text-navy"
        >
          <LogOut className="h-[18px] w-[18px]" aria-hidden />
          Cerrar sesión
        </Link>
      </div>
    </div>
  );
}
