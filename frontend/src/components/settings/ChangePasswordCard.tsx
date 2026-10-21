"use client";

import { PasswordFields, passwordProblem } from "@/components/auth/PasswordFields";
import { Button } from "@/components/ui/Button";
import { Card, CardHeader } from "@/components/ui/Card";
import { Input } from "@/components/ui/Input";
import { useToast } from "@/components/ui/Toast";
import { useAuth } from "@/context/AuthProvider";
import { changeOwnPassword } from "@/lib/api/auth";
import { storeTokens } from "@/lib/api/client";
import { errorText } from "@/lib/errors";
import { useState } from "react";

/**
 * Control de Cambios #25: cada usuario cambia su contraseña escribiendo la actual. Se cierran sus
 * demás sesiones (celular, otros navegadores) y esta continúa con una sesión nueva.
 */
export function ChangePasswordCard() {
  const { user } = useAuth();
  const { showToast } = useToast();
  const [current, setCurrent] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [submitted, setSubmitted] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const problem = !current ? "Escribe tu contraseña actual." : passwordProblem(password, confirm, user ?? undefined);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    if (problem) return;
    setSaving(true);
    setError(null);
    try {
      const auth = await changeOwnPassword(current, password);
      storeTokens(auth.accessToken, auth.refreshToken);
      setCurrent("");
      setPassword("");
      setConfirm("");
      setSubmitted(false);
      showToast("Contraseña cambiada. Se cerraron tus sesiones en otros dispositivos.");
    } catch (err) {
      setError(errorText(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card>
      <CardHeader title="Cambiar contraseña" description="Al cambiarla se cierran tus sesiones abiertas en otros dispositivos." />
      <form onSubmit={submit} className="flex flex-col gap-4">
        <input type="email" value={user?.email ?? ""} autoComplete="username" readOnly hidden />
        <Input
          type="password"
          label="Contraseña actual"
          autoComplete="current-password"
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
          required
        />
        <PasswordFields password={password} confirm={confirm} onPassword={setPassword} onConfirm={setConfirm} problem={submitted ? problem : null} />
        {error ? (
          <p role="alert" className="text-sm text-danger-text">
            {error}
          </p>
        ) : null}
        <p className="text-xs text-muted">¿No recuerdas la actual? Pide al administrador un enlace para restablecerla.</p>
        <div className="flex justify-end">
          <Button type="submit" disabled={saving}>
            {saving ? "Guardando…" : "Cambiar contraseña"}
          </Button>
        </div>
      </form>
    </Card>
  );
}
