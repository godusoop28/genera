import { Card, CardHeader } from "@/components/ui/Card";

/**
 * Control de Cambios #25: esquema aprobado de administración de cuentas (quién hace qué y desde
 * dónde). El servidor aplica estas mismas reglas aunque alguien intente entrar directo a la acción.
 */
const rows: { action: string; who: string; where: string }[] = [
  { action: "Dar de alta una cuenta y asignarle su rol", who: "Administrador", where: "Usuarios → Crear usuario. La persona define su contraseña con su enlace." },
  { action: "Editar nombre y correo", who: "Administrador", where: "Usuarios → Editar" },
  { action: "Cambiar el rol de una cuenta", who: "Administrador (nunca el suyo propio)", where: "Usuarios → Editar. Aplica de inmediato." },
  { action: "Cambiar mi contraseña", who: "Cada usuario", where: "Mi perfil → Cambiar contraseña (pide la actual)" },
  {
    action: "Olvidé mi contraseña / no puedo entrar",
    who: "Administrador, a petición del usuario",
    where: "Usuarios → Restablecer contraseña: genera un enlace de un solo uso (24 h) que se le envía por WhatsApp",
  },
  { action: "Desbloquear tras intentos fallidos", who: "Administrador (o esperar 15 minutos)", where: "Usuarios → Desbloquear" },
  { action: "Desactivar o reactivar (temporal)", who: "Administrador (nunca a sí mismo)", where: "Usuarios → Desactivar / Reactivar" },
  { action: "Dar de baja (definitivo)", who: "Administrador, con motivo", where: "Usuarios → Dar de baja. Antes hay que reasignar expedientes y leads." },
  { action: "Consultar la bitácora de cuentas", who: "Administrador", where: "Usuarios → Bitácora" },
];

const rules = [
  "Nadie conoce la contraseña de otra persona, ni siquiera el administrador; nunca se muestra ni se envía en texto.",
  "Contraseñas de al menos 10 caracteres; se rechazan las demasiado comunes. No se exigen símbolos ni cambios periódicos.",
  "Tras 5 intentos fallidos la cuenta se bloquea 15 minutos.",
  "Cambiar o restablecer la contraseña cierra las demás sesiones; desactivar o dar de baja corta el acceso de inmediato.",
  "Siempre debe quedar al menos un administrador activo.",
  "Una baja no borra nada: el nombre de la persona se conserva en el historial y en los expedientes.",
];

export function AccountScheme() {
  return (
    <Card>
      <CardHeader title="Administración de cuentas" description="Quién puede hacer cada acción sobre las cuentas y desde qué pantalla." />
      <div className="-mx-1 overflow-x-auto px-1">
        <table className="w-full min-w-[560px] text-left text-sm">
          <thead className="border-b border-border text-xs uppercase tracking-wide text-muted">
            <tr>
              <th scope="col" className="py-2 pr-4 font-medium">Acción</th>
              <th scope="col" className="py-2 pr-4 font-medium">Quién</th>
              <th scope="col" className="py-2 font-medium">Dónde / cómo</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {rows.map((r) => (
              <tr key={r.action} className="align-top">
                <th scope="row" className="py-2.5 pr-4 font-medium text-obsessed">{r.action}</th>
                <td className="py-2.5 pr-4 text-obsessed">{r.who}</td>
                <td className="py-2.5 text-muted">{r.where}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <ul className="mt-4 list-disc space-y-1 pl-5 text-sm text-muted">
        {rules.map((r) => (
          <li key={r}>{r}</li>
        ))}
      </ul>
    </Card>
  );
}
