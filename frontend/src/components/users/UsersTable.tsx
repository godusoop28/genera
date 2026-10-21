"use client";

import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { Modal } from "@/components/ui/Modal";
import { SearchInput } from "@/components/ui/SearchInput";
import { Select } from "@/components/ui/Select";
import { StateMessage } from "@/components/ui/StateMessage";
import { useToast } from "@/components/ui/Toast";
import { useAuth } from "@/context/AuthProvider";
import { ApiError } from "@/lib/api/client";
import { listRoles } from "@/lib/api/roles";
import {
  activateUser,
  createUser,
  deactivateUser,
  getUserWorkload,
  issuePasswordLink,
  listUsers,
  offboardUser,
  unlockUser,
  updateUser,
} from "@/lib/api/users";
import type { BackendRoleCode, BackendUserStatus, RoleResponse, SetupLinkResponse, UserResponse, WorkloadItem } from "@/lib/api/types";
import { errorText } from "@/lib/errors";
import { cn, initials } from "@/lib/utils";
import {
  ArrowRight,
  CircleCheck,
  CircleSlash,
  Clock,
  Copy,
  KeyRound,
  Lock,
  MessageCircle,
  Pencil,
  RefreshCw,
  ShieldCheck,
  UserMinus,
  Users,
  UserX,
} from "lucide-react";
import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";

// La API devuelve como máximo 100 por página; el personal interno de una oficina cabe de sobra.
const PAGE_SIZE = 100;

type LoadError = { kind: "forbidden" | "error"; message: string };

const timeFormat = new Intl.DateTimeFormat("es-MX", { hour: "2-digit", minute: "2-digit" });
const dateFormat = new Intl.DateTimeFormat("es-MX", { day: "numeric", month: "short", year: "numeric" });
const dateTimeFormat = new Intl.DateTimeFormat("es-MX", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });

function lastActivityLabel(iso: string | null): string {
  if (!iso) return "Sin actividad";
  const date = new Date(iso);
  const today = new Date();
  const yesterday = new Date(today);
  yesterday.setDate(today.getDate() - 1);
  if (date.toDateString() === today.toDateString()) return `Hoy, ${timeFormat.format(date)}`;
  if (date.toDateString() === yesterday.toDateString()) return `Ayer, ${timeFormat.format(date)}`;
  return dateFormat.format(date);
}

const statusFilters: { value: string; label: string }[] = [
  { value: "", label: "Cuentas vigentes" },
  { value: "ACTIVE", label: "Activas" },
  { value: "PENDING", label: "Pendientes de contraseña" },
  { value: "INACTIVE", label: "Desactivadas" },
  { value: "OFFBOARDED", label: "Dadas de baja" },
];

/** Acción pendiente de confirmar en un modal. */
type Pending =
  | { kind: "link"; user: UserResponse }
  | { kind: "deactivate"; user: UserResponse }
  | { kind: "offboard"; user: UserResponse };

interface UsersTableProps {
  createOpen: boolean;
  onCreateOpenChange: (open: boolean) => void;
  onShowRoles: () => void;
}

/**
 * Control de Cambios #25: administración de cuentas. El administrador da de alta (la persona
 * define su contraseña con un enlace), edita, genera enlaces de restablecimiento, desbloquea,
 * desactiva o da de baja. Nunca ve ni escribe la contraseña de nadie.
 */
