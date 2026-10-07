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
import { activateUser, createUser, deactivateUser, listUsers, updateUser } from "@/lib/api/users";
import type { BackendRoleCode, RoleResponse, UserResponse } from "@/lib/api/types";
import { cn, initials } from "@/lib/utils";
import { ArrowRight, CircleCheck, CircleSlash, Pencil, RefreshCw, ShieldCheck, Users } from "lucide-react";
import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";

// La API devuelve como máximo 100 por página; el personal interno de una oficina cabe de sobra.
const PAGE_SIZE = 100;

type LoadError = { kind: "forbidden" | "error"; message: string };

const timeFormat = new Intl.DateTimeFormat("es-MX", { hour: "2-digit", minute: "2-digit" });
const dateFormat = new Intl.DateTimeFormat("es-MX", { day: "numeric", month: "short", year: "numeric" });

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

interface UsersTableProps {
  createOpen: boolean;
  onCreateOpenChange: (open: boolean) => void;
  onShowRoles: () => void;
}

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
  const [editUser, setEditUser] = useState<UserResponse | null>(null);
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [roleCode, setRoleCode] = useState<BackendRoleCode>("ADVISOR");
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [busyUserId, setBusyUserId] = useState<string | null>(null);

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
      setPassword("");
      setRoleCode("ADVISOR");
      setFormError(null);
    }
  }

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (users ?? []).filter(
      (u) => (!roleFilter || u.roleCode === roleFilter) && (!q || u.name.toLowerCase().includes(q) || u.email.toLowerCase().includes(q)),
    );
  }, [users, query, roleFilter]);

  const allLoaded = users !== null && users.length >= totalUsers;
  const activeCount = users?.filter((u) => u.status === "ACTIVE").length ?? 0;

  const handleCreate = async () => {
    if (!name.trim() || !email.trim() || password.length < 8) {
      setFormError("Completa nombre, correo y una contraseña temporal de al menos 8 caracteres.");
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      await createUser(name.trim(), email.trim(), password, roleCode);
      onCreateOpenChange(false);
      showToast("Usuario creado.");
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
      showToast("Usuario actualizado.");
      loadUsers();
    } catch (err) {
      setFormError(err instanceof ApiError ? `No se pudo actualizar: ${err.message}` : "No se pudo conectar con el servidor. Lo capturado sigue aquí.");
    } finally {
      setSaving(false);
    }
  };

  const toggleStatus = async (user: UserResponse) => {
    if (busyUserId) return;
    setBusyUserId(user.id);
    try {
      if (user.status === "ACTIVE") {
        await deactivateUser(user.id);
        showToast(`${user.name} quedó desactivado.`);
      } else {
        await activateUser(user.id);
        showToast(`${user.name} quedó activado.`);
      }
      loadUsers();
    } catch (err) {
      showToast(err instanceof ApiError ? `No se pudo actualizar: ${err.message}` : "No se pudo conectar con el servidor.");
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

  return (
    <div className="space-y-5">
      <section aria-label="Resumen" className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <SummaryCard icon={<Users className="h-5 w-5" aria-hidden />} tone="bg-gold/20 text-dark-gold" value={totalUsers} label={totalUsers === 1 ? "usuario" : "usuarios"} />
        <SummaryCard
          icon={<CircleCheck className="h-5 w-5" aria-hidden />}
          tone="bg-success-bg text-success-text"
          value={allLoaded ? activeCount : null}
          label={allLoaded ? (activeCount === 1 ? "activo" : "activos") : "activos (solo los primeros 100 cargados)"}
        />
      </section>

      <div className="overflow-hidden rounded-xl border border-border bg-card shadow-[0_1px_2px_rgba(36,39,35,0.04)]">
        <div className="flex flex-col gap-3 border-b border-border p-4 sm:flex-row sm:items-center">
          <SearchInput value={query} onChange={setQuery} label="Buscar usuarios por nombre o correo" placeholder="Buscar por nombre o correo" className="sm:max-w-md sm:flex-1" />
          <Select
            aria-label="Filtrar por rol"
            value={roleFilter}
            onChange={(e) => setRoleFilter(e.target.value)}
            options={[{ label: "Todos los roles", value: "" }, ...roleOptions]}
            containerClassName="sm:ml-auto sm:w-56"
            className="h-11"
          />
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
            description={users.length === 0 ? "Crea el primer usuario con el botón “Crear usuario”." : "Ningún usuario coincide con la búsqueda o el rol seleccionado."}
            className="rounded-none border-0"
            action={
              users.length > 0 ? (
                <Button
                  variant="secondary"
                  onClick={() => {
                    setQuery("");
                    setRoleFilter("");
                  }}
                >
                  Quitar filtros
                </Button>
              ) : null
            }
          />
        ) : (
          <>
            {/* Escritorio y tablet: tabla. */}
            <table className="hidden w-full text-left text-sm md:table">
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
                  <tr key={user.id} className="transition-colors duration-150 hover:bg-app-bg/60">
                    <td className="px-5 py-3.5">
                      <UserIdentity user={user} isMe={user.id === me?.id} />
                    </td>
                    <td className="px-5 py-3.5 text-obsessed">{user.roleName}</td>
                    <td className="px-5 py-3.5">
                      <StatusBadge user={user} />
                    </td>
                    <td className="px-5 py-3.5 text-muted">{lastActivityLabel(user.lastActivityAt)}</td>
                    <td className="px-5 py-3.5">
                      <UserActions user={user} busy={busyUserId === user.id} onEdit={openEdit} onToggle={toggleStatus} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {/* Móvil: tarjetas. */}
            <ul className="divide-y divide-border md:hidden">
              {filtered.map((user) => (
                <li key={user.id} className="space-y-3 p-4">
                  <div className="flex items-start justify-between gap-3">
                    <UserIdentity user={user} isMe={user.id === me?.id} />
                    <StatusBadge user={user} />
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
                  <UserActions user={user} busy={busyUserId === user.id} onEdit={openEdit} onToggle={toggleStatus} />
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
          <p className="text-sm text-muted">Cada usuario tiene los permisos de su rol. Consulta qué puede hacer cada uno.</p>
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
          <Input label="Correo" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="off" />
          <Input
            label="Contraseña temporal"
            type="password"
            hint="Mínimo 8 caracteres. Compártela con la persona por un medio seguro."
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={8}
            autoComplete="new-password"
          />
          <Select label="Rol" value={roleCode} onChange={(e) => setRoleCode(e.target.value as BackendRoleCode)} options={roleOptions} />
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
              {saving ? "Creando…" : "Crear"}
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
          <Select label="Rol" value={roleCode} onChange={(e) => setRoleCode(e.target.value as BackendRoleCode)} options={roleOptions} />
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

function StatusBadge({ user }: { user: UserResponse }) {
  return user.status === "ACTIVE" ? (
    <Badge tone="success">
      <CircleCheck aria-hidden /> Activo
    </Badge>
  ) : (
    <Badge tone="neutral">
      <CircleSlash aria-hidden /> Inactivo
    </Badge>
  );
}

function UserActions({
  user,
  busy,
  onEdit,
  onToggle,
}: {
  user: UserResponse;
  busy: boolean;
  onEdit: (u: UserResponse) => void;
  onToggle: (u: UserResponse) => void;
}) {
  return (
    <div className="flex flex-wrap gap-2 md:justify-end">
      <Button variant="ghost" size="sm" onClick={() => onEdit(user)} aria-label={`Editar a ${user.name}`}>
        <Pencil className="h-3.5 w-3.5" aria-hidden /> Editar
      </Button>
      <Button
        variant={user.status === "ACTIVE" ? "danger" : "secondary"}
        size="sm"
        onClick={() => onToggle(user)}
        disabled={busy}
        aria-label={`${user.status === "ACTIVE" ? "Desactivar" : "Activar"} a ${user.name}`}
      >
        {busy ? "Guardando…" : user.status === "ACTIVE" ? "Desactivar" : "Activar"}
      </Button>
    </div>
  );
}
