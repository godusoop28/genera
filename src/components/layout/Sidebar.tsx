"use client";

import { BrandLogo } from "@/components/brand/BrandLogo";
import { BrandFooter } from "@/components/brand/BrandFooter";
import { useDemoApp } from "@/context/DemoAppProvider";
import { cn } from "@/lib/utils";
import { FilePlus2, Files, LogOut, Users } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";

// Menú final del Módulo 1 (prompt maestro §10/§86): solo estas cuatro entradas.
// No agregar Dashboard, Leads, Propiedades, Campañas, CRM, etc.
const navItems = [
  { href: "/expedientes", label: "Expedientes", icon: Files },
  { href: "/expedientes/nuevo", label: "Nuevo expediente", icon: FilePlus2 },
  { href: "/usuarios", label: "Usuarios y permisos", icon: Users },
];

export function Sidebar({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  const router = useRouter();
  const { logout } = useDemoApp();

  const handleLogout = () => {
    logout();
    onNavigate?.();
    router.push("/login");
  };

  return (
    <div className="flex h-full flex-col bg-obsessed">
      <div className="border-b border-white/10 px-5 py-5">
        <BrandLogo tone="dark" size="md" />
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
                isActive ? "bg-gold/15 text-gold" : "text-white/70 hover:bg-white/5 hover:text-white",
              )}
            >
              <Icon className="h-[18px] w-[18px]" aria-hidden />
              {item.label}
            </Link>
          );
        })}
      </nav>
      <div className="space-y-3 border-t border-white/10 px-3 py-4">
        <button
          type="button"
          onClick={handleLogout}
          className="flex w-full items-center gap-3 rounded-xl px-3.5 py-2.5 text-sm font-medium text-white/70 transition-colors hover:bg-white/5 hover:text-white"
        >
          <LogOut className="h-[18px] w-[18px]" aria-hidden />
          Cerrar sesión
        </button>
        <BrandFooter className="px-3.5 text-white/40" />
      </div>
    </div>
  );
}