export function UsersTable({ createOpen, onCreateOpenChange, onShowRoles }: UsersTableProps) {
  const { showToast } = useToast();
  const { user: me } = useAuth();
  const [users, setUsers] = useState<UserResponse[] | null>(null);
  const [totalUsers, setTotalUsers] = useState(0);
  const [loadError, setLoadError] = useState<LoadError | null>(null);
  const [loading, setLoading] = useState(false);
  const [roles, setRoles] = useState<RoleResponse[]>([]);
  const [query, setQuery] = useState("");
  const [roleFilter, setRoleFilter] = useState<string>("");
  const [statusFilter, setStatusFilter] = useState<string>("");
  const [editUser, setEditUser] = useState<UserResponse | null>(null);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [roleCode, setRoleCode] = useState<BackendRoleCode>("ADVISOR");
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [busyUserId, setBusyUserId] = useState<string | null>(null);
  const [pending, setPending] = useState<Pending | null>(null);
  const [shownLink, setShownLink] = useState<{ user: Pick<UserResponse, "name" | "email">; link: SetupLinkResponse } | null>(null);

  const loadUsers = useCallback(() => {
    listUsers(0, PAGE_SIZE)
      .then((page) => {
        setUsers(page.items);
        setTotalUsers(page.totalElements);
        setLoadError(null);
      })
      .catch((err) => {
        if (err instanceof ApiError && err.status === 403) {
          setLoadError({ kind: "forbidden", message: "Tu sesión no tiene permiso para administrar usuarios." });
        } else {
          setLoadError({
            kind: "error",
            message:
              err instanceof ApiError
                ? `El servidor respondió con un error (${err.status}). Puede ser algo temporal; intenta de nuevo.`
                : "No se pudo conectar con el servidor. Revisa tu conexión e intenta de nuevo.",
          });
        }
      })
      .finally(() => setLoading(false));
  }, []);

  const reloadUsers = () => {
    setLoading(true);
    loadUsers();
  };

  useEffect(() => {
    loadUsers();
    listRoles()
      .then(setRoles)
      .catch(() => undefined);
  }, [loadUsers]);

  // Al abrir "Crear usuario" (desde el botón de la página) el formulario empieza limpio.
  const [prevCreateOpen, setPrevCreateOpen] = useState(createOpen);
  if (createOpen !== prevCreateOpen) {
    setPrevCreateOpen(createOpen);
    if (createOpen) {
      setName("");
      setEmail("");
      setRoleCode("ADVISOR");
      setFormError(null);
    }
  }

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (users ?? []).filter(
      (u) =>
        (statusFilter ? u.status === statusFilter : u.status !== "OFFBOARDED") &&
        (!roleFilter || u.roleCode === roleFilter) &&
        (!q || u.name.toLowerCase().includes(q) || u.email.toLowerCase().includes(q)),
    );
  }, [users, query, roleFilter, statusFilter]);

  const allLoaded = users !== null && users.length >= totalUsers;
  const activeCount = users?.filter((u) => u.status === "ACTIVE").length ?? 0;
  const pendingCount = users?.filter((u) => u.status === "PENDING").length ?? 0;

  const handleCreate = async () => {
    if (!name.trim() || !email.trim()) {
      setFormError("Completa el nombre y el correo.");
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      const created = await createUser(name.trim(), email.trim(), roleCode);
      onCreateOpenChange(false);
      setShownLink({ user: created.user, link: created.link });
      loadUsers();
    } catch (err) {
      setFormError(err instanceof ApiError ? `No se pudo crear el usuario: ${err.message}` : "No se pudo conectar con el servidor. Lo capturado sigue aquí.");
    } finally {
      setSaving(false);
    }
  };

  const openEdit = (user: UserResponse) => {
    setEditUser(user);
    setName(user.name);
    setEmail(user.email);
    setRoleCode(user.roleCode);
    setFormError(null);
  };

  const handleEditSave = async () => {
    if (!editUser) return;
    if (!name.trim() || !email.trim()) {
      setFormError("El nombre y el correo son obligatorios.");
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      await updateUser(editUser.id, name.trim(), email.trim(), roleCode);
      setEditUser(null);
      showToast(roleCode !== editUser.roleCode ? "Usuario actualizado. El nuevo rol aplica de inmediato." : "Usuario actualizado.");
      loadUsers();
    } catch (err) {
      setFormError(err instanceof ApiError ? `No se pudo actualizar: ${err.message}` : "No se pudo conectar con el servidor. Lo capturado sigue aquí.");
    } finally {
      setSaving(false);
    }
  };

  /** Ejecuta una acción sobre una cuenta con su estado de "guardando" y el aviso correspondiente. */
  const act = async (user: UserResponse, action: () => Promise<unknown>, success: string) => {
    if (busyUserId) return;
    setBusyUserId(user.id);
    try {
      await action();
      showToast(success);
      loadUsers();
    } catch (err) {
      showToast(errorText(err));
    } finally {
      setBusyUserId(null);
    }
  };

  const roleOptions = roles.map((r) => ({ label: r.name, value: r.code }));

  if (loadError && users === null) {
    return loadError.kind === "forbidden" ? (
      <StateMessage kind="restricted" title="Acceso restringido" description={loadError.message} />
    ) : (
      <StateMessage
        kind="error"
        title="No se pudo cargar la lista de usuarios"
        description={loadError.message}
        action={
          <Button variant="secondary" onClick={reloadUsers} disabled={loading}>
            <RefreshCw className={cn("h-4 w-4", loading && "animate-spin")} aria-hidden /> Reintentar
          </Button>
        }
      />
    );
  }

  if (users === null) {
    return <StateMessage kind="loading" title="Cargando usuarios…" />;
  }

  const actionsFor = (user: UserResponse) => (
    <UserActions
      user={user}
      isMe={user.id === me?.id}
      busy={busyUserId === user.id}
      onEdit={openEdit}
      onLink={(u) => setPending({ kind: "link", user: u })}
      onUnlock={(u) => act(u, () => unlockUser(u.id), `La cuenta de ${u.name} quedó desbloqueada.`)}
      onActivate={(u) => act(u, () => activateUser(u.id), `${u.name} quedó reactivado.`)}
      onDeactivate={(u) => setPending({ kind: "deactivate", user: u })}
      onOffboard={(u) => setPending({ kind: "offboard", user: u })}
    />
  );

  return (
    <div className="space-y-5">
      <section aria-label="Resumen" className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <SummaryCard icon={<Users className="h-5 w-5" aria-hidden />} tone="bg-gold/20 text-dark-gold" value={totalUsers} label={totalUsers === 1 ? "cuenta" : "cuentas"} />
        <SummaryCard
          icon={<CircleCheck className="h-5 w-5" aria-hidden />}
          tone="bg-success-bg text-success-text"
          value={allLoaded ? activeCount : null}
          label={allLoaded ? (activeCount === 1 ? "activa" : "activas") : "activas (solo las primeras 100 cargadas)"}
        />
        <SummaryCard
          icon={<Clock className="h-5 w-5" aria-hidden />}
          tone="bg-warning-bg text-warning-text"
          value={allLoaded ? pendingCount : null}
          label="pendientes de definir contraseña"
        />
      </section>

      <div className="overflow-hidden rounded-xl border border-border bg-card shadow-[0_1px_2px_rgba(36,39,35,0.04)]">
        <div className="flex flex-col gap-3 border-b border-border p-4 lg:flex-row lg:items-center">
          <SearchInput value={query} onChange={setQuery} label="Buscar usuarios por nombre o correo" placeholder="Buscar por nombre o correo" className="lg:max-w-md lg:flex-1" />
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:ml-auto">
            <Select
              aria-label="Filtrar por estado"
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              options={statusFilters}
              containerClassName="lg:w-56"
              className="h-11"
            />
            <Select
              aria-label="Filtrar por rol"
              value={roleFilter}
              onChange={(e) => setRoleFilter(e.target.value)}
              options={[{ label: "Todos los roles", value: "" }, ...roleOptions]}
              containerClassName="lg:w-56"
              className="h-11"
            />
          </div>
        </div>

        {loadError ? (
          <p role="alert" className="flex flex-wrap items-center gap-3 border-b border-border bg-danger-bg px-4 py-3 text-sm text-danger-text">
            {loadError.message}
            <Button size="sm" variant="secondary" onClick={reloadUsers}>
              Reintentar
            </Button>
          </p>
        ) : null}

        {filtered.length === 0 ? (
          <StateMessage
            kind={users.length === 0 ? "empty" : "no-results"}
            title={users.length === 0 ? "Aún no hay usuarios" : "Sin resultados"}
            description={users.length === 0 ? "Crea el primer usuario con el botón “Crear usuario”." : "Ninguna cuenta coincide con la búsqueda o los filtros."}
            className="rounded-none border-0"
            action={
              users.length > 0 ? (
                <Button
                  variant="secondary"
                  onClick={() => {
                    setQuery("");
                    setRoleFilter("");
                    setStatusFilter("");
                  }}
                >
                  Quitar filtros
                </Button>
              ) : null
            }
          />
        ) : (
          <>
            {/* Escritorio: tabla. */}
            <table className="hidden w-full text-left text-sm lg:table">
              <thead className="bg-app-bg text-xs uppercase tracking-wide text-muted">
                <tr>
                  <th scope="col" className="px-5 py-3 font-medium">Usuario</th>
                  <th scope="col" className="px-5 py-3 font-medium">Rol</th>
                  <th scope="col" className="px-5 py-3 font-medium">Estado</th>
                  <th scope="col" className="px-5 py-3 font-medium">Última actividad</th>
                  <th scope="col" className="px-5 py-3 text-right font-medium">Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {filtered.map((user) => (
                  <tr key={user.id} className="align-top transition-colors duration-150 hover:bg-app-bg/60">
                    <td className="px-5 py-3.5">
                      <UserIdentity user={user} isMe={user.id === me?.id} />
                    </td>
                    <td className="px-5 py-3.5 text-obsessed">{user.roleName}</td>
                    <td className="px-5 py-3.5">
                      <StatusBadges user={user} />
                    </td>
                    <td className="px-5 py-3.5 text-muted">{lastActivityLabel(user.lastActivityAt)}</td>
                    <td className="px-5 py-3.5">{actionsFor(user)}</td>
                  </tr>
                ))}
              </tbody>
            </table>

            {/* Móvil y tablet: tarjetas. */}
            <ul className="divide-y divide-border lg:hidden">
              {filtered.map((user) => (
                <li key={user.id} className="space-y-3 p-4">
                  <div className="flex items-start justify-between gap-3">
                    <UserIdentity user={user} isMe={user.id === me?.id} />
                    <StatusBadges user={user} />
                  </div>
                  <dl className="grid grid-cols-2 gap-2 text-sm">
                    <div>
                      <dt className="text-xs text-muted">Rol</dt>
                      <dd className="text-obsessed">{user.roleName}</dd>
                    </div>
                    <div>
                      <dt className="text-xs text-muted">Última actividad</dt>
                      <dd className="text-obsessed">{lastActivityLabel(user.lastActivityAt)}</dd>
                    </div>
                  </dl>
                  {actionsFor(user)}
                </li>
              ))}
            </ul>
          </>
        )}
      </div>

      <div className="flex flex-col gap-4 rounded-xl border border-border bg-card p-4 shadow-[0_1px_2px_rgba(36,39,35,0.04)] sm:flex-row sm:items-center sm:p-5">
        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-gold/20 text-dark-gold">
          <ShieldCheck className="h-5 w-5" aria-hidden />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-sm font-semibold text-obsessed">Accesos por rol</p>
          <p className="text-sm text-muted">Cada usuario tiene los permisos de su rol. Consulta qué puede hacer cada uno y quién administra las cuentas.</p>
        </div>
        <Button variant="secondary" onClick={onShowRoles} className="self-start sm:self-auto">
          Ver roles y permisos <ArrowRight className="h-4 w-4" aria-hidden />
        </Button>
      </div>

      <Modal open={createOpen} onClose={() => onCreateOpenChange(false)} title="Crear usuario">
        <form
          className="flex flex-col gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            void handleCreate();
          }}
        >
          <Input label="Nombre completo" value={name} onChange={(e) => setName(e.target.value)} required autoComplete="off" />
          <Input label="Correo" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="off" hint="Con este correo entrará al sistema." />
          <Select label="Rol" value={roleCode} onChange={(e) => setRoleCode(e.target.value as BackendRoleCode)} options={roleOptions} />
          <p className="rounded-lg bg-info-bg px-3 py-2 text-sm text-info-text">
            No necesitas inventarle una contraseña: al crear la cuenta obtendrás un enlace para enviárselo y que la persona defina la suya.
          </p>
          {formError ? (
            <p role="alert" className="rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger-text">
              {formError}
            </p>
          ) : null}
          <div className="mt-2 flex justify-end gap-3">
            <Button type="button" variant="secondary" onClick={() => onCreateOpenChange(false)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? "Creando…" : "Crear y obtener enlace"}
            </Button>
          </div>
        </form>
      </Modal>

      <Modal open={Boolean(editUser)} onClose={() => setEditUser(null)} title="Editar usuario">
        <form
          className="flex flex-col gap-4"
          onSubmit={(e) => {
            e.preventDefault();
            void handleEditSave();
          }}
        >
          <Input label="Nombre completo" value={name} onChange={(e) => setName(e.target.value)} required />
          <Input label="Correo" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          <Select
            label="Rol"
            value={roleCode}
            onChange={(e) => setRoleCode(e.target.value as BackendRoleCode)}
            options={roleOptions}
            disabled={editUser?.id === me?.id}
          />
          {editUser?.id === me?.id ? <p className="text-xs text-muted">No puedes cambiar tu propio rol; pídeselo a otro administrador.</p> : null}
          {formError ? (
            <p role="alert" className="rounded-lg bg-danger-bg px-3 py-2 text-sm text-danger-text">
              {formError}
            </p>
          ) : null}
          <div className="mt-2 flex justify-end gap-3">
            <Button type="button" variant="secondary" onClick={() => setEditUser(null)}>
              Cancelar
            </Button>
            <Button type="submit" disabled={saving}>
              {saving ? "Guardando…" : "Guardar"}
            </Button>
          </div>
        </form>
      </Modal>

      {pending?.kind === "link" ? (
        <ConfirmModal
          title={pending.user.status === "PENDING" ? "Nuevo enlace de alta" : "Restablecer contraseña"}
          confirmLabel="Generar enlace"
          onCancel={() => setPending(null)}
          onConfirm={async () => {
            const user = pending.user;
            try {
              const link = await issuePasswordLink(user.id);
              setPending(null);
              setShownLink({ user, link });
              loadUsers();
            } catch (err) {
              showToast(errorText(err));
            }
          }}
        >
          <p className="text-sm text-obsessed">
            Se generará un enlace de un solo uso para que <strong>{pending.user.name}</strong> defina una contraseña nueva. Tú no verás ni escribirás su contraseña.
          </p>
          <ul className="mt-3 list-disc space-y-1 pl-5 text-sm text-muted">
            <li>Cualquier enlace anterior deja de funcionar.</li>
            {pending.user.status === "PENDING" ? null : <li>Su contraseña actual sigue valiendo hasta que use el enlace.</li>}
            <li>Al usarlo se cierran todas sus sesiones abiertas.</li>
          </ul>
        </ConfirmModal>
      ) : null}

      {pending?.kind === "deactivate" ? (
        <WorkloadConfirm
          user={pending.user}
          title="Desactivar cuenta"
          confirmLabel="Desactivar"
          intro={
            <>
              <strong>{pending.user.name}</strong> dejará de poder entrar de inmediato y se cerrarán sus sesiones abiertas. Puedes reactivarla después; su historial se
              conserva.
            </>
          }
          blockOnWorkload={false}
          onCancel={() => setPending(null)}
          onConfirm={async () => {
            const user = pending.user;
            setPending(null);
            await act(user, () => deactivateUser(user.id), `${user.name} quedó desactivado; ya no puede entrar.`);
          }}
        />
      ) : null}

      {pending?.kind === "offboard" ? (
        <WorkloadConfirm
          user={pending.user}
          title="Dar de baja"
          confirmLabel="Dar de baja definitivamente"
          intro={
            <>
              La baja es <strong>definitiva</strong>: <strong>{pending.user.name}</strong> ya no podrá entrar ni reactivarse. No se borra nada: su nombre se conserva en el
              historial y en los expedientes.
            </>
          }
          blockOnWorkload
          askReason
          onCancel={() => setPending(null)}
          onConfirm={async (reason) => {
            const user = pending.user;
            setPending(null);
            await act(user, () => offboardUser(user.id, reason), `${user.name} quedó dado de baja.`);
          }}
        />
      ) : null}

      {shownLink ? <SetupLinkModal user={shownLink.user} link={shownLink.link} onClose={() => setShownLink(null)} /> : null}
    </div>
  );
}

