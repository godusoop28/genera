"use client";

import { useAuth } from "@/context/AuthProvider";
import { roleLabels } from "@/lib/labels";
import { initials } from "@/lib/utils";
import { ChevronRight, Menu, Settings } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";

interface HeaderProps {
  onMenuClick: () => void;
}

interface Crumb {
  label: string;
  href?: string;
}

function breadcrumbFor(pathname: string): Crumb[] {
  if (pathname === "/expedientes/nuevo") return [{ label: "Expedientes", href: "/expedientes" }, { label: "Nuevo expediente" }];
  if (pathname.startsWith("/expedientes/")) return [{ label: "Expedientes", href: "/expedientes" }, { label: "Detalle del expediente" }];
  if (pathname.startsWith("/expedientes")) return [{ label: "Expedientes" }];
  if (pathname.startsWith("/usuarios")) return [{ label: "Usuarios y permisos" }];
  if (pathname.startsWith("/configuracion")) return [{ label: "Configuración" }];
  return [];
}

export function Header({ onMenuClick }: HeaderProps) {
  const { user } = useAuth();
  const pathname = usePathname();
  const displayName = user?.name ?? "Personal interno";
  const roleName = user ? (roleLabels[user.role] ?? user.role) : "";
  const crumbs: Crumb[] = [{ label: "Genera", href: "/expedientes" }, ...breadcrumbFor(pathname)];

  return (
    <header className="sticky top-0 z-20 flex h-16 items-center gap-3 border-b border-border bg-card/95 px-4 backdrop-blur-sm sm:px-6 lg:px-8">
      <button
        type="button"
        onClick={onMenuClick}
        aria-label="Abrir menú"
        className="-ml-1 rounded-lg p-2 text-obsessed hover:bg-app-bg focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30 lg:hidden"
      >
        <Menu className="h-5 w-5" aria-hidden />
      </button>

      <nav aria-label="Ruta de navegación" className="min-w-0 flex-1">
        <ol className="flex min-w-0 items-center gap-1.5 text-sm">
          {crumbs.map((crumb, i) => {
            const last = i === crumbs.length - 1;
            return (
              <li
                key={`${crumb.label}-${i}`}
                className={i === 0 && crumbs.length > 1 ? "hidden shrink-0 items-center gap-1.5 sm:flex" : "flex min-w-0 items-center gap-1.5"}
              >
                {crumb.href && !last ? (
                  <Link
                    href={crumb.href}
                    className="truncate rounded text-muted transition-colors duration-150 hover:text-obsessed focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gold/40"
                  >
                    {crumb.label}
                  </Link>
                ) : (
                  <span className="truncate font-medium text-obsessed" aria-current={last ? "page" : undefined}>
                    {crumb.label}
                  </span>
                )}
                {!last ? <ChevronRight className="h-3.5 w-3.5 shrink-0 text-muted" aria-hidden /> : null}
              </li>
            );
          })}
        </ol>
      </nav>

      <Link
        href="/configuracion"
        title="Configuración de la cuenta"
        aria-label={`${displayName}${roleName ? `, ${roleName}` : ""}. Configuración de la cuenta`}
        className="group flex shrink-0 items-center gap-3 rounded-xl py-1.5 pl-1.5 pr-2 transition-colors duration-150 hover:bg-app-bg focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30"
      >
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gold/25 text-xs font-semibold text-dark-gold">
          {initials(displayName)}
        </span>
        <span className="hidden text-left leading-tight sm:block">
          <span className="block max-w-[12rem] truncate text-sm font-medium text-obsessed">{displayName}</span>
          <span className="block text-xs text-muted">{roleName}</span>
        </span>
        <Settings className="hidden h-4 w-4 text-muted transition-colors duration-150 group-hover:text-obsessed sm:block" aria-hidden />
      </Link>
    </header>
  );
}
