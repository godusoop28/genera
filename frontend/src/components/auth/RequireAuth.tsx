"use client";

import { useAuth } from "@/context/AuthProvider";
import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";

/** Redirige a /login si no hay una sesión real (JWT) vigente. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.replace("/login");
    }
  }, [isLoading, isAuthenticated, router]);

  if (isLoading) {
    return <div className="flex min-h-screen items-center justify-center text-sm text-muted">Cargando…</div>;
  }

  if (!isAuthenticated) {
    return null;
  }

  return <>{children}</>;
}
