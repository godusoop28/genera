"use client";

import { Input } from "@/components/ui/Input";
import { PASSWORD_MIN_LENGTH } from "@/lib/api/auth";

/**
 * Control de Cambios #25: misma política que el backend (PasswordPolicy): largo mínimo, sin
 * contraseñas obvias. El servidor revisa además la lista de contraseñas comunes.
 */
export function passwordProblem(password: string, confirm: string, owner?: { email: string; name: string }): string | null {
  if (password.length < PASSWORD_MIN_LENGTH) return `Usa al menos ${PASSWORD_MIN_LENGTH} caracteres. Una frase corta es fácil de recordar y difícil de adivinar.`;
  if (!password.trim()) return "La contraseña no puede ser solo espacios.";
  if (new Set(password.toLowerCase()).size <= 2) return "Esa contraseña es demasiado fácil de adivinar.";
  if (owner) {
    const local = owner.email.split("@")[0].toLowerCase();
    if (local.length >= 4 && password.toLowerCase().includes(local)) return "La contraseña no debe contener tu correo.";
  }
  if (password !== confirm) return "Las dos contraseñas no coinciden.";
  return null;
}

export function PasswordFields({
  password,
  confirm,
  onPassword,
  onConfirm,
  problem,
  label = "Nueva contraseña",
}: {
  password: string;
  confirm: string;
  onPassword: (v: string) => void;
  onConfirm: (v: string) => void;
  problem: string | null;
  label?: string;
}) {
  return (
    <>
      <Input
        type="password"
        label={label}
        autoComplete="new-password"
        value={password}
        onChange={(e) => onPassword(e.target.value)}
        maxLength={128}
        hint={`Mínimo ${PASSWORD_MIN_LENGTH} caracteres. No necesita símbolos; evita tu nombre o datos obvios.`}
        required
      />
      <Input
        type="password"
        label="Confirma la contraseña"
        autoComplete="new-password"
        value={confirm}
        onChange={(e) => onConfirm(e.target.value)}
        maxLength={128}
        error={problem ?? undefined}
        required
      />
    </>
  );
}