function SummaryCard({ icon, tone, value, label }: { icon: ReactNode; tone: string; value: number | null; label: string }) {
  return (
    <div className="flex items-center gap-4 rounded-xl border border-border bg-card p-4 shadow-[0_1px_2px_rgba(36,39,35,0.04)] sm:p-5">
      <span className={cn("flex h-11 w-11 shrink-0 items-center justify-center rounded-full", tone)}>{icon}</span>
      <div className="min-w-0">
        <p className="text-2xl font-semibold tabular-nums leading-none text-obsessed">{value ?? "—"}</p>
        <p className="mt-1 text-sm text-muted">{label}</p>
      </div>
    </div>
  );
}

function UserIdentity({ user, isMe }: { user: UserResponse; isMe: boolean }) {
  return (
    <div className="flex min-w-0 items-center gap-3">
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gold/25 text-xs font-semibold text-dark-gold" aria-hidden>
        {initials(user.name)}
      </span>
      <div className="min-w-0">
        <p className="break-words font-medium text-obsessed">
          {user.name}
          {isMe ? <span className="ml-1.5 text-xs font-normal text-muted">(tú)</span> : null}
        </p>
        <p className="break-all text-xs text-muted">{user.email}</p>
      </div>
    </div>
  );
}

const statusBadge: Record<BackendUserStatus, { tone: "success" | "warning" | "neutral" | "danger"; label: string; icon: typeof CircleCheck }> = {
  ACTIVE: { tone: "success", label: "Activa", icon: CircleCheck },
  PENDING: { tone: "warning", label: "Pendiente de contraseña", icon: Clock },
  INACTIVE: { tone: "neutral", label: "Desactivada", icon: CircleSlash },
  OFFBOARDED: { tone: "danger", label: "Dada de baja", icon: UserX },
};

