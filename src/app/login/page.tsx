"use client";

import { BrandLogo } from "@/components/brand/BrandLogo";
import { BrandFooter } from "@/components/brand/BrandFooter";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { useDemoApp } from "@/context/DemoAppProvider";
import { PROTOTYPE_BADGE_TEXT } from "@/data/organization";
import { FileLock2 } from "lucide-react";
import { useRouter } from "next/navigation";

export default function LoginPage() {
  const router = useRouter();
  const { login } = useDemoApp();

  const handleSubmit = (event: React.FormEvent) => {
    event.preventDefault();
    login();
    router.push("/expedientes");
  };

  return (
    <div className="flex min-h-screen flex-1 items-center justify-center bg-app-bg px-4 py-10">
      <div className="w-full max-w-md">
        <div className="mb-8 flex flex-col items-center gap-3">
          <BrandLogo tone="light" size="lg" />
          <Badge tone="neutral">{PROTOTYPE_BADGE_TEXT}</Badge>
        </div>

        <div className="rounded-2xl border border-border bg-white p-8 shadow-sm">
          <div className="mb-6 flex flex-col items-center text-center">
            <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gold/15">
              <FileLock2 className="h-7 w-7 text-dark-gold" aria-hidden />
            </div>
            <h1 className="text-xl font-semibold text-obsessed">Acceso de personal interno</h1>
            <p className="mt-1.5 text-sm text-muted">
              Este acceso es exclusivo para personal autorizado de CENTURY 21 Genera. Los
              propietarios no requieren cuenta: reciben una liga directa a su expediente.
            </p>
          </div>

          <form onSubmit={handleSubmit} className="flex flex-col gap-4">
            <Input
              type="email"
              label="Correo electrónico"
              placeholder="admin@demo.local"
              autoComplete="email"
            />
            <Input
              type="password"
              label="Contraseña"
              placeholder="••••••••"
              autoComplete="current-password"
            />

            <Button type="submit" size="lg" className="mt-2 w-full">
              Entrar
            </Button>
          </form>
        </div>

        <div className="mt-6 text-center">
          <BrandFooter />
        </div>
      </div>
    </div>
  );
}
