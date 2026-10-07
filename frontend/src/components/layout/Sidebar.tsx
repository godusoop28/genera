"use client";

import { BrandFooter } from "@/components/brand/BrandFooter";
import { BrandLogo } from "@/components/brand/BrandLogo";
import { useAuth } from "@/context/AuthProvider";
import { useCan } from "@/lib/permissions";
import { cn } from "@/lib/utils";
import { FilePlus2, Files, LogOut, Users } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";

// Menú final del Módulo 1 (prompt maestro §10/§86): solo estas entradas.
// No agregar Dashboard, Leads, Propiedades, Campañas, CRM, etc. Cada entrada
// se muestra solo a quien tiene el permiso (el backend vuelve a validarlo).
const navItems = [
  { href: "/expedientes", label: "Expedientes", icon: Files },
  { href: "/expedientes/nuevo", label: "Nuevo expediente", icon: FilePlus2, permission: "EXPEDIENT_CREATE" },
  { href: "/usuarios", label: "Usuarios y permisos", icon: Users, permission: "USER_MANAGE" },
];

function isActive(href: string, pathname: string): boolean {
  if (href === "/expedientes") return pathname === "/expedientes" || (pathname.startsWith("/expedientes/") && pathname !== "/expedientes/nuevo");
  return pathname.startsWith(href);
}

export function Sidebar({ onNavigate }: { onNavigate?: () => void }) {
  const pathname = usePathname();
  const router = useRouter();
  const { logout } = useAuth();
  const can = useCan();

  const handleLogout = () => {
    void logout();
    onNavigate?.();
    router.push("/login");
  };

  return (
    <div className="flex h-full flex-col bg-brand-ink">
      <div className="px-5 pb-7 pt-6">
        <Link
          href="/expedientes"
          onClick={onNavigate}
          aria-label="CENTURY 21 Genera, ir a expedientes"
          className="inline-block rounded-lg focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/40"
        >
          <BrandLogo tone="nav" className="h-9" />
        </Link>
      </div>
      <nav aria-label="Principal" className="flex-1 space-y-1 overflow-y-auto px-3">
        {navItems
          .filter((item) => !item.permission || can(item.permission))
          .map((item) => {
            const active = isActive(item.href, pathname);
            const Icon = item.icon;
            return (
              <Link
                key={item.href}
                href={item.href}
                onClick={onNavigate}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "relative flex items-center gap-3 rounded-xl px-3.5 py-2.5 text-sm font-medium transition-colors duration-150 focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/40",
                  active ? "bg-gold/15 text-gold" : "text-white/75 hover:bg-white/5 hover:text-white",
                )}
              >
                {active ? <span className="absolute inset-y-2 left-0 w-[3px] rounded-full bg-gold" aria-hidden /> : null}
                <Icon className="h-[18px] w-[18px] shrink-0" aria-hidden />
                {item.label}
              </Link>
            );
          })}
      </nav>
      <div className="space-y-3 border-t border-white/10 px-3 py-4">
        <button
          type="button"
          onClick={handleLogout}
          className="flex w-full items-center gap-3 rounded-xl px-3.5 py-2.5 text-sm font-medium text-white/75 transition-colors duration-150 hover:bg-white/5 hover:text-white focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/40"
        >
          <LogOut className="h-[18px] w-[18px]" aria-hidden />
          Cerrar sesión
        </button>
        <BrandFooter className="px-3.5 text-white/50" />
      </div>
    </div>
  );
}