function StatusBadges({ user }: { user: UserResponse }) {
  const status = statusBadge[user.status];
  const Icon = status.icon;
  return (
    <div className="flex flex-col items-end gap-1 lg:items-start">
      <Badge tone={status.tone}>
        <Icon aria-hidden /> {status.label}
      </Badge>
      {user.locked ? (
        <Badge tone="danger">
          <Lock aria-hidden /> Bloqueada{user.lockedUntil ? ` hasta ${timeFormat.format(new Date(user.lockedUntil))}` : ""}
        </Badge>
      ) : null}
      {user.status === "OFFBOARDED" && user.offboardedAt ? <span className="text-xs text-muted">desde {dateFormat.format(new Date(user.offboardedAt))}</span> : null}
    </div>
  );
}

function UserActions({
  user,
  isMe,
  busy,
  onEdit,
  onLink,
  onUnlock,
  onActivate,
  onDeactivate,
  onOffboard,
}: {
  user: UserResponse;
  isMe: boolean;
  busy: boolean;
  onEdit: (u: UserResponse) => void;
  onLink: (u: UserResponse) => void;
  onUnlock: (u: UserResponse) => void;
  onActivate: (u: UserResponse) => void;
  onDeactivate: (u: UserResponse) => void;
  onOffboard: (u: UserResponse) => void;
}) {
  if (user.status === "OFFBOARDED") {
    return <p className="text-xs text-muted lg:text-right">Sin acciones: la baja es definitiva.</p>;
  }
  return (
    <div className="flex flex-wrap gap-2 lg:justify-end">
      <Button variant="ghost" size="sm" onClick={() => onEdit(user)} aria-label={`Editar a ${user.name}`} disabled={busy}>
        <Pencil className="h-3.5 w-3.5" aria-hidden /> Editar
      </Button>
      {user.status !== "INACTIVE" && !isMe ? (
        <Button variant="ghost" size="sm" onClick={() => onLink(user)} disabled={busy} aria-label={`Enlace de contraseña para ${user.name}`}>
          <KeyRound className="h-3.5 w-3.5" aria-hidden /> {user.status === "PENDING" ? "Nuevo enlace de alta" : "Restablecer contraseña"}
        </Button>
      ) : null}
      {user.locked ? (
        <Button variant="secondary" size="sm" onClick={() => onUnlock(user)} disabled={busy}>
          <Lock className="h-3.5 w-3.5" aria-hidden /> Desbloquear
        </Button>
      ) : null}
      {isMe ? null : user.status === "INACTIVE" ? (
        <Button variant="secondary" size="sm" onClick={() => onActivate(user)} disabled={busy} aria-label={`Reactivar a ${user.name}`}>
          {busy ? "Guardando…" : "Reactivar"}
        </Button>
      ) : (
        <Button variant="danger" size="sm" onClick={() => onDeactivate(user)} disabled={busy} aria-label={`Desactivar a ${user.name}`}>
          {busy ? "Guardando…" : "Desactivar"}
        </Button>
      )}
      {isMe ? null : (
        <Button variant="ghost" size="sm" onClick={() => onOffboard(user)} disabled={busy} aria-label={`Dar de baja a ${user.name}`} className="text-danger-text">
          <UserMinus className="h-3.5 w-3.5" aria-hidden /> Dar de baja
        </Button>
      )}
    </div>
  );
}

