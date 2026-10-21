"use client";

import { BrandFooter } from "@/components/brand/BrandFooter";
import { BrandLogo } from "@/components/brand/BrandLogo";
import { PasswordFields, passwordProblem } from "@/components/auth/PasswordFields";
import { Button } from "@/components/ui/Button";
import { StateMessage } from "@/components/ui/StateMessage";
import { completeAccountLink, getAccountLink, type AccountLinkInfo } from "@/lib/api/auth";
import { ApiError } from "@/lib/api/client";
import { errorText } from "@/lib/errors";
import { CheckCircle2, KeyRound } from "lucide-react";
import Link from "next/link";
import { useEffect, useState } from "react";

const dateTime = new Intl.DateTimeFormat("es-MX", { day: "numeric", month: "long", hour: "2-digit", minute: "2-digit" });

/**
 * Control de Cambios #25: la persona define su contraseña con el enlace de un solo uso que le
 * envió el administrador (alta o restablecimiento). Nadie más ve la contraseña.
 */
export function AccountLinkPage({ token }: { token: string }) {
  const [info, setInfo] = useState<AccountLinkInfo | null>(null);
  const [invalid, setInvalid] = useState<string | null>(null);
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [done, setDone] = useState(false);

  useEffect(() => {
    getAccountLink(token)
      .then(setInfo)
      .catch((err) =>
        setInvalid(
          err instanceof ApiError && (err.status === 410 || err.status === 404)
            ? err.message
            : "No se pudo verificar el enlace. Revisa tu conexión e intenta de nuevo.",
        ),
      );
  }, [token]);

  const problem = info ? passwordProblem(password, confirm, info) : null;

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    if (problem) return;
    setSaving(true);
    setError(null);
    try {
      await completeAccountLink(token, password);
      setDone(true);
    } catch (err) {
      setError(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="flex min-h-screen flex-1 items-center justify-center bg-app-bg px-4 py-10">
      <div className="w-full max-w-md">
        <div className="mb-8 flex flex-col items-center gap-3">
          <BrandLogo tone="light" size="lg" />
        </div>

        <div className="rounded-2xl border border-border bg-card p-6 shadow-sm sm:p-8">
          {invalid ? (
            <StateMessage kind="error" title="Enlace no válido" description={invalid} className="border-0 p-0" />
          ) : !info ? (
            <StateMessage kind="loading" title="Verificando enlace…" className="border-0 p-0" />
          ) : done ? (
            <div className="flex flex-col items-center text-center">
              <CheckCircle2 className="mb-3 h-12 w-12 text-success-text" aria-hidden />
              <h1 className="text-xl font-semibold text-obsessed">Contraseña guardada</h1>
              <p className="mt-1.5 text-sm text-muted">Ya puedes entrar con tu correo {info.email} y tu nueva contraseña.</p>
              <Link
                href="/login"
                className="mt-6 inline-flex min-h-11 w-full items-center justify-center rounded-xl bg-gold px-4 py-2.5 text-sm font-medium text-brand-ink hover:bg-gold-hover focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/40"
              >
                Ir a iniciar sesión
              </Link>
            </div>
          ) : (
            <>
              <div className="mb-6 flex flex-col items-center text-center">
                <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-gold/15">
                  <KeyRound className="h-7 w-7 text-dark-gold" aria-hidden />
                </div>
                <h1 className="text-xl font-semibold text-obsessed">
                  {info.purpose === "INVITE" ? `Bienvenido, ${info.name.split(" ")[0]}` : "Define tu nueva contraseña"}
                </h1>
                <p className="mt-1.5 text-sm text-muted">
                  {info.purpose === "INVITE" ? "Crea tu contraseña para entrar al sistema" : "Elige una contraseña nueva"} con tu correo <strong>{info.email}</strong>. El
                  enlace vence el {dateTime.format(new Date(info.expiresAt))} y solo funciona una vez.
                </p>
              </div>
              <form onSubmit={submit} className="flex flex-col gap-4">
                {/* Ayuda a los gestores de contraseñas a guardarla con el correo correcto. */}
                <input type="email" value={info.email} autoComplete="username" readOnly hidden />
                <PasswordFields password={password} confirm={confirm} onPassword={setPassword} onConfirm={setConfirm} problem={submitted ? problem : null} />
                {error ? (
                  <p role="alert" className="text-sm text-danger-text">
                    {error}
                  </p>
                ) : null}
                <Button type="submit" size="lg" className="mt-2 w-full" disabled={saving}>
                  {saving ? "Guardando…" : "Guardar contraseña"}
                </Button>
              </form>
            </>
          )}
        </div>

        <div className="mt-6 text-center">
          <BrandFooter />
        </div>
      </div>
    </div>
  );
}
