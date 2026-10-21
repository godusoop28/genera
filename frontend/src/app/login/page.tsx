"use client";

import { BrandLogo } from "@/components/brand/BrandLogo";
import { BrandFooter } from "@/components/brand/BrandFooter";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { useAuth } from "@/context/AuthProvider";
import { ApiError } from "@/lib/api/client";
import { FileLock2 } from "lucide-react";
import { useRouter } from "next/navigation";
import { useState } from "react";

export default function LoginPage() {
  const router = useRouter();
  const { login } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [showForgot, setShowForgot] = useState(false);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      router.push("/expedientes");
    } catch (err) {
      if (err instanceof ApiError && err.status === 423) {
        // Control de Cambios #25: bloqueo temporal tras varios intentos fallidos.
        setError(err.message);
      } else if (err instanceof ApiError && (err.status === 401 || err.status === 400)) {
        setError("Correo o contraseña incorrectos.");
      } else if (err instanceof TypeError) {
        setError("No se pudo conectar con el servidor. Intenta de nuevo en unos segundos.");
      } else {
        setError("Ocurrió un error inesperado al iniciar sesión.");
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="flex min-h-screen flex-1 items-center justify-center bg-app-bg px-4 py-10">
      <div className="w-full max-w-md">
        <div className="mb-8 flex flex-col items-center gap-3">
          <BrandLogo tone="light" size="lg" />
        </div>

        <div className="rounded-2xl border border-border bg-card p-8 shadow-sm">
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
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
            />
            <Input
              type="password"
              label="Contraseña"
              placeholder="••••••••"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />

            {error ? (
              <p role="alert" className="text-sm text-danger-text">
                {error}
              </p>
            ) : null}

            <Button type="submit" size="lg" className="mt-2 w-full" disabled={submitting}>
              {submitting ? "Entrando..." : "Entrar"}
            </Button>
          </form>

          <div className="mt-5 border-t border-border pt-4 text-center">
            <button
              type="button"
              onClick={() => setShowForgot((v) => !v)}
              aria-expanded={showForgot}
              className="text-sm font-medium text-dark-gold underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30"
            >
              ¿Olvidaste tu contraseña?
            </button>
            {showForgot ? (
              <p className="mt-3 rounded-lg bg-info-bg px-3 py-2 text-left text-sm text-info-text">
                Pide al administrador de la oficina que te envíe un enlace para restablecerla. Con ese enlace defines tu nueva contraseña; nadie más la conoce.
              </p>
            ) : null}
          </div>
        </div>

        <div className="mt-6 text-center">
          <BrandFooter />
        </div>
      </div>
    </div>
  );
}