function ConfirmModal({
  title,
  confirmLabel,
  danger = false,
  disabled = false,
  children,
  onCancel,
  onConfirm,
}: {
  title: string;
  confirmLabel: string;
  danger?: boolean;
  disabled?: boolean;
  children: ReactNode;
  onCancel: () => void;
  onConfirm: () => Promise<void>;
}) {
  const [busy, setBusy] = useState(false);
  return (
    <Modal
      open
      onClose={onCancel}
      title={title}
      footer={
        <>
          <Button variant="ghost" onClick={onCancel}>
            Cancelar
          </Button>
          <Button
            variant={danger ? "danger" : "primary"}
            disabled={busy || disabled}
            onClick={async () => {
              setBusy(true);
              try {
                await onConfirm();
              } finally {
                setBusy(false);
              }
            }}
          >
            {busy ? "Procesando…" : confirmLabel}
          </Button>
        </>
      }
    >
      {children}
    </Modal>
  );
}

/**
 * Antes de desactivar o dar de baja se muestra lo que la persona tiene a su cargo. Para la baja es
 * obligatorio reasignarlo primero (el servidor también lo exige).
 */
function WorkloadConfirm({
  user,
  title,
  confirmLabel,
  intro,
  blockOnWorkload,
  askReason = false,
  onCancel,
  onConfirm,
}: {
  user: UserResponse;
  title: string;
  confirmLabel: string;
  intro: ReactNode;
  blockOnWorkload: boolean;
  askReason?: boolean;
  onCancel: () => void;
  onConfirm: (reason: string) => Promise<void>;
}) {
  const [workload, setWorkload] = useState<WorkloadItem[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [reason, setReason] = useState("");

  useEffect(() => {
    let alive = true;
    getUserWorkload(user.id)
      .then((w) => alive && setWorkload(w))
      .catch(() => alive && setFailed(true));
    return () => {
      alive = false;
    };
  }, [user.id]);

  const hasWork = (workload?.length ?? 0) > 0;
  const blocked = blockOnWorkload && hasWork;

  return (
    <ConfirmModal
      title={title}
      confirmLabel={confirmLabel}
      danger
      disabled={(workload === null && !failed) || blocked || (askReason && reason.trim().length < 5)}
      onCancel={onCancel}
      onConfirm={() => onConfirm(reason.trim())}
    >
      <p className="text-sm text-obsessed">{intro}</p>
      {workload === null && !failed ? <p className="mt-3 text-sm text-muted">Revisando lo que tiene a su cargo…</p> : null}
      {hasWork ? (
        <div className={cn("mt-3 rounded-lg px-3 py-2 text-sm", blocked ? "bg-danger-bg text-danger-text" : "bg-warning-bg text-warning-text")}>
          <p className="font-medium">Tiene a su cargo:</p>
          <ul className="mt-1 list-disc pl-5">
            {workload!.map((w) => (
              <li key={w.label}>
                {w.count} {w.label.toLowerCase()}
              </li>
            ))}
          </ul>
          <p className="mt-1">
            {blocked
              ? "Reasígnalos a otra persona (desde cada expediente o lead) antes de darle de baja."
              : "Seguirán asignados a esta persona; considera reasignarlos."}
          </p>
        </div>
      ) : null}
      {askReason ? (
        <>
          <label htmlFor="offboard-reason" className="mb-1 mt-4 block text-sm font-medium text-obsessed">
            Motivo de la baja
          </label>
          <textarea
            id="offboard-reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            rows={2}
            maxLength={500}
            placeholder="Ej. Dejó de colaborar con la oficina."
            className="w-full rounded-xl border border-border bg-card px-3.5 py-2.5 text-sm outline-none focus:border-gold"
          />
          <p className="mt-1 text-xs text-muted">Queda en la bitácora con tu nombre, la fecha y la hora.</p>
        </>
      ) : null}
    </ConfirmModal>
  );
}

/** El enlace solo se muestra una vez: el administrador lo copia o lo manda por WhatsApp. */
function SetupLinkModal({ user, link, onClose }: { user: Pick<UserResponse, "name" | "email">; link: SetupLinkResponse; onClose: () => void }) {
  const { showToast } = useToast();
  const invite = link.purpose === "INVITE";
  const expires = dateTimeFormat.format(new Date(link.expiresAt));
  const message = invite
    ? `Hola ${user.name.split(" ")[0]}, ya tienes cuenta en el sistema de CENTURY 21 Genera. Entra a este enlace para crear tu contraseña (vence el ${expires}): ${link.url}`
    : `Hola ${user.name.split(" ")[0]}, este es tu enlace para definir una contraseña nueva en el sistema de CENTURY 21 Genera (vence el ${expires} y solo funciona una vez): ${link.url}`;

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(link.url);
      showToast("Enlace copiado.");
    } catch {
      showToast("No se pudo copiar; selecciónalo y cópialo a mano.");
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={invite ? "Cuenta creada" : "Enlace de restablecimiento"}
      footer={<Button onClick={onClose}>Listo</Button>}
    >
      <p className="text-sm text-obsessed">
        Envía este enlace a <strong>{user.name}</strong> ({user.email}) para que {invite ? "cree" : "defina"} su contraseña. Vence el <strong>{expires}</strong> y solo
        funciona una vez.
      </p>
      <div className="mt-4 flex flex-col gap-2 sm:flex-row">
        <input
          readOnly
          value={link.url}
          onFocus={(e) => e.target.select()}
          aria-label="Enlace de un solo uso"
          className="min-w-0 flex-1 rounded-xl border border-border bg-app-bg px-3.5 py-2.5 text-sm text-obsessed"
        />
        <Button variant="secondary" onClick={copy}>
          <Copy className="h-4 w-4" aria-hidden /> Copiar
        </Button>
      </div>
      <a
        href={`https://wa.me/?text=${encodeURIComponent(message)}`}
        target="_blank"
        rel="noopener noreferrer"
        className="mt-3 inline-flex min-h-10 items-center gap-2 rounded-xl border border-border px-3.5 py-2 text-sm font-medium text-obsessed hover:bg-app-bg focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-gold/30"
      >
        <MessageCircle className="h-4 w-4" aria-hidden /> Enviar por WhatsApp
      </a>
      <p className="mt-4 rounded-lg bg-warning-bg px-3 py-2 text-sm text-warning-text">
        Por seguridad, este enlace no se vuelve a mostrar. Si se pierde o vence, genera uno nuevo con “{invite ? "Nuevo enlace de alta" : "Restablecer contraseña"}”.
      </p>
    </Modal>
  );
}
